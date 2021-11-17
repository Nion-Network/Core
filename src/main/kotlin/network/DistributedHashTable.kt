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

/**
 * Created by Mihael Valentin Berčič
 * on 02/11/2021 at 18:39
 * using IntelliJ IDEA
 */
abstract class DistributedHashTable(configuration: Configuration) : Server(configuration) {

    fun queryFor(vararg publicKeys: String) {
        val unknown = publicKeys.filter { !knownNodes.containsKey(it) }
        if (unknown.isNotEmpty()) {
            val queryMessage = QueryMessage(localNode, publicKeys)
            send(Endpoint.NodeQuery, TransmissionType.Unicast, queryMessage)
        }
    }

    @MessageEndpoint(Endpoint.JoinRequest)
    fun joinRequestReceived(message: Message) {
        Logger.info("Working on join request...")
        val requestingNode = message.decodeAs<Node>()
        val welcomeMessage = WelcomeMessage(localNode, knownNodes.values.toList())
        addNewNodes(requestingNode)
        send(Endpoint.Welcome, TransmissionType.Unicast, welcomeMessage, requestingNode.publicKey)
        Logger.info("Sending back welcome to ${requestingNode.ip}")
    }

    @MessageEndpoint(Endpoint.NodeQuery)
    fun onQuery(message: Message) {
        val query = message.decodeAs<QueryMessage>()
        val lookingFor = query.publicKeys.mapNotNull { knownNodes[it] }
        val seekingNode = query.seeker
        addNewNodes(seekingNode)
        send(Endpoint.QueryReply, TransmissionType.Unicast, lookingFor, seekingNode.publicKey)
    }

    @MessageEndpoint(Endpoint.QueryReply)
    fun onQueryResponse(message: Message) {
        val foundNodes = message.decodeAs<Array<Node>>()
        addNewNodes(*foundNodes)
    }

    fun addNewNodes(vararg nodes: Node) {
        val mapped = nodes.associateBy { it.publicKey }
        knownNodes.putAll(mapped)
    }

    inline fun <reified T> send(endpoint: Endpoint, transmissionType: TransmissionType, data: T, vararg publicKeys: String) {
        if (publicKeys.isNotEmpty()) queryFor(*publicKeys)
        val encodedBody = ProtoBuf.encodeToByteArray(data)
        val signature = crypto.sign(encodedBody)
        val message = Message(endpoint, crypto.publicKey, encodedBody, signature)
        val encodedMessage = ProtoBuf.encodeToByteArray(message)
        send(endpoint, transmissionType, message, encodedMessage, *publicKeys)
    }

}