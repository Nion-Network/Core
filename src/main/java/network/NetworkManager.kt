package network

import io.javalin.Javalin
import io.javalin.http.Context
import logging.Logger
import manager.ApplicationManager
import utils.Utils
import utils.networkHistory

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:58
 * using IntelliJ IDEA
 */
class NetworkManager(applicationManager: ApplicationManager) { // , blockChain: BlockChain

    // BlockChain     blockChain     = new BlockChain(crypto, vdf, configuration);

    val nodeNetwork = NodeNetwork(applicationManager)
    private val validatorManager = applicationManager.validatorManager
    private val configuration = applicationManager.configuration
    private val dhtProtocol = applicationManager.dhtProtocol

    private val application = Javalin.create { it.showJavalinBanner = false }.start(configuration.listeningPort)

    init {
        // blockChain.networkManager = this
        application.exception(Exception::class.java) { exception, context ->
            Logger.error("Stumbled upon error on request from ${context.ip()}")
            exception.printStackTrace()
        }

        application.before {
            val hex = it.header("hex")
            if (networkHistory.containsKey(hex)) {
                Logger.error("We've already seen this message [${it.path()}]... We're ignoring it!")
            }
        }
        "/ping" get { status(200) }

        // DHT protocol
        "/query" post { dhtProtocol.onQuery(this) }
        "/found" post { dhtProtocol.onFound(this) }
        "/joined" post { dhtProtocol.onJoin(this) }
        "/join" post { dhtProtocol.joinRequest(this) }
        "/search" get { this.queryParam("pub_key")?.apply { dhtProtocol.sendSearchQuery(this) } }


        //
        "/include" post { validatorManager.validatorSetInclusionRequest(this) }


        /*
        "/vdf" post { consensus.receivedVdf(this) }
        "/newBlock" post { blockPropagation.receivedNewBlock(this) }
        "/syncBlockchainReply" post { blockPropagation.processBlocks(this) } //we received a reply to our request for blocks
        "/syncBlockchainRequest" post { blockPropagation.receivedSyncRequest(this) } //we were asked for our blocks


         */

        if (!applicationManager.isTrustedNode) {
            Logger.trace("Sending join request to our trusted node...")
            Utils.sendMessageTo(configuration.trustedHttpAddress, "/join", nodeNetwork.createIdentificationMessage()).apply {
                Logger.trace("Join response from trusted node: $this")
            }

            while (!nodeNetwork.isInNetwork) {
                Logger.trace("Waiting to be accepted into the network...")
                Thread.sleep(1000)
            }

            Logger.debug("We're in the network. Happy networking!")
        } else Logger.debug("We're the trusted node!")
        Logger.debug("Listening on port: " + configuration.listeningPort)
    }


    /**
     *  Set networking to respond using given lambda block to provided path on GET request.
     *
     * @param block Response lambda that will execute on GET request.
     */
    private infix fun String.get(block: Context.() -> Unit): Javalin = application.get(this, block)

    /**
     *  Set networking to respond using given lambda block to provided path on POST request.
     *
     * @param block Response lambda that will execute on POST request.
     */
    private infix fun String.post(block: Context.() -> Unit): Javalin = application.post(this, block)

    /*
    /**
     * Initiates the given protocol and passes the payload to it.
     *
     * @param protocol Chosen protocol.
     * @param payload Payload to be sent.
     */
    fun initiate(protocol: ProtocolTasks, payload: Any) {
        Logger.info("Initiating protocol task $protocol")
        when (protocol) {
            ProtocolTasks.newBlock -> blockPropagation.broadcast(payload as BlockData)
            ProtocolTasks.requestBlocks -> blockPropagation.requestBlocks(payload as Int)
            ProtocolTasks.requestInclusion -> consensus.requestInclusion(payload as String)
        }
    }

     */
}