package network.abstraction

import Configuration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Logger
import network.MessageBuilder
import network.ValidatorSet
import network.data.Endpoint
import network.data.Node
import network.data.communication.Message
import network.data.communication.TransmissionLayer
import network.data.communication.TransmissionType
import network.kademlia.Kademlia
import utils.TreeUtils
import utils.asHex
import utils.sha256
import utils.tryAndReport
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.math.BigInteger
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.random.Random

/**
 * Created by mihael
 * on 26/02/2022 at 17:51
 * using IntelliJ IDEA
 */
@ExperimentalSerializationApi
abstract class NewServer(val configuration: Configuration) : Kademlia(configuration) {

    protected val validatorSet = ValidatorSet(localNode, isTrustedNode)
    private val messageHistory = ConcurrentHashMap<String, Long>()
    private val processingQueue = LinkedBlockingQueue<Message>()

    private val udpWritingBuffer = ByteBuffer.allocate(configuration.packetSplitSize)
    private val messageBuilders = ConcurrentHashMap<String, MessageBuilder>()

    private val udpOutgoingQueue = LinkedBlockingQueue<OutgoingData>()
    private val tcpOutgoingQueue = LinkedBlockingQueue<OutgoingData>()

    init {
        Thread(::listenUDP).start()
        Thread(::clearOutgoingUDP).start()

        Thread(::clearOutgoingTCP).start()
        Thread(::listenTCP).start()

        Thread(::clearProcessingQueue).start()
    }

    abstract fun processMessage(message: Message)

    inline fun <reified T> send(endpoint: Endpoint, data: T, amount: Int) {
        val randomNodes = pickRandomNodes(amount)
        send(endpoint, data, *randomNodes.map { it.publicKey }.toTypedArray())
    }

    inline fun <reified T> send(endpoint: Endpoint, data: T, vararg recipients: String) {
        val message = buildMessage(endpoint, data)
        send(message, *recipients)
    }

    fun send(message: Message, vararg recipients: String) {
        val recipientNodes = recipients.toList().ifEmpty { pickRandomNodes().map { it.publicKey } }
        when (message.endpoint.transmissionLayer) {
            TransmissionLayer.UDP -> {
                val encodedPackets = encodeToPackets(message)
                recipientNodes.forEach { publicKey ->
                    query(publicKey) {
                        udpOutgoingQueue.add(OutgoingData(it, *encodedPackets))
                    }
                }
            }
            TransmissionLayer.TCP -> {
                val encoded = ProtoBuf.encodeToByteArray(message)
                recipientNodes.forEach { publicKey ->
                    query(publicKey) {
                        tcpOutgoingQueue.add(OutgoingData(it, encoded))
                    }
                }
            }
        }
    }

    inline fun <reified T> buildMessage(endpoint: Endpoint, data: T): Message {
        val encodedBody = ProtoBuf.encodeToByteArray(data)
        val signature = crypto.sign(encodedBody)
        return Message(endpoint, crypto.publicKey, encodedBody, signature)
    }

    /** Returns [Configuration.broadcastSpreadPercentage] number of nodes.  */
    fun pickRandomNodes(amount: Int = 0): List<Node> {
        val toTake = if (amount > 0) amount else 5 + (configuration.broadcastSpreadPercentage * Integer.max(totalKnownNodes, 1) / 100)
        return getRandomNodes(toTake).filter { it.identifier != localNode.identifier }
    }

    private fun clearOutgoingTCP() {
        while (true) tryAndReport {
            val outgoing = tcpOutgoingQueue.take()
            val recipient = outgoing.recipient
            Logger.debug("Trying to connect to ${recipient.ip}:${recipient.kademliaPort}")
            Socket(recipient.ip, recipient.tcpPort).use { socket ->
                socket.getOutputStream().apply {
                    outgoing.data.forEach { write(it) }
                    flush()
                }
                socket.close()
            }
        }
    }

    private fun listenTCP() {
        while (true) tryAndReport {
            val socket = tcpSocket.accept()
            socket.use { socket ->
                Logger.info("Working on a socket!")
                val data = socket.getInputStream().readAllBytes()
                val message = ProtoBuf.decodeFromByteArray<Message>(data)
                val currentMilliseconds = System.currentTimeMillis()
                if (messageHistory.computeIfAbsent(message.uid.asHex) { currentMilliseconds } < currentMilliseconds) return@tryAndReport
                processingQueue.add(message)
                if (message.endpoint.transmissionType == TransmissionType.Broadcast) broadcast(TransmissionLayer.TCP, message.uid.asHex, data)
            }
        }
    }

