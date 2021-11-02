package network

import chain.ChainBuilder
import communication.UDPServer
import consensus.CommitteeStrategy
import data.*
import data.communication.Message
import data.communication.QueuedMessage
import data.communication.TransmissionType
import data.network.Endpoint
import data.network.Endpoint.*
import data.network.Node
import docker.DockerDataProxy
import docker.DockerMigrationStrategy
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import manager.InformationManager
import manager.VerifiableDelayFunctionManager
import utils.Crypto
import utils.asMessage
import java.lang.Integer.max
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:58
 * using IntelliJ IDEA
 */
class Network(val configuration: Configuration, val listeningPort: Int) {

    private val myIP: String = InetAddress.getLocalHost().hostAddress
    private val localAddress = InetAddress.getLocalHost()

    val crypto = Crypto(".")
    val ourNode = Node(crypto.publicKey, myIP, listeningPort)

    var isInNetwork = false
    val knownNodes = ConcurrentHashMap<String, Node>()
    val isFull: Boolean get() = knownNodes.size >= configuration.maxNodes

    val isTrustedNode: Boolean = configuration.let { localAddress.hostAddress == it.trustedNodeIP && it.trustedNodePort == listeningPort }


    val dht = DistributedHashTable(this)
    private val vdf = VerifiableDelayFunctionManager()
    private val dockerDataProxy = DockerDataProxy(crypto)
    val docker = DockerMigrationStrategy(dht, dockerDataProxy, this, configuration)

    private val informationManager = InformationManager(dht, dockerDataProxy, this)
    private val messageQueue = LinkedBlockingDeque<QueuedMessage<*>>()
    private val committeeStrategy = CommitteeStrategy(this, crypto, vdf)
    private val chainBuilder = ChainBuilder(informationManager, docker, this, dht, crypto, configuration, committeeStrategy, vdf)

    val udp = UDPServer(configuration, crypto, knownNodes, listeningPort)

    init {
        Logger.toggleLogging(configuration.loggingEnabled || (isTrustedNode && configuration.trustedLoggingEnabled))
    }

    fun start() {
        Logger.debug("My IP is $myIP")
        startListeningUDP()
        startQueueThread()

        if (!isTrustedNode) joinTheNetwork()
        else Logger.debug("We're the trusted node!")

        Logger.debug("Listening on port: $listeningPort")
    }

    private fun startListeningUDP() {
        udp.startListening { endPoint, data ->
            when (endPoint) {
                NodeQuery -> data executeImmediately dht::onQuery
                NodeFound -> data queueWith dht::onFound
                SyncRequest -> data executeImmediately chainBuilder::syncRequested
                VoteRequest -> data executeImmediately committeeStrategy::voteRequested

                SyncReply -> data queueWith chainBuilder::syncReplyReceived
                Vote -> data queueWith committeeStrategy::voteReceived
                Welcome -> data queueWith dht::onJoin
                NewBlock -> data queueWith chainBuilder::blockReceived
                JoinRequest -> data queueWith dht::joinRequest
                NodeStatistics -> data queueWith informationManager::dockerStatisticsReceived
                RepresentativeStatistics -> data queueWith informationManager::representativeStatisticsReceived
                InclusionRequest -> data queueWith chainBuilder::inclusionRequested
                else -> {
                    Logger.error("Unexpected $endPoint in packet handler.")
                    Dashboard.reportException(Exception("No endpoint $endPoint."))
                }
            }
        }
    }

    /**
     * Sends the Join request to the trusted node and waits to be accepted into the network.
     *
     */
    private fun joinTheNetwork() {
        Logger.info("Sending join request to our trusted node...")

        val trustedNode = Node("", configuration.trustedNodeIP, configuration.trustedNodePort)
        send(JoinRequest, TransmissionType.Broadcast, ourNode, trustedNode)

        Logger.debug("Waiting to be accepted into the network...")
        Thread.sleep(10000)
        if (!isInNetwork) joinTheNetwork()
        else {
            Logger.debug("We're in the network. Happy networking!")
            chainBuilder.requestInclusion()
            chainBuilder.requestSync()
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
                    Dashboard.reportException(e)
                }
            }
        }.start()
    }

    /**
     * Add the received Message with the body of type T to the message queue.
     *
     * @param T Message's body type.
     * @param block Lambda that is executed when message is taken out of queue.
     */
    private inline infix fun <reified T> ByteArray.queueWith(noinline block: (Message<T>) -> Unit) {
        messageQueue.add(QueuedMessage(asMessage(), block))
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
    inline fun <reified T : Any> send(endpoint: Endpoint, transmissionType: TransmissionType, data: T, vararg nodes: Node) {
        val message = generateMessage(data)
        val encoded = encodeMessage(message)
        val id = message.uid
        if (nodes.isEmpty()) {
            val shuffledNodes = knownNodes.values.shuffled()
            val totalSize = shuffledNodes.size
            val amountToTake = 5 + (configuration.broadcastSpreadPercentage * max(totalSize, 1) / 100)
            udp.send(endpoint, transmissionType, id, encoded, *shuffledNodes.take(amountToTake).toTypedArray())
        } else udp.send(endpoint, transmissionType, id, encoded, *nodes)
    }

    /** Sends the message to passed nodes after they've been found by the network. */
    inline fun <reified T : Any> searchAndSend(endpoint: Endpoint, transmissionType: TransmissionType, data: T, vararg publicKeys: String) {
        val message = generateMessage(data)
        val encoded = encodeMessage(message)
        val id = message.uid
        publicKeys.forEach { publicKey ->
            dht.searchFor(publicKey) { node ->
                udp.send(endpoint, transmissionType, id, encoded, node)
            }
        }
    }

    /** Sends the data to the [specific amount][nodeCount] of nodes using [transmissionType]. */
    inline fun <reified T : Any> send(endpoint: Endpoint, transmissionType: TransmissionType, data: T, nodeCount: Int) {
        val toSend = knownNodes.values.shuffled().take(nodeCount)
        send(endpoint, transmissionType, data, *toSend.toTypedArray())
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

    inline fun <reified T> encodeMessage(message: Message<T>): ByteArray {
        val encoded = ProtoBuf { encodeDefaults = true }.encodeToByteArray(message)
        val encodedJson = Json.encodeToString(message).encodeToByteArray()
        Dashboard.logMessageSize(encoded.size, encodedJson.size)
        return encoded
    }

    /** Immediately executes the callback with the message received. */
    private inline infix fun <reified T> ByteArray.executeImmediately(crossinline block: Message<T>.() -> Unit) {
        try {
            block.invoke(asMessage())
        } catch (e: Exception) {
            Dashboard.reportException(e)
        }
    }

}


