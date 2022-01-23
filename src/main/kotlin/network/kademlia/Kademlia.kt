package network.kademlia

import Configuration
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import network.SocketHolder
import network.data.Node
import utils.*
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.DatagramPacket
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
open class Kademlia(configuration: Configuration) : SocketHolder(configuration) {

    val crypto = Crypto(".")
    val localAddress = InetAddress.getLocalHost()
    val localNode = Node(localAddress.hostAddress, udpSocket.localPort, tcpSocket.localPort, kademliaSocket.localPort, crypto.publicKey).apply {
        Dashboard.myInfo = "$ip:$kademliaPort"
    }
    private val knownNodes = ConcurrentHashMap<String, Node>()

    val totalKnownNodes get() = knownNodes.size
    val isBootstrapped get() = totalKnownNodes > 1
    private val tree = ConcurrentHashMap<Int, Bucket>()
    private val outgoingQueue = LinkedBlockingQueue<QueueMessage>()
    private val incomingQueue = LinkedBlockingQueue<KademliaMessage>()
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
        sendFindRequest(localNode.identifier, Node(ip, port, port, port, "BOOTSTRAP"), block)
    }

    /** Performs the query for the [publicKey] and executes the callback passed. If known, immediately else when found. */
    fun query(publicKey: String, action: ((Node) -> Unit)? = null) {
        val identifier = sha256(publicKey).asHex
        val knownNode = knownNodes[identifier]
        Logger.info("Querying for ${identifier.take(5)}: ${knownNode?.ip}")
        if (knownNode == null) sendFindRequest(identifier, block = action)
        else if (action != null) launchCoroutine { action(knownNode) }
    }

    /** Retrieves [amount] of the closest nodes. */
    fun getRandomNodes(amount: Int): List<Node> {
        return knownNodes.values.shuffled().take(amount)
    }

    /** Looks into buckets and retrieves at least [bucketSize] closest nodes. */
    private fun lookup(position: Int, needed: Int = bucketSize, startedIn: Int = position): Set<Node> {
        val bucket = tree[position]?.getNodes() ?: emptySet()
        val missing = needed - bucket.size
        if (missing <= 0) return bucket
        val closestPosition = tree.keys.filter { it < position }.maxOrNull() ?: 256
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
            kademliaSocket.receive(packet)
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
                    Logger.info("Closest I could find for ${lookingFor.take(5)} was ${closestNodes.joinToString(",") { it.identifier.take(5) }}")
                    addToQueue(kademliaMessage.sender, KademliaEndpoint.CLOSEST_NODES, encodedReply)
                }
                KademliaEndpoint.CLOSEST_NODES -> {
                    val closestNodes = ProtoBuf.decodeFromByteArray<ClosestNodes>(kademliaMessage.data)
                    val lookingFor = closestNodes.lookingFor
                    closestNodes.nodes.apply { // TODO: clean this
                        val node = firstOrNull { it.identifier == lookingFor }
                        forEach { add(it) }
                        Logger.debug("Adding $size nodes!")
                        val query = queryStorage[lookingFor]
                        if (node == null && !knownNodes.contains(lookingFor)) {
                            query?.apply { hops++ }
                            shuffle()
                            take(3).forEach { sendFindRequest(lookingFor, it) }
                        } else {
                            val actions = mutableListOf<(Node) -> Unit>()
                            query?.queue?.drainTo(actions)
                            Logger.trace("Drained $query into  ${actions.size} actions.")
                            if (query != null) {
                                val duration = System.currentTimeMillis() - query.start
                                Logger.trace("Kademlia took ${duration}ms and ${query.hops} hops to find ${node.identifier}")
                                Dashboard.reportDHTQuery(node.identifier, query.hops, duration)
                            }
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
                kademliaSocket.send(packet)
                Logger.trace("Kademlia sent a packet to ${outgoing.ip}:${outgoing.port}")
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
        }.filter { it.identifier != localNode.identifier }.randomOrNull().apply {
            if (this == null) Dashboard.reportException(Exception("Bootstrapped: $isBootstrapped"))
        } ?: return
        val encodedRequest = ProtoBuf.encodeToByteArray(identifier)
        val query = queryStorage.computeIfAbsent(identifier) { KademliaQuery(hops = 0) }
        if (block != null) query.queue.put(block)
        addToQueue(sendTo, KademliaEndpoint.FIND_NODE, encodedRequest)
        Logger.trace("Kademlia sent a FIND_NODE for ${identifier.take(5)} with $block (size: ${query.queue.size}).")
    }

    /** Encodes [KademliaMessage] and puts it into the [outgoingQueue]. */
    private fun addToQueue(receiver: Node, endpoint: KademliaEndpoint, data: ByteArray) {
        val outgoingMessage = KademliaMessage(localNode, endpoint, data)
        val encodedOutgoing = ProtoBuf.encodeToByteArray(outgoingMessage)
        val queueMessage = QueueMessage(receiver.ip, receiver.kademliaPort, encodedOutgoing)
        Logger.trace("Kademlia added to queue [$endpoint].")
        outgoingQueue.put(queueMessage)
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