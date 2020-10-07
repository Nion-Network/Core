package protocols

import abstraction.Message
import abstraction.Node
import io.javalin.http.Context
import logging.Logger
import manager.ApplicationManager
import messages.FoundMessage
import messages.IdentificationMessage
import messages.QueryMessageBody
import network.knownNodes
import utils.getMessage

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 15:33
 * using IntelliJ IDEA
 */
class DHT(private val applicationManager: ApplicationManager) {

    private val networkManager by lazy { applicationManager.networkManager }
    private val nodeNetwork by lazy { networkManager.nodeNetwork }

    fun sendSearchQuery(forPublicKey: String) {
        if (knownNodes.containsKey(forPublicKey)) return
        Logger.info("Broadcasting on /query looking for our key owner...")
        nodeNetwork.broadcast("/query", nodeNetwork.createQueryMessage(forPublicKey))
    }

    /**
     * When we get a http request on /found, this method triggers...
     *
     * @param context Http request context
     */
    fun onFound(context: Context) {
        val message: Message<FoundMessage> = context.getMessage()
        val body = message.body
        val newNode = Node(body.forPublicKey, body.foundIp, body.foundPort)
        knownNodes[newNode.publicKey] = newNode
        Logger.info("We got the IP for public key!")
    }

    /**
     * On query request checks if we have the node cached. If we do, we send back FoundMessageBody...
     *
     * @param context HTTP Context
     */
    fun onQuery(context: Context) {
        //println("Received query request for ${context.body()}")
        val message = context.getMessage<QueryMessageBody>()
        val body = message.body
        val lookingFor: String = body.searchingPublicKey

        knownNodes[lookingFor]?.apply {
            val foundMessage = nodeNetwork.createGenericsMessage(FoundMessage(ip, port, publicKey))
            body.node.sendMessage("/found", foundMessage)
        } ?: nodeNetwork.pickRandomNodes(5).forEach { it.sendMessage("/query", message) }
    }

    /**
     * On join request, check if we can store the new node joining. If we can't, we send it's message to 5 random neighbours...
     *
     * @param context HTTP Context
     */
    fun joinRequest(context: Context) {
        val ip = context.ip()
        Logger.debug("Join request coming in from $ip ...")
        val message = context.getMessage<IdentificationMessage>()
        val body = message.body

        if (!nodeNetwork.isFull) body.node.apply {
            Logger.debug("Node [$ip] has been accepted into the network...")
            knownNodes[publicKey] = this
            sendMessage("/joined", nodeNetwork.createIdentificationMessage())
        } else nodeNetwork.broadcast("/join", message)
        context.status(200)
    }

    fun onJoin(context: Context) {
        val message: Message<IdentificationMessage> = context.getMessage()
        val confirmed: Boolean = applicationManager.crypto.verify(message.bodyAsString, message.signature, message.publicKey)
        if (confirmed) {
            val acceptorNode: Node = message.body.node
            Logger.debug("We've been accepted into network by ${acceptorNode.ip}")
            knownNodes[acceptorNode.publicKey] = acceptorNode
            nodeNetwork.isInNetwork = true
        }
        Logger.debug("We were accepted into the network!")
    }
}