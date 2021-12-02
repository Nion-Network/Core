package kademlia

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import utils.tryAndReport
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by mihael
 * on 01/12/2021 at 13:10
 * using IntelliJ IDEA
 */
class Kademlia(identifier: String, ip: String, port: Int) {

    val localNode = KademliaNode(identifier, ip, port)
    val tree = KademliaTreeNode(0)
    private val outputQueue = LinkedBlockingQueue<QueueMessage>()
    private val datagramSocket = DatagramSocket(port)
    private val queryTimes = mutableMapOf<String, QueryDuration>()

    init {
        Logger.debug("Our identifier is: ${localNode.identifier}")
        tree.add(localNode)
        Thread(::clearOutgoing).start()
        Thread(::receiveIncoming).start()
    }

    data class QueryDuration(val start: Long, var hops: Int)

    fun lookup(identifier: String) {
        tryAndReport {
            val closestNodes = tree.find(identifier.asBitSet)
            val alreadyKnown = closestNodes.firstOrNull { it.identifier == identifier }
            if (alreadyKnown == null) sendFindRequest(identifier)
        }
    }

    private fun receiveIncoming() {
        val pureArray = ByteArray(60_000)
        val inputStream = DataInputStream(ByteArrayInputStream(pureArray))
        val packet = DatagramPacket(pureArray, 60_000)
        while (true) tryAndReport {
            inputStream.reset()
            datagramSocket.receive(packet)
            Logger.trace("Received Kademlia packet!")
            val start = System.currentTimeMillis()
            val dataLength = inputStream.readInt()
            val data = inputStream.readNBytes(dataLength)
            val kademliaMessage = ProtoBuf.decodeFromByteArray<KademliaMessage>(data)
            when (kademliaMessage.endpoint) {
                KademliaEndpoint.PING -> TODO()
                KademliaEndpoint.FIND_NODE -> {
                    val message = ProtoBuf.decodeFromByteArray<FindRequest>(kademliaMessage.data)
                    val lookingFor = BitSet.valueOf(message.lookingFor.toBigInteger(16).toByteArray())
                    val closestNodes = tree.find(lookingFor)
                    val foundTheOneLookingFor = closestNodes.any { it.identifier == message.lookingFor }
                    val response = ClosestNodes(message.lookingFor, closestNodes.toTypedArray())
                    val encodedResponse = ProtoBuf.encodeToByteArray(response)
                    Logger.debug("Sending back to ${kademliaMessage.sender.ip} with correct node: $foundTheOneLookingFor")
                    addToQueue(kademliaMessage.sender, KademliaEndpoint.CLOSEST_NODES, encodedResponse)
                }
                KademliaEndpoint.CLOSEST_NODES -> {
                    val message = ProtoBuf.decodeFromByteArray<ClosestNodes>(kademliaMessage.data)
                    val lookingFor = message.lookingFor
                    val closestNodes = message.nodes
                    val foundTheOne = closestNodes.any { it.identifier == lookingFor }
                    val query = queryTimes[message.lookingFor] ?: throw Exception("We were not looking for this ID.")
                    closestNodes.forEach { tree.add(it) }
                    if (!foundTheOne && closestNodes.isNotEmpty()) {
                        val nearestNode = closestNodes.filter { it.identifier != localNode.identifier }.random()
                        Logger.info("Sending more FIND_NODE to ${nearestNode.ip}.")
                        query.hops++
                        sendFindRequest(message.lookingFor, nearestNode)
                    } else if (foundTheOne) {
                        val currentTime = System.currentTimeMillis()
                        val duration = currentTime - query.start
                        Logger.trace("Query complete for $lookingFor with ${query.hops} hops and duration of ${duration}ms")
                        Dashboard.reportDHTQuery(query.hops, duration)
                    }
                }
            }
            tree.add(kademliaMessage.sender)
            Logger.trace("Took me ${System.currentTimeMillis() - start}ms to process the message.")
            Logger.info("\n$tree\n")
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

    private fun sendFindRequest(identifier: String, recipient: KademliaNode? = null) {
        val sendTo = recipient ?: tree.find(identifier.asBitSet).filter { it.identifier != localNode.identifier }.random()
        Logger.trace("Sending out a FIND_NODE request for $identifier.")
        val findRequest = FindRequest(identifier)
        val encodedRequest = ProtoBuf.encodeToByteArray(findRequest)
        queryTimes.computeIfAbsent(identifier) { QueryDuration(System.currentTimeMillis(), 0) }
        addToQueue(sendTo, KademliaEndpoint.FIND_NODE, encodedRequest)
    }

    private fun addToQueue(receiver: KademliaNode, endpoint: KademliaEndpoint, data: ByteArray) {
        val outgoingMessage = KademliaMessage(localNode, endpoint, data)
        val encodedOutgoing = ProtoBuf.encodeToByteArray(outgoingMessage)
        val queueMessage = QueueMessage(receiver.ip, receiver.port, encodedOutgoing)
        outputQueue.add(queueMessage)
    }

    fun bootstrap(ip: String, port: Int) {
        Logger.info("Bootstrapping Kademlia!")
        sendFindRequest(localNode.identifier, KademliaNode("BOOTSTRAP", ip, port))
    }

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
    class KademliaMessage(val sender: KademliaNode, val endpoint: KademliaEndpoint, val data: ByteArray)

    @Serializable
    data class FindRequest(val lookingFor: String)

    @Serializable
    class ClosestNodes(val lookingFor: String, val nodes: Array<KademliaNode>)

}

val String.asBitSet get() = BitSet.valueOf(toBigInteger(16).toByteArray())
