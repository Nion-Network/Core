package network.messaging

import Configuration
import chain.ValidatorSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import network.data.Endpoint
import network.data.Node
import network.data.TransmissionLayer
import network.data.TransmissionType
import network.data.messages.Message
import network.kademlia.Kademlia
import network.rpc.Topic
import utils.*
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
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.random.Random

/**
 * Created by mihael
 * on 26/02/2022 at 17:51
 * using IntelliJ IDEA
 */
@ExperimentalSerializationApi
abstract class Server(val configuration: Configuration) : Kademlia(configuration) {

    protected val validatorSet = ValidatorSet(localNode, isTrustedNode)
    private val messageHistory = ConcurrentHashMap<String, Long>()
    private val processingQueue = LinkedBlockingQueue<Message>()

    private val udpWritingBuffer = ByteBuffer.allocate(configuration.packetSplitSize)
    private val messageBuilders = ConcurrentHashMap<String, MessageBuilder>()

    private val outgoingMessageQueue = LinkedBlockingQueue<QueuedMessage>()
    private val udpOutgoingQueue = LinkedBlockingQueue<OutgoingData>()
    private val tcpOutgoingQueue = LinkedBlockingQueue<OutgoingData>()

    init {
        Thread(::listenUDP).start()
        Thread(::clearOutgoingUDP).start()

        Thread(::clearOutgoingTCP).start()
        Thread(::listenTCP).start()

        Thread(::sendQueuedMessages).start()
        Thread(::clearProcessingQueue).start()
        val period = configuration.historyCleaningFrequency * 60_000
        val maximumAge = configuration.historyMinuteClearance * 60_000
        Timer().scheduleAtFixedRate(configuration.slotDuration, period) {
            val currentTime = System.currentTimeMillis()
            Logger.trace("Clearing message history...")
            messageHistory.entries.removeIf { (_, value) -> currentTime - value > maximumAge }
        }
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
        outgoingMessageQueue.add(QueuedMessage(message, recipients))
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

    private fun sendQueuedMessages() {
        while (true) {
            val queuedMessage = outgoingMessageQueue.take()
            val message = queuedMessage.message
            val recipients = queuedMessage.recipients

            val recipientNodes = recipients.toList().ifEmpty {
                if (validatorSet.activeValidators.isEmpty() || !configuration.useTreeBasedMessageRoutingProtocol) return@ifEmpty pickRandomNodes().map { it.publicKey }
                val messageId = message.uid.asHex
                val seed = BigInteger(messageId, 16).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
                val messageRandom = Random(seed)
                val shuffled = validatorSet.shuffled(messageRandom)
                shuffled.take(1)
            }
            val transmissionLayer = message.endpoint.transmissionLayer

            val data: Array<ByteArray>
            val outgoingQueue: LinkedBlockingQueue<OutgoingData>

            when (transmissionLayer) {
                TransmissionLayer.UDP -> {
                    data = encodeToPackets(message)
                    outgoingQueue = udpOutgoingQueue
                }
                else -> {
                    data = arrayOf(ProtoBuf.encodeToByteArray(message))
                    outgoingQueue = tcpOutgoingQueue
                }
            }

            recipientNodes.forEach { publicKey ->
                query(publicKey) { outgoingQueue.add(OutgoingData(it, *data)) }
            }

            if (message.endpoint == Endpoint.NewBlock) {
                Dashboard.sentMessage(message.uid.asHex, message.endpoint, localNode.publicKey, message.body.size)
            }
        }
    }

    private fun clearOutgoingTCP() {
        while (true) tryAndReport {
            val outgoing = tcpOutgoingQueue.take()
            val recipient = outgoing.recipient
            launchCoroutine {
                withContext(Dispatchers.IO) {
                    Socket(recipient.ip, recipient.tcpPort).use { socket ->
                        socket.getOutputStream().apply {
                            outgoing.data.forEach { write(it) }
                            flush()
                        }
                    }
                }
            }
        }
    }

    private fun listenTCP() {
        while (true) tryAndReport {
            tcpSocket.accept().use { socket ->
                val data = socket.getInputStream().readAllBytes()
                val message = ProtoBuf.decodeFromByteArray<Message>(data)
                if (alreadySeen(message.uid.asHex)) return@use
                processingQueue.add(message)
                if (message.endpoint.transmissionType == TransmissionType.Broadcast) broadcast(TransmissionLayer.TCP, message.uid.asHex, data)
            }
        }
    }

    private fun alreadySeen(id: String): Boolean {
        val currentTime = System.currentTimeMillis()
        return messageHistory.computeIfAbsent(id) { System.currentTimeMillis() } < currentTime

    }

    private fun broadcast(transmissionLayer: TransmissionLayer, messageId: String, vararg data: ByteArray) {
        val seed = BigInteger(messageId, 16).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
        val messageRandom = Random(seed)
        val shuffled = validatorSet.shuffled(messageRandom)
        val k = configuration.treeChildrenCount
        val index = shuffled.indexOf(localNode.publicKey)

        if (isTrustedNode) {
            // Logger.debug(TreeUtils.outputTree(k, shuffled.map { sha256(it).asHex.substring(50..60) }))
        }

        val broadcastNodes = mutableSetOf<String>()
        when {
            configuration.useTreeBasedMessageRoutingProtocol && index != -1 -> {
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
            else -> broadcastNodes.addAll(pickRandomNodes().map { it.publicKey })
        }

        val knownAndNotInSet = knownNodes.values.map(Node::publicKey).filter { !validatorSet.activeValidators.contains(it) }
        broadcastNodes.addAll(knownAndNotInSet)

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
            if (alreadySeen(packetId) || alreadySeen(messageId)) return@tryAndReport

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
                messageBuilders.remove(messageId)
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

    private class QueuedMessage(val message: Message, val recipients: Array<out String>)
}