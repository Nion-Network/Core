package kademlia

import data.network.Node
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256
import utils.tryAndReport
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by mihael
 * on 01/12/2021 at 13:10
 * using IntelliJ IDEA
 */
class Kademlia(private val localNode: Node, port: Int) {

    private val tree = ConcurrentHashMap<Int, MutableSet<Node>>()
    private val outputQueue = LinkedBlockingQueue<QueueMessage>()
    private val datagramSocket = DatagramSocket(port)
    private val queryTimes = mutableMapOf<String, QueryDuration>()
    private val maxNodes = 5

    init {
        Logger.debug("Our identifier is: ${localNode.identifier}")
        Logger.debug(localNode)
        Thread(::clearOutgoing).start()
        Thread(::receiveIncoming).start()
        val x = sha256("1").asHex
        val y = sha256("2").asHex
        val z = sha256("100").asHex
        add(localNode)
        (2 until 10).forEach {
            // add(Node("$it", 69, "$it"))
        }
        printTree()
        println(lookup(z).joinToString(","))
    }

    private fun add(node: Node) {
        val bits = node.bitSet.apply { xor(localNode.bitSet) }
        val position = bits.nextSetBit(0).takeIf { it >= 0 } ?: bits.size()
        val set = tree.computeIfAbsent(position) { mutableSetOf() }
        val isFull = set.size >= maxNodes
        val containsAlready = set.contains(node)
        if (!isFull || containsAlready) {
            set.remove(node)
            set.add(node)
        }
    }

    private fun lookup(identifier: String, needed: Int = maxNodes, index: Int = -1): Set<Node> {
        val bits = identifier.asBitSet.apply { xor(localNode.bitSet) }
        val position = if (index >= 0) index else bits.nextSetBit(0).takeIf { it >= 0 } ?: bits.size()
        val set = tree[position] ?: emptySet()
        val missing = needed - set.size
        if (missing <= 0) return set
        val closestPosition = tree.keys.minByOrNull { it - position } ?: return set
        return set.plus(lookup(identifier, missing, closestPosition))
    }

    fun query(identifier: String) {
        val isKnown = lookup(identifier).any { it.identifier == identifier }
        sendFindRequest(identifier)
        // if (!isKnown) sendFindRequest(identifier)
        // else Logger.debug("Kademlia already knows $identifier.")
    }

    private fun receiveIncoming() {
        val pureArray = ByteArray(60_000)
        val inputStream = DataInputStream(ByteArrayInputStream(pureArray))
        val packet = DatagramPacket(pureArray, 60_000)
        while (true) tryAndReport {
            inputStream.reset()
            datagramSocket.receive(packet)
            val start = System.currentTimeMillis()
            val dataLength = inputStream.readInt()
            val data = inputStream.readNBytes(dataLength)
            val kademliaMessage = ProtoBuf.decodeFromByteArray<KademliaMessage>(data)
            Logger.error("Received Kademlia packet on ${kademliaMessage.endpoint}.")
            when (kademliaMessage.endpoint) {
                KademliaEndpoint.PING -> TODO()
                KademliaEndpoint.FIND_NODE -> {
                    Logger.trace("Current duration: ${System.currentTimeMillis() - start}ms")
                    val findMessage = ProtoBuf.decodeFromByteArray<FindRequest>(kademliaMessage.data)
                    val closestNodes = lookup(findMessage.lookingFor)
                    val reply = ClosestNodes(findMessage.lookingFor, closestNodes.toTypedArray())
                    val encodedReply = ProtoBuf.encodeToByteArray(reply)
                    add(kademliaMessage.sender)
                    Logger.info("Closest I could find for ${findMessage.lookingFor.take(5)} was ${closestNodes.joinToString(",") { it.identifier.take(5) }}")
                    addToQueue(kademliaMessage.sender, KademliaEndpoint.CLOSEST_NODES, encodedReply)
                    Logger.trace("Current duration: ${System.currentTimeMillis() - start}ms")
                }
                KademliaEndpoint.CLOSEST_NODES -> {
                    Logger.trace("Current duration: ${System.currentTimeMillis() - start}ms")
                    val closestNodes = ProtoBuf.decodeFromByteArray<ClosestNodes>(kademliaMessage.data)
                    closestNodes.nodes.apply {
                        val found = firstOrNull { it.identifier == closestNodes.lookingFor }
                        val queryDuration = queryTimes[closestNodes.lookingFor]
                        queryDuration?.apply {
                            hops++
                        }
                        forEach { add(it) }
                        if (found == null) toList().shuffled().take(3).forEach { sendFindRequest(closestNodes.lookingFor, it) }
                        else queryTimes.remove(closestNodes.lookingFor)?.apply {
                            block?.invoke(this, found)
                        }
                    }
                    Logger.trace("Current duration: ${System.currentTimeMillis() - start}ms")
                }
            }
            Logger.trace("Took me ${System.currentTimeMillis() - start}ms to process the message.")
            printTree()
        }
    }

