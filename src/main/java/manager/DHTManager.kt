package manager

import data.Message
import data.Node
import io.javalin.http.Context
import logging.Logger
import data.FoundMessage
import data.QueryMessage
import network.knownNodes
import utils.getMessage

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 15:33
 * using IntelliJ IDEA
 */
class DHTManager(private val applicationManager: ApplicationManager) {

    private val networkManager by lazy { applicationManager.networkManager }
    private val nodeNetwork by lazy { networkManager.nodeNetwork }

    fun sendSearchQuery(forPublicKey: String) {
        if (knownNodes.containsKey(forPublicKey)) return
        Logger.info("Broadcasting on /query looking for our key owner...")
        nodeNetwork.broadcast("/query", applicationManager.createQueryMessage(forPublicKey))
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
        val message = context.getMessage<QueryMessage>()
        val body = message.body
        val lookingFor: String = body.searchingPublicKey

        knownNodes[lookingFor]?.apply {
            val foundMessage = applicationManager.generateMessage(FoundMessage(ip, port, publicKey))
            body.node.sendMessage("/found", foundMessage)
        } ?: nodeNetwork.sendMessageToRandomNodes("/query", 5, message)
    }

    /**
     * On join request, check if we can store the new node joining. If we can't, we send it's message to 5 random neighbours...
     *
     * @param context HTTP Context
     */
    fun joinRequest(context: Context) {
        val ip = context.ip()
        Logger.debug("Join request coming in from $ip ...")
        val message = context.getMessage<Node>()
        val node = message.body

        if (!nodeNetwork.isFull) node.apply {
            knownNodes[publicKey] = this
            sendMessage("/joined", applicationManager.identificationMessage)
        } else nodeNetwork.broadcast("/join", message)
        context.status(200)
    }

    /**
     * After we've been accepted into the network, the node that has accepted us sends confirmation to this endpoint.
     *
     * @param context
     */
    fun onJoin(context: Context) {
        val message: Message<Node> = context.getMessage()
        val confirmed: Boolean = applicationManager.crypto.verify(message.bodyAsString, message.signature, message.publicKey)
        if (confirmed) {
            val acceptorNode: Node = message.body
            knownNodes[acceptorNode.publicKey] = acceptorNode
            nodeNetwork.isInNetwork = true
            Logger.debug("We've been accepted into network by ${acceptorNode.ip}")
        }
    }
}