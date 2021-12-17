package kademlia

import data.Configuration
import data.network.Node
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import utils.Crypto
import utils.Utils.Companion.asBitSet
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256
import utils.launchCoroutine
import utils.tryAndReport
import utils.tryWithLock
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock

/**
 * Created by mihael
 * on 01/12/2021 at 13:10
 * using IntelliJ IDEA
 */
open class Kademlia(configuration: Configuration) {

    val crypto = Crypto(".")
    val localAddress = InetAddress.getLocalHost()
    val localNode = Node(localAddress.hostAddress, configuration.port, crypto.publicKey)
    private val knownNodes = ConcurrentHashMap<String, Node>()

    val totalKnownNodes get() = knownNodes.size
    val isBootstrapped get() = totalKnownNodes > 1
    private val tree = ConcurrentHashMap<Int, Bucket>()
    private val outgoingQueue = LinkedBlockingQueue<QueueMessage>()
    private val incomingQueue = LinkedBlockingQueue<KademliaMessage>()
    private val datagramSocket = DatagramSocket(configuration.port + 2)
    private val queryStorage = ConcurrentHashMap<String, KademliaQuery>()
    private val bucketSize = 5
    private val testLock = ReentrantLock(true)

    init {
        Logger.debug("Our identifier is: ${localNode.identifier}")
        Thread(::sendOutgoing).start()
        Thread(::receiveIncoming).start()
        Thread(::processIncoming).start()
        add(localNode)
        printTree()
    }

    /** Sends a FIND_NODE request of our key to the known bootstrapping [Node]. */
    fun bootstrap(ip: String, port: Int, block: ((Node) -> Unit)? = null) {
        Logger.info("Bootstrapping Kademlia!")
        sendFindRequest(localNode.identifier, Node(ip, port, "BOOTSTRAP"), block)
    }

    /** Performs the query for the [publicKey] and executes the callback passed. If known, immediately else when found. */
    fun query(publicKey: String, action: ((Node) -> Unit)? = null) {
        testLock.tryWithLock {
            val identifier = sha256(publicKey).asHex
            val knownNode = knownNodes[identifier]
            if (knownNode == null) sendFindRequest(identifier, block = action)
            else if (action != null) executeOnFound(action, knownNode)
        }
    }

    /** Retrieves [amount] of the closest nodes. */
    fun getRandomNodes(amount: Int): List<Node> {
        return knownNodes.values.shuffled().take(amount)
    }