    private fun clearOutgoing() {
        val dataBuffer = ByteBuffer.allocate(60_000)
        while (true) tryAndReport {
            val outgoing = outputQueue.take()
            dataBuffer.apply {
                clear()
                putInt(outgoing.data.size)
                put(outgoing.data)
                val packet = DatagramPacket(dataBuffer.array(), dataBuffer.position(), InetSocketAddress(outgoing.ip, outgoing.port))
                datagramSocket.send(packet)
                Logger.trace("Sent out a packet.")
            }
        }
    }

    private fun sendFindRequest(identifier: String, recipient: Node? = null) {
        if (recipient == localNode) return
        val sendTo = recipient ?: lookup(identifier).filter { it.identifier != localNode.identifier }.random()
        Logger.trace("Sent a FIND_NODE request for $identifier to ${sendTo.ip}.")
        val findRequest = FindRequest(identifier)
        val encodedRequest = ProtoBuf.encodeToByteArray(findRequest)
        queryTimes.computeIfAbsent(identifier) {
            val start = System.currentTimeMillis()
            QueryDuration(start, 0) { query, node ->
                val currentTime = System.currentTimeMillis()
                val duration = currentTime - query.start
                Logger.trace("Query complete for ${node.identifier} with ${query.hops} hops and duration of ${duration}ms")
                Dashboard.reportDHTQuery(node.identifier, query.hops, duration)
            }
        }
        Logger.trace("Added to outgoing queue to find node.")
        addToQueue(sendTo, KademliaEndpoint.FIND_NODE, encodedRequest)
    }

    private fun addToQueue(receiver: Node, endpoint: KademliaEndpoint, data: ByteArray) {
        val outgoingMessage = KademliaMessage(localNode, endpoint, data)
        val encodedOutgoing = ProtoBuf.encodeToByteArray(outgoingMessage)
        val queueMessage = QueueMessage(receiver.ip, receiver.port + 2, encodedOutgoing)
        outputQueue.add(queueMessage)
    }

    fun bootstrap(ip: String, port: Int) {
        Logger.info("Bootstrapping Kademlia!")
        sendFindRequest(localNode.identifier, Node(ip, port, "BOOTSTRAP"))
    }

    fun printTree() {
        val string = StringBuilder()
        tree.toMap().forEach { (index, nodes) ->
            string.append("\tbucket[$index] [label='${nodes.joinToString(",") { it.identifier.take(5) }}']\n")
        }
        Logger.info("\n\n$string")
        Logger.info("Total nodes known: ${tree.values.sumOf { it.size }}")
    }

    data class QueryDuration(val start: Long, var hops: Int, val block: ((QueryDuration, Node) -> Unit)? = null)

    enum class KademliaEndpoint {
        PING,
        CLOSEST_NODES,
        FIND_NODE;

        companion object {
            private val cache = values().associateBy { it.ordinal }
            operator fun get(id: Int) = cache[id]
        }
    }

    class QueueMessage(val ip: String, val port: Int, val data: ByteArray)

    @Serializable
    class KademliaMessage(val sender: Node, val endpoint: KademliaEndpoint, val data: ByteArray)

    @Serializable
    data class FindRequest(val lookingFor: String)

    @Serializable
    class ClosestNodes(val lookingFor: String, val nodes: Array<Node>)

}

val String.asBitSet get() = BitSet.valueOf(toBigInteger(16).toByteArray().reversedArray())
