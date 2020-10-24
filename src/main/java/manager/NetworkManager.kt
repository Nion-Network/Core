package manager

import data.*
import data.EndPoint.*
import data.NetworkRequestType.GET
import data.NetworkRequestType.POST
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import logging.Logger
import utils.Crypto
import utils.Utils
import utils.getMessage
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:58
 * using IntelliJ IDEA
 */
class NetworkManager(configFileContent: String) {

    var isInNetwork = false
    val knownNodes = mutableMapOf<String, Node>()
    val isFull: Boolean get() = knownNodes.size >= configuration.maxNodes

    val configuration: Configuration = Utils.gson.fromJson<Configuration>(configFileContent, Configuration::class.java)
    val crypto = Crypto(".")
    val vdf = VDFManager()
    val dht = DHTManager(this)
    val dashboard = DashboardManager(configuration)

    private val networkHistory = hashMapOf<String, Long>()
    private val messageQueue = LinkedBlockingQueue<QueuedMessage<*>>()
    private val startingInclusionSet = if (configuration.isTrustedNode) mutableMapOf(crypto.publicKey to true) else mutableMapOf()

    val currentState = State(0, 0, 0, configuration.initialDifficulty, startingInclusionSet)

    private val chainManager = ChainManager(this)
    private val committeeManager = CommitteeManager(this)
    private val validatorManager = chainManager.validatorManager

    private val server = Javalin.create { it.showJavalinBanner = false }.start(configuration.listeningPort)

    private val myIP: String get() = InetAddress.getLocalHost().hostAddress

    val ourNode get() = Node(crypto.publicKey, myIP, configuration.listeningPort)

    init {
        server.before { if (networkHistory.containsKey(it.header("hex"))) throw ForbiddenResponse("NO MEANS NO") }
        server.exception(Exception::class.java) { exception, _ -> exception.printStackTrace() }
    }

    fun start() {
        Logger.trace("My IP is $myIP")

        PING run { status(200) }
        SEARCH run { queryParam("pub_key")?.apply { dht.sendSearchQuery(this) } }

        JOIN processMessage dht::joinRequest
        QUERY processMessage dht::onQuery

        FOUND processMessage dht::onFound
        JOINED processMessage dht::onJoin
        VOTE_REQUEST processMessage committeeManager::voteRequest

        VOTE queueMessage chainManager::voteReceived
        BLOCK queueMessage chainManager::blockReceived
        INCLUDE queueMessage validatorManager::inclusionRequest
        SYNC_REPLY queueMessage chainManager::syncReplyReceived
        SYNC_REQUEST queueMessage chainManager::syncRequestReceived

        if (!configuration.isTrustedNode) joinTheNetwork()
        else Logger.debug("We're the trusted node!")

        Logger.debug("Listening on port: " + configuration.listeningPort)
        startQueueThread()
        startHistoryCleanup()
    }


    /**
     * Sends the Join request to the trusted node and waits to be accepted into the network.
     *
     */
    private fun joinTheNetwork() {
        Logger.trace("Sending join request to our trusted node...")
        val response = Utils.sendMessageTo(configuration.trustedHttpAddress, "/join", generateMessage(ourNode))
        Logger.trace("Join response from trusted node: $response")

        while (!isInNetwork) {
            Logger.trace("Waiting to be accepted into the network...")
            Thread.sleep(1000)
        }

        Logger.debug("We're in the network. Happy networking!")
        chainManager.requestSync()
    }

    /**
     * Runs the thread that is in charge of queue processing.
     *
     */
    private fun startQueueThread() = Thread {
        while (true) messageQueue.take().apply {
            if (!networkHistory.containsKey(hex)) {
                execute()
                networkHistory[hex] = System.currentTimeMillis()
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
    }, 0, configuration.historyCleaningFrequency.toLong(), TimeUnit.MINUTES)

    /**
     *  Set networking to respond using given lambda block to provided path on GET request.
     *
     * @param block Response lambda that will execute on GET request.
     */
    private infix fun String.get(block: Context.() -> Unit): Javalin = server.get(this, block)

    /**
     *  Set networking to respond using given lambda block to provided path on POST request.
     *
     * @param block Response lambda that will execute on POST request.
     */
    private infix fun String.post(block: Context.() -> Unit): Javalin = server.post(this, block)

    /**
     *  Set networking to immediately respond using given lambda block to provided path on POST request.
     *
     * @param block Response lambda that will execute on POST request.
     */
    private infix fun EndPoint.run(block: Context.() -> Unit): Javalin = when (requestType) {
        GET -> path get block
        POST -> path post block
    }

    /**
     * Immediately process the received Message of type T from the request on specified endpoint.
     *
     * @param T Message body type.
     * @param block Lambda that processes the message.
     * @return
     */
    private inline infix fun <reified T> EndPoint.processMessage(crossinline block: (Message<T>) -> Unit): Javalin = when (requestType) {
        GET -> path get { block(getMessage()) }
        POST -> path post { block(getMessage()) }
    }

    /**
     * Add the received Message with the body of type T to the message queue.
     *
     * @param T Message's body type.
     * @param block Lambda that is executed when message is taken out of queue.
     */
    private inline infix fun <reified T> EndPoint.queueMessage(noinline block: (Message<T>) -> Unit) = when (requestType) {
        GET -> path get { }
        POST -> path post { header("hex")?.apply { messageQueue.put(QueuedMessage(this, getMessage(), block)) } }
    }

    /**
     * Sends the message to a few randomly chosen nodes from the known nodes.
     *
     * @param T Message body type.
     * @param path Endpoint for the message to go to.
     * @param spread Amount of nodes to choose (if less are known, all are chosen)
     * @param message Message with body of type T.
     */
    fun <T> sendMessageToRandomNodes(path: String, spread: Int, message: Message<T>) = pickRandomNodes(spread).forEach { it.sendMessage(path, message) }

    /**
     * Broadcasts the specified message to known nodes in the network.
     *
     * @param T Message body type.
     * @param path Endpoint for the message to go to.
     * @param message Message with body of type T.
     * @param limited If true, broadcast spread will be limited to the amount specified in configuration,
     */
    fun <T> broadcast(path: String, message: Message<T>, limited: Boolean = false) {
        val hexHash = message.hex
        networkHistory[hexHash] = message.timeStamp
        val shuffledNodes = knownNodes.values.shuffled()
        val amountToTake = if (limited) configuration.broadcastSpread else shuffledNodes.size
        for (node in shuffledNodes.take(amountToTake)) node.sendMessage(path, message)
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
}