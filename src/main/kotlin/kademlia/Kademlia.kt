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

    private val tree = ConcurrentHashMap<Int, Bucket>()
    private val outputQueue = LinkedBlockingQueue<QueueMessage>()
    private val incomingQueue = LinkedBlockingQueue<KademliaMessage>()
    private val datagramSocket = DatagramSocket(port)
    private val queryTimes = ConcurrentHashMap<String, QueryDuration>()
    private val bucketSize = 50

    init {
        Logger.debug("Our identifier is: ${localNode.identifier}")
        Thread(this::clearOutgoing).start()
        Thread(this::receiveIncoming).start()
        Thread(this::processIncoming).start()
        add(localNode)

        // testLookup()
        printTree()
    }

    private fun add(node: Node) {
        val bits = node.bitSet.apply { xor(localNode.bitSet) }
        val position = bits.nextSetBit(0).takeIf { it >= 0 } ?: bits.size()
        val bucket = tree.computeIfAbsent(position) { Bucket(bucketSize) }
        bucket.add(node)
    }

    private fun lookup(position: Int, needed: Int = bucketSize, startedIn: Int = position): Set<Node> {
        Logger.info("Looking into Bucket[$position]")
        val bucket = tree[position]?.getNodes() ?: emptySet()
        val missing = needed - bucket.size
        if (missing <= 0) return bucket
        val closestPosition = if (position == 0) 256
        else tree.keys.filter { it < position }.maxOrNull() ?: return bucket
        if (closestPosition == startedIn) {
            Logger.error("We went around.")
            return bucket
        }
        return bucket.plus(lookup(closestPosition, missing, startedIn))
    }

    fun query(identifier: String, action: ((Node) -> Unit)? = null) {
        val distance = getDistance(identifier)
        val isKnown = lookup(distance).any { it.identifier == identifier }
        /*if (!isKnown)*/ sendFindRequest(identifier, block = action)
    }

    fun getDistance(identifier: String): Int {
        val bits = identifier.asBitSet.apply { xor(localNode.bitSet) }
        return bits.nextSetBit(0).takeIf { it >= 0 } ?: bits.size()
    }

    private fun receiveIncoming() {
        val pureArray = ByteArray(60_000)
        val inputStream = DataInputStream(ByteArrayInputStream(pureArray))
        val packet = DatagramPacket(pureArray, 60_000)
        while (true) tryAndReport {
            inputStream.reset()
            datagramSocket.receive(packet)
            val dataLength = inputStream.readInt()
            val data = inputStream.readNBytes(dataLength)
            val kademliaMessage = ProtoBuf.decodeFromByteArray<KademliaMessage>(data)
            incomingQueue.put(kademliaMessage)
            Logger.trace("Added to Kademlia incoming queue: ${kademliaMessage.endpoint}.")
        }
    }

    private fun processIncoming() {
        while (true) tryAndReport {
            val kademliaMessage = incomingQueue.take()
            val start = System.currentTimeMillis()
            Logger.trace("Processing ${kademliaMessage.endpoint}.")
            add(kademliaMessage.sender)
            when (kademliaMessage.endpoint) {
                KademliaEndpoint.PING -> TODO()
                KademliaEndpoint.FIND_NODE -> {
                    Logger.trace("Current duration: ${System.currentTimeMillis() - start}ms")
                    val findMessage = ProtoBuf.decodeFromByteArray<FindRequest>(kademliaMessage.data)
                    val distance = getDistance(findMessage.lookingFor)
                    val closestNodes = lookup(distance)
                    val reply = ClosestNodes(findMessage.lookingFor, closestNodes.toTypedArray())
                    val encodedReply = ProtoBuf.encodeToByteArray(reply)
                    addToQueue(kademliaMessage.sender, KademliaEndpoint.CLOSEST_NODES, encodedReply)
                    Logger.info("Closest I could find for ${findMessage.lookingFor.take(5)} was ${closestNodes.joinToString(",") { it.identifier.take(5) }}")
                    Logger.trace("Current duration: ${System.currentTimeMillis() - start}ms")
                }
                KademliaEndpoint.CLOSEST_NODES -> {
                    Logger.trace("Current duration: ${System.currentTimeMillis() - start}ms")
                    val closestNodes = ProtoBuf.decodeFromByteArray<ClosestNodes>(kademliaMessage.data)
                    val lookingFor = closestNodes.lookingFor
                    closestNodes.nodes.apply { // TODO: clean this
                        val node = firstOrNull { it.identifier == lookingFor }
                        val isFound = node != null
                        forEach { add(it) }
                        Logger.debug("Adding $size nodes!")
                        val query = (if (node == null) queryTimes[lookingFor] else queryTimes.remove(lookingFor)) ?: return@apply
                        query.hops++
                        if (!isFound) {
                            shuffle()
                            take(3).forEach { sendFindRequest(lookingFor, it) }
                        } else {
                            query.action?.invoke(node!!)
                            val duration = System.currentTimeMillis() - query.start
                            Logger.trace("Kademlia took ${duration}ms and ${query.hops} hops to find ${node?.identifier}")
                            Dashboard.reportDHTQuery(lookingFor, query.hops, duration)
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
            }
        }
    }

    private fun sendFindRequest(identifier: String, recipient: Node? = null, block: ((Node) -> Unit)? = null) {
        if (recipient == localNode) return
        val distance = getDistance(identifier)
        val sendTo = recipient ?: lookup(distance).apply { Logger.info("Lookup size: " + this.size) }.filter { it.identifier != localNode.identifier }.random()
        val findRequest = FindRequest(identifier)
        val encodedRequest = ProtoBuf.encodeToByteArray(findRequest)
        queryTimes.computeIfAbsent(identifier) { QueryDuration(hops = 0, action = block) }
        addToQueue(sendTo, KademliaEndpoint.FIND_NODE, encodedRequest)
        Logger.trace("Kademlia sent a FIND_NODE for ${identifier.take(5)}.")
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
        tree.forEach { (index, bucket) ->
            string.append("\tbucket[$index] [label='${bucket.getNodes().joinToString(",") { it.identifier.take(5) }}']\n")
        }
        Logger.info("\n\n$string")
        Logger.info("Total nodes known: ${tree.values.sumOf { it.size }}")
    }

    data class QueryDuration(val start: Long = System.currentTimeMillis(), var hops: Int, val action: ((Node) -> Unit)? = null)

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

    private fun testLookup() {
        (0..10).map {
            add(Node("$it", it, "$it"))
        }
        val distance = getDistance(sha256("8").asHex)
        println(lookup(distance).joinToString(",") { it.identifier.take(5) })
    }
}

val String.asBitSet get() = BitSet.valueOf(toBigInteger(16).toByteArray().reversedArray())