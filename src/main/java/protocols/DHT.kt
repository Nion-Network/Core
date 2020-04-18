package protocols

import abstraction.Message
import abstraction.Node
import abstraction.asNode
import io.javalin.http.Context
import network.NodeNetwork
import utils.Crypto
import utils.bodyAsMessage

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 15:33
 * using IntelliJ IDEA
 */
class DHT(private val nodeNetwork: NodeNetwork) {

    fun joinRequest(context: Context) {
        val message: Message = context.bodyAsMessage
        val originalBody: String = message.body

        if (!nodeNetwork.isFull) {
            val myMessage = nodeNetwork.createMessage("")
            val node: Node = originalBody.asNode
            nodeNetwork.nodeMap[node.publicKey] = node
            node.sendMessage("/joined", myMessage)
        } else nodeNetwork.pickRandomNodes(5).forEach { it.sendMessage("/join", message) }
    }

    fun onJoin(context: Context) {
        val message = context.bodyAsMessage
        val confirmed = Crypto.verify(message.body, message.signature, message.publicKey)
        if (confirmed) nodeNetwork.isInNetwork = true
    }
}