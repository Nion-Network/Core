package manager

import data.FoundMessage
import data.Message
import data.Node
import data.QueryMessage
import logging.Logger

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 15:33
 * using IntelliJ IDEA
 */
class DHTManager(private val networkManager: NetworkManager) {

    private val knownNodes = networkManager.knownNodes
    private val crypto = networkManager.crypto

    fun sendSearchQuery(forPublicKey: String) {
        if (knownNodes.containsKey(forPublicKey)) return
        val message = networkManager.generateMessage(QueryMessage(networkManager.ourNode, forPublicKey))
        networkManager.broadcast("/query", message)
    }

    /**
     * When we get a http request on /found, this method triggers...
     *
     * @param context Http request context
     */
    fun onFound(message: Message<FoundMessage>) {
        val body = message.body
        val newNode = Node(body.forPublicKey, body.foundIp, body.foundPort)
        knownNodes[newNode.publicKey] = newNode
    }

    /**
     * On query request checks if we have the node cached. If we do, we send back FoundMessageBody...
     *
     * @param context HTTP Context
     */
    fun onQuery(message: Message<QueryMessage>) {
        //println("Received query request for ${context.body()}")
        val body = message.body
        val lookingFor: String = body.searchingPublicKey

        knownNodes[lookingFor]?.apply {
            val foundMessage = networkManager.generateMessage(FoundMessage(ip, port, publicKey))
            body.node.sendMessage("/found", foundMessage)
        } ?: networkManager.broadcast("/query", message)
    }

    /**
     * On join request, check if we can store the new node joining. If we can't, we send it's message to 5 random neighbours...
     *
     * @param context HTTP Context
     */
    fun joinRequest(message: Message<Node>) {
        val node = message.body

        if (!networkManager.isFull) node.apply {
            knownNodes[publicKey] = this
            sendMessage("/joined", networkManager.generateMessage(networkManager.ourNode))
        } else networkManager.broadcast("/join", message)
    }

    /**
     * After we've been accepted into the network, the node that has accepted us sends confirmation to this endpoint.
     *
     * @param context
     */
    fun onJoin(message: Message<Node>) {
        val confirmed: Boolean = crypto.verify(message.bodyAsString, message.signature, message.publicKey)
        if (confirmed) {
            val acceptorNode: Node = message.body
            knownNodes[acceptorNode.publicKey] = acceptorNode
            networkManager.isInNetwork = true
            Logger.debug("We've been accepted into network by ${acceptorNode.ip}")
        }
    }
}