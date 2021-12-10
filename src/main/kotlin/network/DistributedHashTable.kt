package network

import data.Configuration
import data.communication.Message
import data.communication.TransmissionType
import data.communication.WelcomeMessage
import data.network.Endpoint
import data.network.Node
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
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
        val welcomeMessage = WelcomeMessage(localNode, emptyList())
        send(Endpoint.Welcome, TransmissionType.Unicast, welcomeMessage, requestingNode.publicKey)
    }

    inline fun <reified T> send(endpoint: Endpoint, transmissionType: TransmissionType, data: T, amount: Int) {
        val nodes = pickRandomNodes(amount)
        send(endpoint, transmissionType, data, *nodes.map { it.publicKey }.toTypedArray())
    }

    inline fun <reified T> send(endpoint: Endpoint, transmissionType: TransmissionType, data: T, vararg publicKeys: String) {
        val encodedBody = ProtoBuf.encodeToByteArray(data)
        val signature = crypto.sign(encodedBody)
        val message = Message(endpoint, crypto.publicKey, encodedBody, signature)
        val encodedMessage = ProtoBuf.encodeToByteArray(message)
        send(endpoint, transmissionType, message, encodedMessage, *publicKeys)
    }

}