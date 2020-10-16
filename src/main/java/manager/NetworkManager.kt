package manager

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import logging.Logger
import network.NodeNetwork
import utils.Utils
import utils.networkHistory

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:58
 * using IntelliJ IDEA
 */
class NetworkManager(val applicationManager: ApplicationManager) {

    val nodeNetwork = NodeNetwork(applicationManager)
    private val configuration = applicationManager.configuration

    private val dhtProtocol by lazy { applicationManager.dhtManager }
    private val chainManager by lazy { applicationManager.chainManager }
    private val validatorManager by lazy { applicationManager.validatorManager }
    private val committeeManager by lazy { applicationManager.committeeManager }

    private val application = Javalin.create { it.showJavalinBanner = false }.start(configuration.listeningPort)


    fun start() {
        "/ping" get { status(200) }

        // DHT protocol endpoints
        "/join" post { dhtProtocol.joinRequest(this) }
        "/query" post { dhtProtocol.onQuery(this) }
        "/found" post { dhtProtocol.onFound(this) }
        "/joined" post { dhtProtocol.onJoin(this) }
        "/search" get { queryParam("pub_key")?.apply { dhtProtocol.sendSearchQuery(this) } }

        // Blockchain endpoints
        "/vote" post { chainManager.voteReceived(this) }
        "/block" post { chainManager.blockReceived(this) }
        "/include" post { validatorManager.inclusionRequest(this) }
        "/syncReply" post { chainManager.syncReplyReceived(this) }
        "/syncRequest" post { chainManager.syncRequestReceived(this) }
        "/voteRequest" post { committeeManager.voteRequest(this) }

        if (!applicationManager.isTrustedNode) {
            Logger.trace("Sending join request to our trusted node...")
            val response = Utils.sendMessageTo(configuration.trustedHttpAddress, "/join", applicationManager.identificationMessage)
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
        application.before { if (networkHistory.containsKey(it.header("hex"))) throw ForbiddenResponse("NO MEANS NO") }
        application.exception(Exception::class.java) { exception, _ -> exception.printStackTrace() }
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