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
import kotlin.random.Random

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
        println(Dashboard.myInfo)
    }
    val isTrustedNode = localNode.let { node -> node.ip == configuration.trustedNodeIP && node.kademliaPort == configuration.trustedNodePort }

    private val knownNodes = ConcurrentHashMap<String, Node>()

    val totalKnownNodes get() = knownNodes.size
    val isBootstrapped get() = totalKnownNodes > 1
    private val tree = ConcurrentHashMap<Int, Bucket>()
    private val outgoingQueue = LinkedBlockingQueue<KademliaQueueMessage>()
    private val incomingQueue = LinkedBlockingQueue<KademliaMessage>()
    private val queryStorage = ConcurrentHashMap<String, KademliaQuery>()
    private val bucketSize = 20
    private val testLock = ReentrantLock(true)

    init {
        Logger.debug("Our identifier is: ${localNode.identifier}")
        Thread(::sendOutgoing).start()
        Thread(::receiveIncoming).start()
        Thread(::processIncoming).start()
        lookForInactiveQueries()
        if (isTrustedNode) add(localNode)
        printTree()
    }

    /** Sends a FIND_NODE request of our key to the known bootstrapping [Node]. */
    fun bootstrap(ip: String, port: Int, block: ((Node) -> Unit)? = null) {
        Logger.info("Bootstrapping Kademlia!")
        sendFindRequest(localNode.identifier, listOf(Node(ip, port, port, port, "BOOTSTRAP")), block)
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
            // Logger.error("We went around.")
            return bucket
        }
        return bucket.plus(lookup(closestPosition, missing, startedIn))
    }

    /** Adds the node to the appropriate bucket, if there is enough space. */
    private fun add(node: Node) {
        if (knownNodes.containsKey(node.identifier)) return
        knownNodes[node.identifier] = node
        val bits = node.bitSet.apply { xor(localNode.bitSet) }
        val position = bits.nextSetBit(0).takeIf { it >= 0 } ?: bits.size()
        val bucket = tree.computeIfAbsent(position) { Bucket(bucketSize) }
        bucket.add(node)
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
            when (kademliaMessage.endpoint) {
                KademliaEndpoint.PING -> TODO()
                KademliaEndpoint.FIND_NODE -> {
                    val lookingFor = ProtoBuf.decodeFromByteArray<String>(kademliaMessage.data)
                    val distance = getDistance(lookingFor)
                    val closestNodes = lookup(distance)
                    val reply = ClosestNodes(lookingFor, closestNodes.toTypedArray())
                    val encodedReply = ProtoBuf.encodeToByteArray(reply)
                    Logger.info(
                        "Closest I could find [${kademliaMessage.sender.ip}:${kademliaMessage.sender.kademliaPort}] for ${lookingFor.take(5)} was ${
                            closestNodes.joinToString(",") {
                                it.identifier.take(
                                    5
                                )
                            }
                        }"
                    )
                    addToQueue(kademliaMessage.sender, KademliaEndpoint.CLOSEST_NODES, encodedReply)
                    add(kademliaMessage.sender)
                }
                KademliaEndpoint.CLOSEST_NODES -> {
                    val closestNodes = ProtoBuf.decodeFromByteArray<ClosestNodes>(kademliaMessage.data)
                    val receivedNodes = closestNodes.nodes
                    val queryHolders = receivedNodes.mapNotNull { queryStorage[it.identifier] }
                    val identifier = closestNodes.identifier
                    val identifierQueryHolder = queryStorage[identifier]
                    val searchedNode = receivedNodes.firstOrNull { it.identifier == identifier } ?: knownNodes[identifier]
                    receivedNodes.forEach { add(it) }
                    Logger.trace("Received back ${closestNodes.nodes.size} nodes. Covers ${queryHolders.size} queries. Found ${identifier.take(5)}️ ${if (searchedNode == null) "💔" else "💚"}")
                    if (searchedNode == null && identifierQueryHolder != null) {
                        identifierQueryHolder.hops++
                        receivedNodes.shuffle()
                        Logger.info("Received back: ${receivedNodes.joinToString(", ") { it.identifier.take(5) }}")
                        sendFindRequest(identifier, receivedNodes.take(3))
                    }
                    queryHolders.forEach { queryHolder ->
                        queryHolder.hops++
                        val node = knownNodes[queryHolder.identifier] ?: return@forEach
                        val actionsToDo = mutableListOf<(Node) -> Unit>()
                        val drained = queryHolder.queue.drainTo(actionsToDo)
                        // Logger.trace("Drained $drained actions.")
                        launchCoroutine {
                            actionsToDo.forEach { it.invoke(node) }
                        }
                        Dashboard.reportDHTQuery(identifier, localNode.identifier, queryHolder.hops, queryHolder.let { System.currentTimeMillis() - it.start })
                        queryStorage.remove(queryHolder.identifier)
                    }
                }
            }
        }
    }

    /** Sends outgoing [kademlia messages][KademliaMessage] when available (from [outgoingQueue]).*/
    private fun sendOutgoing() {
        val dataBuffer = ByteBuffer.allocate(60_000)
        val packet = DatagramPacket(dataBuffer.array(), dataBuffer.position())
        while (true) tryAndReport {
            val outgoing = outgoingQueue.take()
            dataBuffer.apply {
                clear()
                putInt(outgoing.data.size)
                put(outgoing.data)
                packet.socketAddress = InetSocketAddress(outgoing.ip, outgoing.port)
                packet.length = dataBuffer.position()
                kademliaSocket.send(packet)
                Thread.sleep(Random.nextLong(5, 10))
                // Logger.trace("Kademlia sent a packet [${outgoing.endpoint}] to ${outgoing.ip}:${outgoing.port}")
            }
        }
    }

    /** Sends a FIND_NODE request to the [recipient] or a random closest node (relative to the [identifier]). */
    private fun sendFindRequest(identifier: String, recipients: List<Node> = mutableListOf(), block: ((Node) -> Unit)? = null) {
        val distance = getDistance(identifier)
        val possibleRecipients = recipients.ifEmpty { lookup(distance) }.filter { it != localNode }.shuffled().take(3)
        val encodedRequest = ProtoBuf.encodeToByteArray(identifier)
        val query = queryStorage.computeIfAbsent(identifier) { KademliaQuery(identifier) }
        query.lastUpdate = System.currentTimeMillis()
        if (block != null) query.queue.put(block)
        possibleRecipients.forEach { addToQueue(it, KademliaEndpoint.FIND_NODE, encodedRequest) }
    }

    /** Encodes [KademliaMessage] and puts it into the [outgoingQueue]. */
    private fun addToQueue(receiver: Node, endpoint: KademliaEndpoint, data: ByteArray) {
        val outgoingMessage = KademliaMessage(localNode, endpoint, data)
        val encodedOutgoing = ProtoBuf.encodeToByteArray(outgoingMessage)
        val queueMessage = KademliaQueueMessage(endpoint, receiver.ip, receiver.kademliaPort, encodedOutgoing)
        // Logger.trace("Kademlia added to queue [$endpoint] ==> ${receiver.ip}:${receiver.kademliaPort}.")
        outgoingQueue.put(queueMessage)
    }

    private fun lookForInactiveQueries() {
        val inactiveQueries = queryStorage.filterValues { System.currentTimeMillis() - it.lastUpdate > 1000 }
        inactiveQueries.forEach { (identifier, _) -> sendFindRequest(identifier) }
        Logger.info("Reviving ${inactiveQueries.size} inactive queries.")
        runAfter(5000, this::lookForInactiveQueries)
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