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
class NetworkManager(val applicationManager: ApplicationManager) { // , blockChain: BlockChain

    val nodeNetwork = NodeNetwork(applicationManager)
    private val configuration = applicationManager.configuration

    private val dhtProtocol by lazy { applicationManager.dhtManager }
    private val vdfManager by lazy { applicationManager.vdfManager }
    private val chainManager by lazy { applicationManager.chainManager }
    private val validatorManager by lazy { applicationManager.validatorManager }
    private val committeeManager by lazy { applicationManager.committeeManager }

    private val application = Javalin.create { it.showJavalinBanner = false }.start(configuration.listeningPort)


    fun start() {
        "/ping" get { status(200) }

        // DHT protocol
        "/query" post { dhtProtocol.onQuery(this) }
        "/found" post { dhtProtocol.onFound(this) }
        "/joined" post { dhtProtocol.onJoin(this) }
        "/join" post { dhtProtocol.joinRequest(this) }
        "/search" get { this.queryParam("pub_key")?.apply { dhtProtocol.sendSearchQuery(this) } }

        "/include" post { validatorManager.validatorSetInclusionRequest(this) }
        "/syncRequest" post { chainManager.syncRequestReceived(this) } //we were asked for our blocks
        "/syncReply" post { chainManager.syncReplyReceived(this) } //we received a reply to our request for blocks
        "/block" post { chainManager.blockReceived(this) }
        "/voteRequest" post { committeeManager.voteRequest(this) }
        "/vote" post { chainManager.voteReceived(this) }

        if (!applicationManager.isTrustedNode) {
            Logger.trace("Sending join request to our trusted node...")
            val response = Utils.sendMessageTo(configuration.trustedHttpAddress, "/join", nodeNetwork.createIdentificationMessage())
            Logger.trace("Join response from trusted node: $response")

            while (!nodeNetwork.isInNetwork) {
                Logger.trace("Waiting to be accepted into the network...")
                Thread.sleep(1000)
            }

            Logger.debug("We're in the network. Happy networking!")
        } else Logger.debug("We're the trusted node!")
        Logger.debug("Listening on port: " + configuration.listeningPort)
    }

    init {

        application.exception(Exception::class.java) { exception, context ->
            Logger.error("Stumbled upon error on request from ${context.ip()}")
            exception.printStackTrace()
        }

        application.before { if (networkHistory.containsKey(it.header("hex"))) Logger.error("We've already seen this message on [${it.path()}]... We're ignoring it!") }
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
}