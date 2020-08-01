package network

import Main
import abstraction.ProtocolTasks
import common.BlockChain
import common.BlockData
import configuration.Configuration
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import logging.Logger
import protocols.BlockPropagation
import protocols.Consensus
import protocols.DHT
import utils.Crypto
import utils.Utils
import utils.networkHistory

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
    private val blockPropagation: BlockPropagation = BlockPropagation(nodeNetwork, crypto, blockChain, configuration)
    private val consensus: Consensus = Consensus(nodeNetwork, crypto, blockChain)

    init {
        blockChain.networkManager = this
        application.exception(Exception::class.java) { exception, context ->
            Logger.error("Stumbled upon error on request from ${context.ip()}")
            exception.printStackTrace()
        }

        application.before {
            val hex = it.header("hex")
            if (networkHistory.containsKey(hex)) {
                Logger.error("We've already seen this message [${it.path()}]... We're ignoring it!")
                throw ForbiddenResponse("We've already seen this message yo...")
            }
        }

        Logger.trace("My IP is ${nodeNetwork.myIP}")

        "/ping" get { status(200) }
        "/join" post { dhtProtocol.joinRequest(this) }
        "/joined" post { dhtProtocol.onJoin(this) }
        "/query" post { dhtProtocol.onQuery(this) }
        "/found" post { dhtProtocol.onFound(this) }


        "/chain" get { this.result(Main.gson.toJson(blockChain.chain)) } //for browser debugging
        "/search" get { dhtProtocol.sendSearchQuery(this.queryParam("pub_key").toString()); }
        "/newBlock" post { blockPropagation.receivedNewBlock(this) }
        "/syncBlockchainRequest" post { blockPropagation.receivedSyncRequest(this) } //we were asked for our blocks
        "/syncBlockchainReply" post { blockPropagation.processBlocks(this) } //we received a reply to our request for blocks
        "/include" post { consensus.validatorSetInclusionRequest(this) }
        "/vdf" post { consensus.receivedVdf(this) }

        // Join request to trusted Node after setup
        // Check for IP (or port difference for local testing)...
        if (nodeNetwork.myIP != configuration.trustedNodeIP || configuration.listeningPort != configuration.trustedNodePort) {
            Logger.trace("Sending join request to our trusted node...")
            Utils.sendMessageTo(configuration.trustedHttpAddress, "/join", nodeNetwork.createIdentificationMessage()).apply {
                Logger.trace("Join response from trusted node: $this")
            }

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
    private infix fun String.get(block: Context.() -> Unit): Javalin = application.get(this, block)
    private infix fun String.post(block: Context.() -> Unit): Javalin = application.post(this, block)

    //entry points for protocols
    fun initiate(protocol: ProtocolTasks, payload: Any) {
        Logger.info("Initiating protocol task $protocol")
        when (protocol) {
            ProtocolTasks.newBlock -> blockPropagation.broadcast(payload as BlockData)
            ProtocolTasks.requestBlocks -> blockPropagation.requestBlocks(payload as Int)
            ProtocolTasks.requestInclusion -> consensus.requestInclusion(payload as String)
        }
    }
}