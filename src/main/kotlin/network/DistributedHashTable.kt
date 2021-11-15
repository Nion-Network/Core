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
        val requestingNode = message.decodeAs<Node>()
        val welcomeMessage = WelcomeMessage(localNode, knownNodes.values)
        addNewNodes(requestingNode)
        send(Endpoint.Welcome, TransmissionType.Unicast, welcomeMessage, requestingNode.publicKey)
    }

    @MessageEndpoint(Endpoint.NodeQuery)
    fun onQuery(message: Message) {
        val query = message.decodeAs<QueryMessage>()
        Logger.info("Coming from onQuery with $query!")
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