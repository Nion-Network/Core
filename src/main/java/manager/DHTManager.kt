package manager

import communication.TransmissionType
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
            sendUDP(Endpoint.NodeQuery, message, TransmissionType.Unicast)
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
                sendUDP(Endpoint.NodeFound, foundMessage, TransmissionType.Unicast, body.node)
            } ?: sendUDP(Endpoint.NodeQuery, message, TransmissionType.Unicast)
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
            Logger.debug("Received join request from ${Logger.cyan}${node.ip}${Logger.reset}")
            if (!isFull) node.apply {
                val nodesToShare = knownNodes.values.toTypedArray()
                val joinedMessage = JoinedMessage(ourNode, nodesToShare)
                knownNodes.computeIfAbsent(publicKey) { this }
                sendUDP(Endpoint.Welcome, generateMessage(joinedMessage), TransmissionType.Unicast, this)
                Logger.debug("Sent successful join back to ${node.ip}")
            } else sendUDP(Endpoint.JoinRequest, message, TransmissionType.Unicast)
        }

    }

    /**
     * After we've been accepted into the network, the node that has accepted us sends confirmation to this endPoint.
     *
     * @param context
     */
    fun onJoin(message: Message<JoinedMessage>) {
        networkManager.apply {
            val confirmed: Boolean = crypto.verify(message.body.toString(), message.signature, message.publicKey)
            if (confirmed) {
                val joinedMessage = message.body
                val acceptor: Node = joinedMessage.acceptor
                val acceptorKey = acceptor.publicKey

                knownNodes.computeIfAbsent(acceptorKey) { acceptor }
                networkManager.isInNetwork = true
                Logger.debug("We've been accepted into network by ${acceptor.ip}")

                joinedMessage.knownNodes.forEach { newNode ->
                    knownNodes.computeIfAbsent(newNode.publicKey) {
                        sendUDP(Endpoint.JoinRequest, generateMessage(ourNode), TransmissionType.Unicast, newNode)
                        newNode
                    }
                    Logger.debug("Added ${newNode.publicKey.substring(30..50)}")
                }
                // networkManager.broadcast(EndPoint.Join, generateMessage(ourNode), false)
            }
        }
    }
}

