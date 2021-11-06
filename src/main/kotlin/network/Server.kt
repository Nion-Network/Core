package network

import data.Configuration
import data.communication.Message
import data.communication.TransmissionType
import data.network.Endpoint
import data.network.Node
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logging.Dashboard
import utils.Crypto
import utils.Utils.Companion.sha256
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
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
abstract class Server(protected val configuration: Configuration) {

    val crypto = Crypto(".")
    val localAddress = InetAddress.getLocalHost()
    val localNode = Node(crypto.publicKey, localAddress.hostAddress, configuration.port)

    private val outgoingQueue = LinkedBlockingQueue<OutgoingQueuedMessage>()
    private val receivedQueue = LinkedBlockingQueue<MessageBuilder>()
    private val queuedForLater = ConcurrentHashMap<String, ByteArray>()
    protected val knownNodes = ConcurrentHashMap<String, Node>()

    private val networkHistory = ConcurrentHashMap<String, Long>()
    private val messageBuilders = mutableMapOf<ByteArray, MessageBuilder>()
    private val udpSocket = DatagramSocket(configuration.port)
    private val tcpSocket = ServerSocket(configuration.port + 1)
    private var started = false

    fun launch() {
        if (started) throw IllegalStateException("Nion has already started.")
        startHistoryCleanup()
        Thread(this::listenForUDP).start()
        Thread(this::sendUDP).start()
        Thread(this::processReceivedMessages).start()
        knownNodes["miha"] = localNode
        started = true
    }

    private fun listenForUDP() {
        val pureArray = ByteArray(configuration.packetSplitSize)
        val inputStream = DataInputStream(ByteArrayInputStream(pureArray))
        val packet = DatagramPacket(pureArray, configuration.packetSplitSize)
        while (true) inputStream.apply {
            reset()
            udpSocket.receive(packet)
            val packetId = readNBytes(32)
            val messageId = readNBytes(32)
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

            if (messageBuilder.addPart(currentSlice, data)) {
                messageBuilders.remove(messageId)
                receivedQueue.put(messageBuilder)
            }

        }
    }

    private fun sendUDP() {
        val dataBuffer = ByteBuffer.allocate(configuration.packetSplitSize)
        while (true) dataBuffer.apply {
            val outgoingMessage = outgoingQueue.take()
            val encodedMessage = outgoingMessage.message
            val recipients = outgoingMessage.recipients
            val readyToSend = if (recipients.isEmpty()) pickRandomNodes() else recipients.mapNotNull { knownNodes[it] }
            val readyForSearch = recipients.filter { !knownNodes.containsKey(it) }
            readyForSearch.forEach { queuedForLater[it] = encodedMessage }

            /* Header length total 72B = 32B + 1B + 1B + 32B + 4B + 4B + 4B
            *   packetId: 32B
            *   messageId: 32B
            *   broadcastByte: 1B
            *   endpointByte: 1B
            *   slicesNeeded: 4B
            *   currentSlice: 4B
            *   dataLength: 4B
            */
            val encodedMessageLength = encodedMessage.size
            val allowedDataSize = configuration.packetSplitSize - 72
            val slicesNeeded = encodedMessageLength / allowedDataSize + 1
            (0 until slicesNeeded).forEach { slice ->
                clear()
                val uuid = "$${UUID.randomUUID()}".toByteArray()
                val from = slice * allowedDataSize
                val to = Integer.min(from + allowedDataSize, encodedMessageLength)
                val data = encodedMessage.sliceArray(from until to)
                val packetId = sha256(uuid + data)
                val messageId = sha256(outgoingMessage.messageUID)

                put(packetId)
                put(messageId)
                put(if (outgoingMessage.transmissionType == TransmissionType.Broadcast) 1 else 0)
                put(outgoingMessage.endpoint.identification)
                putInt(slicesNeeded)
                putInt(slice)
                putInt(data.size)
                put(data)

                val packet = DatagramPacket(array(), 0, position())
                val started = System.currentTimeMillis()
                readyToSend.forEach { node ->
                    val recipientAddress = InetSocketAddress(node.ip, node.port)
                    packet.socketAddress = recipientAddress
                    udpSocket.send(packet)
                    val delay = System.currentTimeMillis() - started
                    val sender = localAddress.toString()
                    val recipient = recipientAddress.toString()
                    Dashboard.sentMessage(messageId.toString(), outgoingMessage.endpoint, sender, recipient, data.size, delay)
                }
            }

        }
    }

    private fun processReceivedMessages() {
        while (true) {
            val messageBuilder = receivedQueue.take()
            onMessageReceived(messageBuilder.endpoint, messageBuilder.gluedData())
        }
    }

    open fun onMessageReceived(endpoint: Endpoint, data: ByteArray) {

    }

    private class OutgoingQueuedMessage(
        val endpoint: Endpoint,
        val transmissionType: TransmissionType,
        val messageUID: String,
        val message: ByteArray,
        vararg val recipients: String
    )

    fun send(endpoint: Endpoint, transmissionType: TransmissionType, message: Message, encodedMessage: ByteArray, vararg publicKeys: String) {
        outgoingQueue.add(OutgoingQueuedMessage(endpoint, transmissionType, message.uid, encodedMessage, *publicKeys))
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
        val totalKnownNodes = knownNodes.size
        val toTake = if (amount > 0) amount else 5 + (configuration.broadcastSpreadPercentage * Integer.max(totalKnownNodes, 1) / 100)
        return knownNodes.values.shuffled().take(toTake)
    }

}