    /** Looks into buckets and retrieves at least [bucketSize] closest nodes. */
    private fun lookup(position: Int, needed: Int = bucketSize, startedIn: Int = position): Set<Node> {
        Logger.trace("Missing: $needed.")
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

    /** Adds the node to the appropriate bucket, if there is enough space. */
    private fun add(node: Node) {
        val bits = node.bitSet.apply { xor(localNode.bitSet) }
        val position = bits.nextSetBit(0).takeIf { it >= 0 } ?: bits.size()
        val bucket = tree.computeIfAbsent(position) { Bucket(bucketSize) }
        bucket.add(node)
        knownNodes.computeIfAbsent(node.identifier) { node }
    }

    /** Calculates the XOR distance between our [localNode] and [identifier]. */
    private fun getDistance(identifier: String): Int {
        val bits = identifier.asBitSet.apply { xor(localNode.bitSet) }
        return bits.nextSetBit(0).takeIf { it >= 0 } ?: bits.size()
    }

    /** Reads from [datagramSocket] and puts messages into the [incoming messages queue][incomingQueue]. */
    private fun receiveIncoming() {
        val pureArray = ByteArray(60_000)
        val inputStream = DataInputStream(ByteArrayInputStream(pureArray))
        val packet = DatagramPacket(pureArray, pureArray.size)
        while (true) tryAndReport {
            inputStream.reset()
            datagramSocket.receive(packet)
            val dataLength = inputStream.readInt()
            val data = inputStream.readNBytes(dataLength)
            val kademliaMessage = ProtoBuf.decodeFromByteArray<KademliaMessage>(data)
            incomingQueue.put(kademliaMessage)
        }
    }

    /** Takes one queued [KademliaMessage] from [incomingQueue] when available and processes it. */
    private fun processIncoming() {
        while (true) tryAndReport {
            val kademliaMessage = incomingQueue.take()
            add(kademliaMessage.sender)
            when (kademliaMessage.endpoint) {
                KademliaEndpoint.PING -> TODO()
                KademliaEndpoint.FIND_NODE -> {
                    val lookingFor = ProtoBuf.decodeFromByteArray<String>(kademliaMessage.data)
                    val distance = getDistance(lookingFor)
                    val closestNodes = lookup(distance)
                    val reply = ClosestNodes(lookingFor, closestNodes.toTypedArray())
                    val encodedReply = ProtoBuf.encodeToByteArray(reply)
                    addToQueue(kademliaMessage.sender, KademliaEndpoint.CLOSEST_NODES, encodedReply)
                    Logger.info("Closest I could find for ${lookingFor.take(5)} was ${closestNodes.joinToString(",") { it.identifier.take(5) }}")
                }
                KademliaEndpoint.CLOSEST_NODES -> {
                    val closestNodes = ProtoBuf.decodeFromByteArray<ClosestNodes>(kademliaMessage.data)
                    val lookingFor = closestNodes.lookingFor
                    closestNodes.nodes.apply { // TODO: clean this
                        val node = firstOrNull { it.identifier == lookingFor }
                        val isFound = node != null
                        forEach { add(it) }
                        Logger.debug("Adding $size nodes!")
                        val query = (if (node == null) queryStorage[lookingFor] else queryStorage.remove(lookingFor)) ?: return@apply
                        query.hops++
                        if (!isFound) {
                            shuffle()
                            take(3).forEach { sendFindRequest(lookingFor, it) }
                        } else if (node != null) {
                            query.action?.apply { executeOnFound(this, node) }
                            val duration = System.currentTimeMillis() - query.start
                            Logger.trace("Kademlia took ${duration}ms and ${query.hops} hops to find ${node.identifier}")
                            Dashboard.reportDHTQuery(node.identifier, query.hops, duration)
                        }
                    }
                }
            }
        }
    }

    /** Sends outgoing [kademlia messages][KademliaMessage] when available (from [outgoingQueue]).*/
    private fun sendOutgoing() {
        val dataBuffer = ByteBuffer.allocate(60_000)
        while (true) tryAndReport {
            val outgoing = outgoingQueue.take()
            dataBuffer.apply {
                clear()
                putInt(outgoing.data.size)
                put(outgoing.data)
                val packet = DatagramPacket(dataBuffer.array(), dataBuffer.position(), InetSocketAddress(outgoing.ip, outgoing.port))
                datagramSocket.send(packet)
            }
        }
    }

    /** Sends a FIND_NODE request to the [recipient] or a random closest node (relative to the [identifier]). */
    private fun sendFindRequest(identifier: String, recipient: Node? = null, block: ((Node) -> Unit)? = null) {
        if (recipient == localNode) return
        val distance = getDistance(identifier)
        val sendTo = recipient ?: lookup(distance).apply {
            if (isEmpty()) {
                Logger.error("LOOKUP SIZE for ${identifier.take(5)} IS 0 SOMEHOW!")
                printTree()
            }
        }.filter { it.identifier != localNode.identifier }.random()
        val encodedRequest = ProtoBuf.encodeToByteArray(identifier)
        queryStorage.computeIfAbsent(identifier) { KademliaQuery(hops = 0, action = block) }
        addToQueue(sendTo, KademliaEndpoint.FIND_NODE, encodedRequest)
        Logger.trace("Kademlia sent a FIND_NODE for ${identifier.take(5)}.")
    }

    /** Encodes [KademliaMessage] and puts it into the [outgoingQueue]. */
    private fun addToQueue(receiver: Node, endpoint: KademliaEndpoint, data: ByteArray) {
        val outgoingMessage = KademliaMessage(localNode, endpoint, data)
        val encodedOutgoing = ProtoBuf.encodeToByteArray(outgoingMessage)
        val queueMessage = QueueMessage(receiver.ip, receiver.port + 2, encodedOutgoing)
        outgoingQueue.put(queueMessage)
    }

    /**Launches the queued action from [queryStorage]. */
    private fun executeOnFound(action: ((Node) -> Unit), node: Node) {
        launchCoroutine {
            action(node)
        }
    }

    /** Debugs the built kademlia tree [development purposes only]. */
    fun printTree() {
        val string = StringBuilder()
        tree.forEach { (index, bucket) ->
            string.append("\tbucket[$index] [label='${bucket.getNodes().joinToString(",") { it.identifier.take(5) }}']\n")
        }
        Logger.info("\n\n$string")
        Logger.info("Total nodes known: ${tree.values.sumOf { it.size }} vs $totalKnownNodes")
    }
}