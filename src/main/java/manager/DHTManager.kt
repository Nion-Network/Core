package manager

import data.*
import logging.Logger

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 15:33
 * using IntelliJ IDEA
 */
class DHTManager(private val networkManager: NetworkManager) {

    infix fun searchFor(forPublicKey: String) {
        networkManager.apply {
            if (knownNodes.containsKey(forPublicKey)) return
            val message = generateMessage(QueryMessage(networkManager.ourNode, forPublicKey))
            broadcast(EndPoint.Query, message)
        }
    }

    /**
     * When we get a http request on /found, this method triggers...
     *
     * @param context Http request context
     */
    fun onFound(message: Message<FoundMessage>) {
        val body = message.body
        val newNode = Node(body.forPublicKey, body.foundIp, body.foundPort)
        networkManager.knownNodes.computeIfAbsent(newNode.publicKey) { newNode }
    }

    /**
     * On query request checks if we have the node cached. If we do, we send back FoundMessageBody...
     *
     * @param context HTTP Context
     */
    fun onQuery(message: Message<QueryMessage>) {
        val body = message.body
        val lookingFor: String = body.searchingPublicKey
        Logger.info("Received DHT query for ${lookingFor.subSequence(30, 50)}")
        val comingFrom = body.node
        networkManager.apply {
            knownNodes.computeIfAbsent(comingFrom.publicKey) { comingFrom }
            knownNodes[lookingFor]?.apply {
                val foundMessage = generateMessage(FoundMessage(ip, port, publicKey))
                sendMessage(body.node, EndPoint.Found, foundMessage)
            } ?: broadcast(EndPoint.Query, message)
        }
    }

    /**
     * On join request, check if we can store the new node joining. If we can't, we send it's message to 5 random neighbours...
     *
     * @param context HTTP Context
     */
    fun joinRequest(message: Message<Node>) {
        networkManager.apply {
            val node = message.body
            broadcast(EndPoint.OnJoin, message)

            Logger.info("Received join request!")
            if (!isFull && !knownNodes.containsKey(node.publicKey)) node.apply {
                val nodesToShare = knownNodes.values
                val joinedMessage = JoinedMessage(ourNode, nodesToShare)
                knownNodes.computeIfAbsent(publicKey) { this }
                sendMessage(this, EndPoint.OnJoin, generateMessage(joinedMessage))
            }
        }

    }

    /**
     * After we've been accepted into the network, the node that has accepted us sends confirmation to this endpoint.
     *
     * @param context
     */
    fun onJoin(message: Message<JoinedMessage>) {
        networkManager.apply {
            val confirmed: Boolean = crypto.verify(message.bodyAsString, message.signature, message.publicKey)
            if (confirmed) {
                val joinedMessage = message.body
                val acceptor: Node = joinedMessage.acceptor
                val acceptorKey = acceptor.publicKey

                knownNodes.computeIfAbsent(acceptorKey) { acceptor }
                networkManager.isInNetwork = true
                Logger.debug("We've been accepted into network by ${acceptor.ip}")

                joinedMessage.knownNodes.forEach { newNode ->
                    knownNodes.computeIfAbsent(newNode.publicKey) { newNode }
                    Logger.debug("Added ${newNode.publicKey.substring(30..50)}")
                }
            }
        }
    }
}

