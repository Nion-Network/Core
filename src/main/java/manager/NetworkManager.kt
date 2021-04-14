package manager

import communication.UDPServer
import data.*
import data.EndPoint.*
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import logging.Logger
import utils.Crypto
import utils.Utils
import utils.asMessage
import java.lang.Integer.max
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:58
 * using IntelliJ IDEA
 */
class NetworkManager(configurationPath: String, private val listeningPort: Int) {

    var isInNetwork = false
    val knownNodes = mutableMapOf<String, Node>()
    val isFull: Boolean get() = knownNodes.size >= configuration.maxNodes

    val configuration: Configuration = Utils.gson.fromJson<Configuration>(Utils.readFile(configurationPath), Configuration::class.java)
    val isTrustedNode: Boolean get() = configuration.let { InetAddress.getLocalHost().hostAddress == it.trustedNodeIP && it.trustedNodePort == listeningPort }
    val crypto = Crypto(".")
    val vdf = VDFManager()
    val dht = DHTManager(this)
    val docker = DockerManager(crypto, configuration)
    val dashboard = DashboardManager(configuration)
    val informationManager = InformationManager(this)

    private val networkHistory = ConcurrentHashMap<String, Long>()
    private val messageQueue = LinkedBlockingQueue<QueuedMessage<*>>()
    private val startingInclusionSet = if (isTrustedNode) mutableMapOf(crypto.publicKey to true) else mutableMapOf()

    val currentState = State(0, 0, 0, configuration.initialDifficulty, startingInclusionSet)

    private val chainManager = ChainManager(this)
    private val committeeManager = CommitteeManager(this)
    val validatorManager = chainManager.validatorManager

    private val udpServer = UDPServer(configuration, dashboard, networkHistory, listeningPort)
    private val httpServer = Javalin.create { it.showJavalinBanner = false }.start(listeningPort + 1)

    private val myIP: String = InetAddress.getLocalHost().hostAddress

    val ourNode = Node(crypto.publicKey, myIP, listeningPort)

    init {
        Logger.toggleLogging(configuration.loggingEnabled || isTrustedNode)
        httpServer.before {
            if (it.ip().startsWith("127")) return@before
            val hex = it.header("hex") ?: ""
            if (networkHistory.containsKey(hex)) throw ForbiddenResponse("NO MEANS NO")
            else networkHistory[hex] = System.currentTimeMillis()
        }
        httpServer.exception(Exception::class.java) { exception, _ -> exception.printStackTrace() }
    }

    fun start() {
        Logger.debug("My IP is $myIP")

        udpServer.startListening { endPoint, data ->
            when (endPoint) {
                Query -> data executeImmediately dht::onQuery
                Found -> data executeImmediately dht::onFound
                OnJoin -> data executeImmediately dht::onJoin
                Join -> data executeImmediately dht::joinRequest

                Vote -> data executeImmediately chainManager::voteReceived
                Include -> data executeImmediately validatorManager::inclusionRequest
                SyncRequest -> data executeImmediately chainManager::syncRequestReceived
                OnVoteRequest -> data executeImmediately committeeManager::voteRequest
                NodeStatistics -> data executeImmediately informationManager::dockerStatisticsReceived
                RepresentativeStatistics -> data executeImmediately informationManager::representativeStatisticsReceived

                SyncReply -> data queueMessage chainManager::syncReplyReceived
                BlockReceived -> data queueMessage chainManager::blockReceived
                else -> Logger.error("Unexpected $endPoint in packet handler.")
            }
        }

        httpServer.apply {
            post("/dockerStats", docker::updateStats)
            get("/ping") { Logger.info("Pinged me!") }
            get("/run/image", docker::runImage)
            post("/run/migration/image", docker::runMigratedImage)
        }

        if (!isTrustedNode) joinTheNetwork()
        else Logger.debug("We're the trusted node!")

        Logger.debug("Listening on port: $listeningPort")
        startQueueThread()
        startHistoryCleanup()
    }

    private fun <T> encodeToPacketData(endPoint: EndPoint, message: Message<T>): ByteArray {
        val hex = message.hex.toByteArray()
        val hexLength = hex.size

        val messageBytes = message.asJson.toByteArray()
        val messageLength = messageBytes.size

        return ByteBuffer.allocate(9 + hexLength + messageLength).apply {
            putInt(hexLength)
            put(hex)
            put(endPoint.identification)
            putInt(messageLength)
            put(messageBytes)
        }.array()
    }

    /**
     * Sends the Join request to the trusted node and waits to be accepted into the network.
     *
     */
    private fun joinTheNetwork() {
        Logger.info("Sending join request to our trusted node...")
        val trustedAddress = InetSocketAddress(configuration.trustedNodeIP, configuration.trustedNodePort)
        val packetData = encodeToPacketData(Join, generateMessage(ourNode))
        val packet = DatagramPacket(packetData, packetData.size, trustedAddress)
        udpServer.send(packet)

        while (!isInNetwork) {
            Logger.debug("Waiting to be accepted into the network...")
            Thread.sleep(1000)
        }

        Logger.debug("We're in the network. Happy networking!")
    }

