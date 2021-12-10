package network

import data.Configuration
import data.communication.Message
import data.communication.TransmissionType
import data.network.Endpoint
import data.network.Node
import kademlia.Kademlia
import logging.Dashboard
import logging.Logger
import utils.Crypto
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256
import utils.tryAndReport
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Created by Mihael Valentin Berčič
 * on 13/04/2021 at 00:57
 * using IntelliJ IDEA
 */
abstract class Server(val configuration: Configuration) {

    val crypto = Crypto(".")
    val localAddress = InetAddress.getLocalHost()
    val localNode = Node(localAddress.hostAddress, configuration.port, crypto.publicKey)
    val isTrustedNode = localNode.let { node -> node.ip == configuration.trustedNodeIP && node.port == configuration.trustedNodePort }
    val kademlia = Kademlia(localNode, configuration.port + 2)

    private val receivedQueue = LinkedBlockingQueue<MessageBuilder>()
    private val outgoingQueue = LinkedBlockingQueue<OutgoingQueuedMessage>()
    private val queuedForLater = ConcurrentHashMap<String, OutgoingQueuedMessage>()

    private val networkHistory = ConcurrentHashMap<String, Long>()
    private val messageBuilders = mutableMapOf<String, MessageBuilder>()
    private val udpSocket = DatagramSocket(configuration.port)
    protected val tcpSocket = ServerSocket(configuration.port + 1)
    private var started = false

    open fun launch() {
        if (isTrustedNode) Logger.info("We're the trusted node.")
        if (started) throw IllegalStateException("Nion has already started.")
        startHistoryCleanup()
        Thread(this::listenForUDP).start()
        Thread(this::sendUDP).start()
        Thread(this::processReceivedMessages).start()
        started = true
    }

    private fun listenForUDP() {
        val pureArray = ByteArray(configuration.packetSplitSize)
        val inputStream = DataInputStream(ByteArrayInputStream(pureArray))
        val packet = DatagramPacket(pureArray, configuration.packetSplitSize)
        while (true) tryAndReport {
            inputStream.reset()
            udpSocket.receive(packet)
            val packetId = inputStream.readNBytes(32).asHex
            val messageId = inputStream.readNBytes(32).asHex
            if (networkHistory.containsKey(packetId) || networkHistory.containsKey(messageId)) return@tryAndReport
            networkHistory[packetId] = System.currentTimeMillis()
            inputStream.apply {
                val isBroadcast = read() == 1
                val endpoint = Endpoint.byId(read().toByte()) ?: return@apply
                val totalSlices = readInt()
                val currentSlice = readInt()
                val dataLength = readInt()
                val data = readNBytes(dataLength)
                val messageBuilder = messageBuilders.computeIfAbsent(messageId) {
                    val broadcastNodes = if (isBroadcast) pickRandomNodes() else emptyList()
                    MessageBuilder(endpoint, totalSlices, broadcastNodes)
                }
                if (isBroadcast) {
                    messageBuilder.nodes.forEach { node ->
                        packet.socketAddress = InetSocketAddress(node.ip, node.port)
                        udpSocket.send(packet)
                    }
                }
                if (!kademlia.isBootstrapped) {
                    kademlia.bootstrap(configuration.trustedNodeIP, configuration.trustedNodePort)
                    return@tryAndReport
                }
                if (messageBuilder.addPart(currentSlice, data)) {
                    messageBuilders.remove(messageId)
                    receivedQueue.put(messageBuilder)
                    networkHistory[messageId] = System.currentTimeMillis()
                }
            }
        }
    }

    private fun sendUDP() {
        val dataBuffer = ByteBuffer.allocate(configuration.packetSplitSize)
        while (true) tryAndReport {
            val outgoingMessage = outgoingQueue.take()
            val encodedMessage = outgoingMessage.message
            /* Header length total 78B = 32B + 1B + 1B + 32B + 4B + 4B + 4B
            *   packetId: 32B
            *   messageId: 32B
            *   broadcastByte: 1B
            *   endpointByte: 1B
            *   slicesNeeded: 4B
            *   currentSlice: 4B
            *   dataLength: 4B
            */
            val encodedMessageLength = encodedMessage.size
            val allowedDataSize = configuration.packetSplitSize - 78
            val slicesNeeded = encodedMessageLength / allowedDataSize + 1
            dataBuffer.apply {
                (0 until slicesNeeded).forEach { slice ->
                    clear()
                    val uuid = "$${UUID.randomUUID()}".toByteArray()
                    val from = slice * allowedDataSize
                    val to = Integer.min(from + allowedDataSize, encodedMessageLength)
                    val data = encodedMessage.sliceArray(from until to)
                    val packetId = sha256(uuid + data)

                    put(packetId)
                    put(outgoingMessage.messageUID)
                    put(if (outgoingMessage.transmissionType == TransmissionType.Broadcast) 1 else 0)
                    put(outgoingMessage.endpoint.identification)
                    putInt(slicesNeeded)
                    putInt(slice)
                    putInt(data.size)
                    put(data)

                    val packet = DatagramPacket(array(), 0, position())
                    val started = System.currentTimeMillis()
                    val recipientNode = outgoingMessage.recipient
                    val recipientAddress = InetSocketAddress(recipientNode.ip, recipientNode.port)
                    packet.socketAddress = recipientAddress
                    udpSocket.send(packet)
                    val delay = System.currentTimeMillis() - started
                    val sender = localAddress.toString()
                    val recipient = recipientAddress.toString()
                    Dashboard.sentMessage(outgoingMessage.messageUID.asHex, outgoingMessage.endpoint, sender, recipient, data.size, delay)
                    // Logger.trace("Sent to ${outgoingMessage.endpoint}...")
                }

            }
            Logger.debug("Server sent UDP packet to ${outgoingMessage.endpoint} @ ${outgoingMessage.recipient.identifier.take(5)}")
        }
    }

    private fun processReceivedMessages() {
        while (true) {
            val messageBuilder = receivedQueue.take()
            onMessageReceived(messageBuilder.endpoint, messageBuilder.gluedData())
        }
    }

    abstract fun onMessageReceived(endpoint: Endpoint, data: ByteArray)

    private class OutgoingQueuedMessage(
        val endpoint: Endpoint,
        val transmissionType: TransmissionType,
        val messageUID: ByteArray,
        val message: ByteArray,
        val recipient: Node
    )

    fun send(endpoint: Endpoint, transmissionType: TransmissionType, message: Message, encodedMessage: ByteArray, vararg publicKeys: String) {
        val keys = publicKeys.takeIf { it.isNotEmpty() } ?: pickRandomNodes().map { it.publicKey }.toTypedArray()
        keys.forEach { key ->
            val identifier = sha256(key).asHex.take(5)
            Logger.info("Looking for $identifier to send a message to $endpoint.")
            kademlia.query(key) {
                Logger.info("Found the one i was looking for!")
                outgoingQueue.put(OutgoingQueuedMessage(endpoint, transmissionType, sha256(message.uid), encodedMessage, it))
            }
        }
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

    /** Returns [Configuration.broadcastSpreadPercentage] number of nodes.  */
    fun pickRandomNodes(amount: Int = 0): List<Node> {
        val totalKnownNodes = kademlia.totalKnownNodes
        val toTake = if (amount > 0) amount else 5 + (configuration.broadcastSpreadPercentage * Integer.max(totalKnownNodes, 1) / 100)
        return kademlia.getRandomNodes(toTake).filter { it != localNode }
    }

}