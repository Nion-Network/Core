package protocols

import abstraction.Message
import abstraction.Node
import io.javalin.http.Context
import logging.Logger
import messages.FoundMessageBody
import messages.QueryMessageBody
import messages.WelcomeMessageBody
import network.NodeNetwork
import utils.Crypto
import utils.Utils
import utils.bodyAsMessage
import utils.fromJsonTo

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 15:33
 * using IntelliJ IDEA
 */
class DHT(private val nodeNetwork: NodeNetwork, private val crypto: Crypto) {


    fun sendSearchQuery(forPublicKey: String){
        Logger.info("Broadcasting on /query looking for our key owner...")
        nodeNetwork.broadcast("/query", nodeNetwork.createQueryMessage(forPublicKey))
    }

    /**
     * When we get a http request on /found, this method triggers...
     *
     * @param context Http request context
     */
    fun onFound(context: Context) {
        val message: Message = context.bodyAsMessage
        val foundMessageBody: FoundMessageBody = message.body fromJsonTo FoundMessageBody::class.java
        Logger.info("We got back what we were looking for!\n$foundMessageBody")
    }

    /**
     * On query request checks if we have the node cached. If we do, we send back FoundMessageBody...
     *
     * @param context HTTP Context
     */
    fun onQuery(context: Context) {
        //println("Received query request for ${context.body()}")
        val message: Message = context.bodyAsMessage
        val queryMessageBody: QueryMessageBody = message.body fromJsonTo QueryMessageBody::class.java
        val lookingFor: String = queryMessageBody.searchingPublicKey

        nodeNetwork.nodeMap[lookingFor]?.apply {
            val returnAddress: String = queryMessageBody.returnToHttpAddress
            val foundMessage: Message = nodeNetwork.createMessage(FoundMessageBody(ip, port, publicKey))
            Utils.sendMessageTo(returnAddress, "/found", foundMessage)
        } ?: nodeNetwork.pickRandomNodes(5).forEach { it.sendMessage("/query", message) }
    }

    /**
     * On join request, check if we can store the new node joining. If we can't, we send it's message to 5 random neighbours...
     *
     * @param context HTTP Context
     */
    fun joinRequest(context: Context) {
        try {
            val message: Message = context.bodyAsMessage
            val originalBody: String = message.body
            if (!nodeNetwork.isFull) {
                val myMessage: Message = nodeNetwork.createWelcomeMessage()
                val node: Node = originalBody fromJsonTo Node::class.java
                nodeNetwork.nodeMap[node.publicKey] = node
                node.sendMessage("/joined", myMessage)
            } else nodeNetwork.pickRandomNodes(5).forEach { it.sendMessage("/join", message) }
            context.status(200)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onJoin(context: Context) {
        val message = context.bodyAsMessage
        val confirmed = crypto.verify(message.body, message.signature, message.publicKey)
        if (confirmed) {
            val welcomeMessage: WelcomeMessageBody = message.body fromJsonTo WelcomeMessageBody::class.java
            val acceptorNode: Node = welcomeMessage.acceptorNode
            nodeNetwork.nodeMap[acceptorNode.publicKey] = acceptorNode
            nodeNetwork.isInNetwork = true
        }
        Logger.debug("Message received on path /joined")
    }
}