    private fun broadcast(transmissionLayer: TransmissionLayer, messageId: String, vararg data: ByteArray) {
        val seed = BigInteger(messageId, 16).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
        val messageRandom = Random(seed)
        val shuffled = validatorSet.shuffled(messageRandom)
        val k = 2
        val index = shuffled.indexOf(localNode.publicKey)

        if (isTrustedNode) {
            Logger.error(TreeUtils.outputTree(k, shuffled.mapNotNull { query(it) }.map { "${it.ip}:${it.kademliaPort}".padEnd(22) }))
        }
        val broadcastNodes = mutableSetOf<String>()
        if (index != -1) {
            val currentDepth = TreeUtils.computeDepth(k, index)
            val totalNodes = TreeUtils.computeTotalNodesOnDepth(k, currentDepth)
            val minimumIndex = TreeUtils.computeMinimumIndexAtDepth(k, totalNodes, currentDepth)
            val maximumIndex = TreeUtils.computeMaximumIndexAtDepth(totalNodes)
            val neighbourIndex = (index + 1).takeIf { it <= maximumIndex && it < shuffled.size } ?: minimumIndex
            val children = TreeUtils.findChildren(k, index)
            val neighbourChildren = TreeUtils.findChildren(k, neighbourIndex)
            val neighbour = shuffled[neighbourIndex]
            val childrenKeys = children.mapNotNull { shuffled.getOrNull(it) }
            val neighbourChildrenKeys = neighbourChildren.mapNotNull { shuffled.getOrNull(it) }

            broadcastNodes.add(neighbour)
            broadcastNodes.addAll(childrenKeys)
            broadcastNodes.addAll(neighbourChildrenKeys)
            Logger.error("[$index] [$children] Neighbour: $neighbourIndex ... Children: ${childrenKeys.joinToString(",") { "${shuffled.indexOf(it)}" }}")

        }
        broadcastNodes.addAll(pickRandomNodes().map { it.publicKey })
        Logger.trace("We have to retransmit to [total: ${shuffled.size}] --> ${broadcastNodes.size} nodes.")

        broadcastNodes.forEach { publicKey ->
            query(publicKey) {
                val outgoingData = OutgoingData(it, *data)
                val outgoingQueue = if (transmissionLayer == TransmissionLayer.UDP) udpOutgoingQueue else tcpOutgoingQueue
                outgoingQueue.add(outgoingData)
            }
        }
    }

    private fun clearProcessingQueue() {
        while (true) tryAndReport {
            val message = processingQueue.take()
            processMessage(message)
        }
    }

    /**Listens for UDP [messages][Message] and adds them to the processing queue. If the Message is [TransmissionType.Broadcast] it is broadcasted. */
    private fun listenUDP() {
        val pureArray = ByteArray(configuration.packetSplitSize)
        val inputStream = DataInputStream(ByteArrayInputStream(pureArray))
        val packet = DatagramPacket(pureArray, configuration.packetSplitSize)
        while (true) tryAndReport {
            inputStream.reset()
            udpSocket.receive(packet)
            val packetIdBytes = inputStream.readNBytes(32)
            val packetId = packetIdBytes.asHex
            val messageIdBytes = inputStream.readNBytes(32)
            val messageId = messageIdBytes.asHex
            val currentMilliseconds = System.currentTimeMillis()
            if (messageHistory.computeIfAbsent(packetId) { currentMilliseconds } < currentMilliseconds)
                return@tryAndReport

            val endpoint = Endpoint.byId(inputStream.read().toByte()) ?: return@tryAndReport
            if (endpoint.transmissionType == TransmissionType.Broadcast) broadcast(TransmissionLayer.UDP, messageId, packet.data.copyOf())

            val totalSlices = inputStream.readInt()
            val currentSlice = inputStream.readInt()
            val dataLength = inputStream.readInt()
            val data = inputStream.readNBytes(dataLength)
            val messageBuilder = messageBuilders.computeIfAbsent(messageId) { MessageBuilder(totalSlices) }
            messageBuilder.addPart(currentSlice, data)

            if (messageBuilder.isReady) {
                val gluedData = messageBuilder.gluedData()
                val decoded = ProtoBuf.decodeFromByteArray<Message>(gluedData)
                processingQueue.add(decoded)
            }
        }
    }

    private fun clearOutgoingUDP() {
        while (true) tryAndReport {
            val outgoing = udpOutgoingQueue.take()
            val recipient = outgoing.recipient
            val recipientAddress = InetSocketAddress(recipient.ip, recipient.udpPort)
            outgoing.data.forEach {
                udpSocket.send(DatagramPacket(it, 0, it.size, recipientAddress))
                Thread.sleep(Random.nextLong(5))
            }
        }
    }

    private fun encodeToPackets(message: Message): Array<ByteArray> {
        /* Header length total 77B = 32B + 32B + 1B + 4B + 4B + 4B
         *   packetId: 32B
         *   messageId: 32B
         *   endpointByte: 1B
         *   slicesNeeded: 4B
         *   currentSlice: 4B
         *   dataLength: 4B
         */
        val encodedMessage = ProtoBuf.encodeToByteArray(message)
        val encodedMessageLength = encodedMessage.size
        val allowedDataSize = configuration.packetSplitSize - 78
        val slicesNeeded = encodedMessageLength / allowedDataSize + 1
        return (0 until slicesNeeded).map { slice ->
            val uuid = "$${UUID.randomUUID()}".toByteArray()
            val from = slice * allowedDataSize
            val to = Integer.min(from + allowedDataSize, encodedMessageLength)
            val data = encodedMessage.sliceArray(from until to)
            val packetId = sha256(uuid + data)
            val endpoint = message.endpoint
            udpWritingBuffer
                .clear()
                .put(packetId)
                .put(message.uid)
                .put(endpoint.ordinal.toByte()) // ToDo change from byte
                .putInt(slicesNeeded)
                .putInt(slice)
                .putInt(data.size)
                .put(data)
                .array()
                .copyOf(udpWritingBuffer.position())
        }.toTypedArray()
    }

    private fun decodePackets(messageBuilder: MessageBuilder): Message {
        val data = messageBuilder.gluedData()
        return ProtoBuf.decodeFromByteArray(data)
    }

}