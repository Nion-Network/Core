package network

import communication.*
import data.*
import data.communication.JoinedMessage
import data.communication.Message
import data.communication.QueryMessage
import data.communication.TransmissionType
import data.network.Endpoint
import data.network.Node
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import utils.coroutineAndReport
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 15:33
 * using IntelliJ IDEA
 */
class DistributedHashTable(private val network: Network) {

    private val queue = ConcurrentHashMap<String, (Node) -> Unit>()

    /** On found node for the [publicKey], if a lambda block for the current node exists, it is executed. */
    private fun executeOnFound(publicKey: String) {
        network.knownNodes[publicKey]?.let { node ->
            queue.remove(publicKey)?.invoke(node)
        }
    }

    /** Send out a search for [public key][forPublicKey] and add a callback block to be executed after the node is found. */
    fun searchFor(forPublicKey: String, onFound: ((Node) -> Unit)? = null) {
        if (onFound != null) queue[forPublicKey] = onFound
        if (network.knownNodes.containsKey(forPublicKey)) {
            coroutineAndReport { executeOnFound(forPublicKey) }
            return
        }
        network.send(Endpoint.NodeQuery, TransmissionType.Broadcast, QueryMessage(network.ourNode, forPublicKey))
    }

    /** When the node is found, the data is sent to this endpoint. The node is added to our [known nodes][Network.knownNodes]. */
    fun onFound(message: Message<Node>) {
        val node = message.body
        network.knownNodes.computeIfAbsent(node.publicKey) { node }
        executeOnFound(node.publicKey)
    }

    /** When a query endpoint receives a message we check if we know who the public key belongs to.. If we do, we send back the known [Node]...*/
    fun onQuery(message: Message<QueryMessage>) {
        val body = message.body
        val lookingFor: String = body.searchingPublicKey
        Logger.info("Received DHT query for ${lookingFor.subSequence(30, 50)}")
        val comingFrom = body.seekingNode
        network.apply {
            knownNodes.computeIfAbsent(comingFrom.publicKey) { comingFrom }
            val searchedNode = knownNodes[lookingFor]
            if (searchedNode != null) send(Endpoint.NodeFound, TransmissionType.Unicast, searchedNode, body.seekingNode)
            // else send(Endpoint.NodeQuery, TransmissionType.Unicast, body)
        }
    }

    /** On join request, check if we can store the new node joining. If we can't, we send its message to some random neighbours...*/
    fun joinRequest(message: Message<Node>) {
        network.apply {
            val node = message.body
            Logger.debug("Received join request from ${Logger.cyan}${node.ip}${Logger.reset}")
            if (!isFull) node.apply {
                val toTake = configuration.broadcastSpreadPercentage * knownNodes.values.size / 100
                val nodesToShare = knownNodes.values.take(toTake).toTypedArray()
                val joinedMessage = JoinedMessage(ourNode, nodesToShare)
                knownNodes.computeIfAbsent(publicKey) { this }
                send(Endpoint.Welcome, TransmissionType.Unicast, joinedMessage, this)
            }
        }

    }

    /**
     * After we've been accepted into the network, the node that has accepted us sends confirmation to this endPoint.
     *
     * @param context
     */
    fun onJoin(message: Message<JoinedMessage>) {
        network.apply {
            val encoded = ProtoBuf.encodeToByteArray(message.body)
            val confirmed: Boolean = crypto.verify(encoded, message.signature, message.publicKey)
            if (confirmed) {
                val joinedMessage = message.body
                val acceptor = joinedMessage.acceptor
                val acceptorKey = acceptor.publicKey

                knownNodes.computeIfAbsent(acceptorKey) { acceptor }
                network.isInNetwork = true
                val newNodes = joinedMessage.knownNodes.size
                Logger.debug("We've been accepted into network by ${acceptor.ip} with $newNodes nodes.")
                joinedMessage.knownNodes.forEach { newNode -> knownNodes.computeIfAbsent(newNode.publicKey) { newNode } }
            } else {
                Logger.error("Verification failed.")
                Dashboard.reportException(Exception("Verification failed!"))
            }
        }
    }
}

