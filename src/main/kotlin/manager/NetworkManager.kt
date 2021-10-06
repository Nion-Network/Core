package manager

import chain.BlockProducer
import chain.ChainManager
import communication.Message
import communication.QueuedMessage
import communication.TransmissionType
import communication.UDPServer
import data.*
import data.Endpoint.*
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import utils.Crypto
import utils.asMessage
import java.lang.Integer.max
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:58
 * using IntelliJ IDEA
 */
class NetworkManager(val configuration: Configuration, val dashboard: Dashboard, private val listeningPort: Int) {

    private val myIP: String = InetAddress.getLocalHost().hostAddress

    var isInNetwork = false
    val knownNodes = ConcurrentHashMap<String, Node>()
    val isFull: Boolean get() = knownNodes.size >= configuration.maxNodes

    val isTrustedNode: Boolean = configuration.let { InetAddress.getLocalHost().hostAddress == it.trustedNodeIP && it.trustedNodePort == listeningPort }
    val crypto = Crypto(".")
    val ourNode = Node(crypto.publicKey, myIP, listeningPort)

    private val dht = DistributedHashTable(this)
    private val vdf = VerifiableDelayFunctionManager()
    val docker = DockerManager(dht, crypto, dashboard, configuration)

    private val networkHistory = ConcurrentHashMap<String, Long>()

    val informationManager = InformationManager(this)
    private val messageQueue = LinkedBlockingDeque<QueuedMessage<*>>()

    private val blockProducer = BlockProducer(crypto, configuration, isTrustedNode)

    private val chainManager = ChainManager(this, crypto, configuration, vdf, dht, docker, dashboard, informationManager, blockProducer)
    private val committeeManager = CommitteeManager(this, crypto, vdf, dashboard)

    val udp = UDPServer(configuration, crypto, dashboard, knownNodes, networkHistory, listeningPort)
    private val httpServer = Javalin.create { it.showJavalinBanner = false }.start(listeningPort + 5)


    init {
        Logger.toggleLogging(configuration.loggingEnabled || (isTrustedNode && configuration.trustedLoggingEnabled))
        httpServer.before {
            if (it.ip().startsWith("127")) return@before
            val hex = it.header("hex") ?: ""
            if (networkHistory.containsKey(hex)) throw ForbiddenResponse("NO MEANS NO")
            else networkHistory[hex] = System.currentTimeMillis()
        }
        httpServer.exception(Exception::class.java) { exception, _ ->
            exception.printStackTrace()
            dashboard.reportException(exception)
        }
    }

    fun start() {
        Logger.debug("My IP is $myIP")

        udp.startListening { endPoint, data ->
            try {
                when (endPoint) {
                    NodeQuery -> data executeImmediately dht::onQuery
                    NodeFound -> data executeImmediately dht::onFound
                    SyncRequest -> data executeImmediately chainManager::syncRequestReceived
                    Endpoint.VoteRequest -> data executeImmediately committeeManager::voteRequest

                    Welcome -> data queueMessage dht::onJoin
                    NewBlock -> data queueMessage chainManager::blockReceived
                    SyncReply -> data queueMessage chainManager::syncReplyReceived
                    JoinRequest -> data queueMessage dht::joinRequest
                    VoteReceived -> data queueMessage chainManager::voteReceived
                    NodeStatistics -> data queueMessage informationManager::dockerStatisticsReceived
                    RepresentativeStatistics -> data queueMessage informationManager::representativeStatisticsReceived
                    InclusionRequest -> data queueMessage chainManager::inclusionRequest
                    else -> {
                        Logger.error("Unexpected $endPoint in packet handler.")
                        dashboard.reportException(Exception("No fucking endpoint $endPoint."))
                    }
                }
            } catch (e: Exception) {
                dashboard.reportException(e)
                e.printStackTrace()
            }
        }

        httpServer.apply {
            get("/ping") { Logger.info("Pinged me!") }
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

        val trustedNode = Node("", configuration.trustedNodeIP, configuration.trustedNodePort)
        sendUDP(JoinRequest, ourNode, TransmissionType.Broadcast, trustedNode)

        Logger.debug("Waiting to be accepted into the network...")
        Thread.sleep(10000)
        if (!isInNetwork) joinTheNetwork()
        else {
            Logger.debug("We're in the network. Happy networking!")
            chainManager.requestInclusion()
            chainManager.requestSync()
        }
    }

    /** Runs the thread that is in charge of [message queue][messageQueue] processing. */
    private fun startQueueThread() {
        Thread {
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
    }

    /** Schedules message history cleanup service that runs at fixed rate. */
    private fun startHistoryCleanup() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
            networkHistory.forEach { (messageHex, timestamp) ->
                val difference = System.currentTimeMillis() - timestamp
                val shouldBeRemoved = TimeUnit.MILLISECONDS.toMinutes(difference) >= configuration.historyMinuteClearance
                if (shouldBeRemoved) networkHistory.remove(messageHex)
            }
        }, 0, configuration.historyCleaningFrequency, TimeUnit.MINUTES)
    }

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


    /**
     * Sends the specified message to either specified or random nodes in the network.
     *
     * @param T Message type.
     * @param endpoint [Endpoint] for which the message is targeted.
     * @param message Body to be sent to the specified endPoint.
     * @param transmissionType How should the message be sent.
     * @param nodes If this field is empty, it'll send to random nodes of quantity specified by [Configuration]
     */
    inline fun <reified T : Any> sendUDP(endpoint: Endpoint, data: T, transmissionType: TransmissionType, vararg nodes: Node) {
        // TODO add "additionalNodes" flag.
        val message = generateMessage(data)
        val encoded = ProtoBuf { encodeDefaults = true }.encodeToByteArray(message)
        val encodedJson = Json.encodeToString(message).encodeToByteArray()
        dashboard.logMessageSize(encoded.size, encodedJson.size)

        val id = message.uid
        if (nodes.isEmpty()) {
            val shuffledNodes = knownNodes.values.shuffled()
            val totalSize = shuffledNodes.size
            val amountToTake = 5 + (configuration.broadcastSpreadPercentage * max(totalSize, 1) / 100)
            udp.send(endpoint, id, encoded, transmissionType, shuffledNodes.take(amountToTake).toTypedArray())
        } else udp.send(endpoint, id, encoded, transmissionType, nodes)
    }

    /** Sends the data to the [specific amount][nodeCount] of nodes using [transmissionType]. */
    inline fun <reified T : Any> sendUDP(endpoint: Endpoint, data: T, transmissionType: TransmissionType, nodeCount: Int) {
        val toSend = knownNodes.values.shuffled().take(nodeCount)
        sendUDP(endpoint, data, transmissionType, *toSend.toTypedArray())
    }

    /** Clears the [messageQueue]. */
    fun clearMessageQueue() {
        messageQueue.clear()
    }

    /**
     * Create a generics message ready to be sent across the network.
     *
     * @param T Message body class type
     * @param data Body of type T to be serialized into JSON.
     * @return Message with the signed body type of T, current publicKey and the body itself.
     */
    inline fun <reified T> generateMessage(data: T): Message<T> {
        return Message(
            crypto.publicKey,
            crypto.sign(ProtoBuf { encodeDefaults = true }.encodeToByteArray(data)),
            data
        )
    }

    /** Immediately executes the callback with the message received. */
    private inline infix fun <reified T> ByteArray.executeImmediately(crossinline block: Message<T>.() -> Unit) {
        block.invoke(asMessage())
    }

}


