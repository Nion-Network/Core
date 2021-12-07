package network

import data.Configuration
import data.communication.Message
import data.communication.QueryMessage
import data.communication.TransmissionType
import data.communication.WelcomeMessage
import data.network.Endpoint
import data.network.Node
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Logger
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256
import utils.launchCoroutine
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Mihael Valentin Berčič
 * on 02/11/2021 at 18:39
 * using IntelliJ IDEA
 */
abstract class DistributedHashTable(configuration: Configuration) : Server(configuration) {

    private val queuedActions = ConcurrentHashMap<String, (Node) -> Unit>()

    @MessageEndpoint(Endpoint.JoinRequest)
    fun joinRequestReceived(message: Message) {
        val requestingNode = message.decodeAs<Node>()
        val welcomeMessage = WelcomeMessage(localNode, knownNodes.values.toList())
        addNewNodes(requestingNode)
        send(Endpoint.Welcome, TransmissionType.Unicast, welcomeMessage, requestingNode.publicKey)
    }

    @MessageEndpoint(Endpoint.NodeQuery)
    fun onQuery(message: Message) {
        val query = message.decodeAs<QueryMessage>()
        val lookingFor = query.publicKeys.mapNotNull { knownNodes[it] }.associateBy { it.publicKey }
        val missing = query.publicKeys.filter { !lookingFor.containsKey(it) }
        val allAnswered = missing.isEmpty()
        val seekingNode = query.seeker
        addNewNodes(seekingNode)
        if (lookingFor.isNotEmpty()) send(Endpoint.QueryReply, TransmissionType.Unicast, lookingFor.values.toList(), seekingNode.publicKey)
        if (!allAnswered) pickRandomNodes(1).apply {
            send(Endpoint.NodeQuery, TransmissionType.Unicast, query.copy(publicKeys = missing), *map { it.publicKey }.toTypedArray())
        }

    }

    @MessageEndpoint(Endpoint.QueryReply)
    fun onQueryReply(message: Message) {
        val foundNodes = message.decodeAs<Array<Node>>()
        addNewNodes(*foundNodes)
        invokeQueuedAction(*foundNodes)
    }

    fun addNewNodes(vararg nodes: Node) {
        val mapped = nodes.associateBy { it.publicKey }
        knownNodes.putAll(mapped)
        checkForQueuedMessages(nodes)
    }

    fun queryFor(vararg publicKeys: String, onFoundBlock: ((Node) -> Unit)? = null) {
        val unknown = publicKeys.filter { !knownNodes.containsKey(it) }
        if (true || unknown.isNotEmpty()) {
            val queryMessage = QueryMessage(localNode, publicKeys.toList())
            send(Endpoint.NodeQuery, TransmissionType.Unicast, queryMessage)
            publicKeys.forEach {
                kademlia.query(sha256(it).asHex)
            }
        } else Logger.debug("Already know this node of ${publicKeys.joinToString(",") { sha256(it).asHex }}")
        if (onFoundBlock != null && publicKeys.isNotEmpty()) {
            val known = publicKeys.mapNotNull { knownNodes[it] }
            queuedActions.putAll(publicKeys.map { it to onFoundBlock })
            known.forEach { node ->
                launchCoroutine { onFoundBlock(node) }
            }
        }
    }

    private fun invokeQueuedAction(vararg nodes: Node) {
        nodes.forEach { node ->
            queuedActions.remove(node.publicKey)?.invoke(node)
        }
    }

    inline fun <reified T> send(endpoint: Endpoint, transmissionType: TransmissionType, data: T, vararg publicKeys: String) {
        val encodedBody = ProtoBuf.encodeToByteArray(data)
        val signature = crypto.sign(encodedBody)
        val message = Message(endpoint, crypto.publicKey, encodedBody, signature)
        val encodedMessage = ProtoBuf.encodeToByteArray(message)
        send(endpoint, transmissionType, message, encodedMessage, *publicKeys)
        if (publicKeys.isNotEmpty()) queryFor(*publicKeys)
    }

}