package network

import abstraction.Message
import abstraction.Node
import common.BlockChain
import configuration.Configuration
import io.javalin.Javalin
import io.javalin.http.Context
import logging.Logger
import protocols.BlockPropagation
import protocols.DHT
import utils.Crypto
import utils.Utils
import javax.servlet.http.Part

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:58
 * using IntelliJ IDEA
 */
class NetworkManager(configuration: Configuration, crypto: Crypto, blockChain: BlockChain) {

    private val nodeNetwork = NodeNetwork(configuration, crypto)
    private val application = Javalin.create { it.showJavalinBanner = false }.start(configuration.listeningPort)


    // Protocols
    private val dhtProtocol: DHT = DHT(nodeNetwork, crypto)
    private val  blockPropagation: BlockPropagation = BlockPropagation(nodeNetwork,crypto,blockChain,configuration)

    init {
        Logger.trace("My IP is ${nodeNetwork.myIP}")

        "/ping" get { status(200) }
        "/join" post { dhtProtocol.joinRequest(this) }
        "/query" post { dhtProtocol.onQuery(this) }
        "/found" post { dhtProtocol.onFound(this) }
        "/joined" post { dhtProtocol.onJoin(this) }
        "/chain" get{ this.result(Main.gson.toJson(blockChain)) }
        "/search" get { dhtProtocol.sendSearchQuery(this.queryParam("pub_key").toString()); }
        "/newBlock" post { blockPropagation.receivedNewBlock(this)}

        // Join request to trusted Node after setup
        // Check for IP (or port difference for local testing)...
        if (nodeNetwork.myIP != configuration.trustedNodeIP || configuration.listeningPort != configuration.trustedNodePort) {
            val joinMessage: Message = nodeNetwork.createMessage(Node(crypto.publicKey, nodeNetwork.myIP, configuration.listeningPort))
            Logger.trace("Sending join request to our trusted node...")

            val joinResponse = Utils.sendMessageTo(configuration.trustedHttpAddress, "/join", joinMessage)
            Logger.trace("Join response from trusted node: $joinResponse")

            while (!nodeNetwork.isInNetwork) {
                Logger.trace("Waiting to be accepted into the network...")
                Thread.sleep(1000)
            }

            Logger.debug("We're in the network. Happy networking!")
        } else Logger.debug("We're the trusted node! Very important...")

        Logger.debug("Listening on port: " + configuration.listeningPort)
    }

    /**
     * Set javalin application's context to response to the string (path) with the context block.
     *
     * @param block the application will use when the GET path is visited.
     */
    infix fun String.get(block: Context.() -> Unit) = application.get(this, block)
    infix fun String.post(block: Context.() -> Unit) = application.post(this, block)
}