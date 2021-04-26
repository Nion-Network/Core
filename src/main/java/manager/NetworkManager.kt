package manager

import chain.BlockProducer
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
import java.util.*
import java.util.concurrent.*

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:58
 * using IntelliJ IDEA
 */
class NetworkManager(configurationPath: String, private val listeningPort: Int) {

    var isInNetwork = false
    val knownNodes = ConcurrentHashMap<String, Node>()
    val isFull: Boolean get() = knownNodes.size >= configuration.maxNodes

    val configuration: Configuration = Utils.gson.fromJson<Configuration>(Utils.readFile(configurationPath), Configuration::class.java)
    val isTrustedNode: Boolean get() = configuration.let { InetAddress.getLocalHost().hostAddress == it.trustedNodeIP && it.trustedNodePort == listeningPort }

    val crypto = Crypto(".")
    val dht = DHTManager(this)
    val vdf = VDFManager()
    val docker = DockerManager(crypto, configuration)
    val dashboard = DashboardManager(configuration)

    val informationManager = InformationManager(this)
    private val networkHistory = ConcurrentHashMap<String, Long>()

    private val messageQueue = LinkedBlockingDeque<QueuedMessage<*>>()

    // val currentState = State(-1, -1, 0, configuration.initialDifficulty, startingInclusionSet)
    private val blockProducer = BlockProducer(crypto, configuration, isTrustedNode)
    private val chainManager = ChainManager(this, crypto, configuration, vdf, dht, docker, dashboard, informationManager, blockProducer)
    private val committeeManager = CommitteeManager(this, crypto, vdf, dashboard)

    private val udpServer = UDPServer(configuration, crypto, dashboard, knownNodes, networkHistory, listeningPort)
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
            Logger.trace("------------------------- Endpoint hit $endPoint! -------------------------")
            when (endPoint) {
                Query -> data queueMessage dht::onQuery
                Found -> data queueMessage dht::onFound
                OnJoin -> data queueMessage dht::onJoin
                Join -> data queueMessage dht::joinRequest

                Vote -> data queueMessage chainManager::voteReceived
                Include -> data queueMessage chainManager::inclusionRequest
                SyncRequest -> data queueMessage chainManager::syncRequestReceived
                OnVoteRequest -> data queueMessage committeeManager::voteRequest
                // NodeStatistics -> data queueMessage informationManager::dockerStatisticsReceived
                // RepresentativeStatistics -> data queueMessage informationManager::representativeStatisticsReceived

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

        startQueueThread()
        startHistoryCleanup()

        if (!isTrustedNode) joinTheNetwork()
        else Logger.debug("We're the trusted node!")

        Logger.debug("Listening on port: $listeningPort")
    }

    /**
     * Sends the Join request to the trusted node and waits to be accepted into the network.
     *
     */
    private fun joinTheNetwork() {
        Logger.info("Sending join request to our trusted node...")

        val joinRequestMessage = generateMessage(ourNode)
        val trustedNode = Node("", configuration.trustedNodeIP, configuration.trustedNodePort)
        sendMessage(trustedNode, Join, joinRequestMessage, false)

        Logger.debug("Waiting to be accepted into the network...")
        Thread.sleep(10000)
        if (!isInNetwork) joinTheNetwork()
        else {
            Logger.debug("We're in the network. Happy networking!")
            chainManager.requestInclusion(true)
        }
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
        messageQueue.put(QueuedMessage(asMessage(), block).apply {
            // Logger.info("Put to queue ${T::class.java.toGenericString()}. Current size: ${messageQueue.size + 1}")
        })
    }


    fun <T> sendMessage(node: Node?, endPoint: EndPoint, message: Message<T>, isBroadcast: Boolean = false) {
        if (node == null) return
        udpServer.send(endPoint, message, node, isBroadcast = isBroadcast)
    }

    /**
     * Broadcasts the specified message to known nodes in the network.
     *
     * @param T Message body type.
     * @param path Endpoint for the message to go to.
     * @param message Message with body of type T.
     * @param isBroadcast If true, broadcast spread will be limited to the amount specified in configuration,
     */
    fun <T> broadcast(endPoint: EndPoint, message: Message<T>, isBroadcast: Boolean = true) {
        val shuffledNodes = knownNodes.values.shuffled()
        val totalSize = shuffledNodes.size
        val amountToTake = if (isBroadcast) 5 + (configuration.broadcastSpreadPercentage * 100 / max(totalSize, 1)) else totalSize
        val nodes = shuffledNodes.take(amountToTake)
        udpServer.send(endPoint, message, *nodes.toTypedArray(), isBroadcast = isBroadcast) // TODO think of more optimal
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