    /**
     * Runs the thread that is in charge of queue processing.
     *
     */
    private fun startQueueThread() = Thread {
        while (true) {
            try {
                messageQueue.take().execute.invoke()
            } catch (e: java.lang.Exception) {
                Logger.error("Exception caught!")
                e.printStackTrace()
                dashboard.reportException(e)
            }
        }
    }.start()

    /**
     * Runs the executor that will clean old messages every X minutes specified in configuration.
     *
     */
    private fun startHistoryCleanup() = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
        networkHistory.toList().forEach { (messageHex, timestamp) ->
            val difference = System.currentTimeMillis() - timestamp
            if (TimeUnit.MILLISECONDS.toMinutes(difference) >= configuration.historyMinuteClearance) networkHistory.remove(messageHex)
        }
        //dashboard.logQueue(networkHistory.size, DigestUtils.sha256Hex(crypto.publicKey))
    }, 0, configuration.historyCleaningFrequency.toLong(), TimeUnit.MINUTES)

    /**
     *  Set networking to respond using given lambda block to provided path on GET request.
     *
     * @param block Response lambda that will execute on GET request.
     */
    @Deprecated("HTTP is no longer being used. Replaced with UDP packets.")
    private infix fun String.get(block: Context.() -> Unit): Javalin = httpServer.get(this, block)

    /**
     *  Set networking to respond using given lambda block to provided path on POST request.
     *
     * @param block Response lambda that will execute on POST request.
     */
    @Deprecated("HTTP is no longer being used. Replaced with UDP packets.")
    private infix fun String.post(block: Context.() -> Unit): Javalin = httpServer.post(this, block)

    /**
     * Add the received Message with the body of type T to the message queue.
     *
     * @param T Message's body type.
     * @param block Lambda that is executed when message is taken out of queue.
     */
    private inline infix fun <reified T> ByteArray.queueMessage(noinline block: (Message<T>) -> Unit) {
        messageQueue.put(QueuedMessage(asMessage(), block))
    } /*= when (requestType) {
        GET -> path get { /* TODO */ }
        POST -> path post {
            val hex = header("hex")
            if (hex != null) messageQueue.put(QueuedMessage(hex, getMessage(), block))
            Logger.info("Putting [${path()}] [${messageQueue.size}]...")
            status(200)
        }
    }*/


    fun <T> sendPacket(node: Node?, endPoint: EndPoint, message: Message<T>) {
        if (node == null) return
        val data = encodeToPacketData(endPoint, message)
        val packet = DatagramPacket(data, data.size, InetSocketAddress(node.ip, node.port))
        udpServer.send(packet)
    }

    /**
     * Sends the message to a few randomly chosen nodes from the known nodes.
     *
     * @param T Message body type.
     * @param path Endpoint for the message to go to.
     * @param spread Amount of nodes to choose (if less are known, all are chosen)
     * @param message Message with body of type T.
     */
    fun <T> sendMessageToRandomNodes(endPoint: EndPoint, spread: Int, message: Message<T>) = pickRandomNodes(spread).forEach { sendPacket(it, endPoint, message) }

    /**
     * Broadcasts the specified message to known nodes in the network.
     *
     * @param T Message body type.
     * @param path Endpoint for the message to go to.
     * @param message Message with body of type T.
     * @param limited If true, broadcast spread will be limited to the amount specified in configuration,
     */
    fun <T> broadcast(endPoint: EndPoint, message: Message<T>, limited: Boolean = true) {
        val hexHash = message.hex
        // if (!networkHistory.contains(hexHash)) networkHistory[hexHash] = message.timeStamp
        val shuffledNodes = knownNodes.values.shuffled()
        val totalSize = shuffledNodes.size
        val amountToTake = if (limited) 3 + (configuration.broadcastSpreadPercentage * 100 / max(totalSize, 1)) else totalSize
        shuffledNodes.take(amountToTake).forEach { sendPacket(it, endPoint, message) }
    }

    /**
     * Create a generics message ready to be sent across the network.
     *
     * @param T Message body class type
     * @param data Body of type T to be serialized into JSON.
     * @return Message with the signed body type of T, current publicKey and the body itself.
     */
    fun <T> generateMessage(data: T): Message<T> = Message(crypto.publicKey, crypto.sign(Utils.gson.toJson(data)), data)

    /**
     * Picks the random [amount] nodes from the known nodes map.
     * If size of known nodes is less than the amount specified, all will be picked, but still in random order.
     *
     * @param amount How many nodes to take.
     * @return
     */
    private fun pickRandomNodes(amount: Int): List<Node> = knownNodes.values.shuffled().take(amount)

    fun clearMessageQueue() {
        messageQueue.clear()
    }

    private inline infix fun <reified T> ByteArray.executeImmediately(crossinline block: Message<T>.() -> Unit) {
        block.invoke(asMessage())
    }
}