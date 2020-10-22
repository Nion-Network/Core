package manager

import data.EndPoint
import data.EndPoint.*
import data.NetworkRequestType.GET
import data.NetworkRequestType.POST
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import logging.Logger
import network.NodeNetwork
import utils.Utils
import utils.networkHistory
import java.util.concurrent.LinkedBlockingQueue

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

    private val messageQueue = LinkedBlockingQueue<Pair<String, (String) -> Unit>>()

    fun start() {

        Thread {
            try {
                while (true) messageQueue.take().apply { second(first) }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }.start()

        SEARCH run { queryParam("pub_key")?.apply { dhtProtocol.sendSearchQuery(this) } }

        PING run { status(200) }
        JOIN run dhtProtocol::joinRequest
        QUERY run dhtProtocol::onQuery
        FOUND run dhtProtocol::onFound
        JOINED run dhtProtocol::onJoin
        INCLUDE run validatorManager::inclusionRequest
        SYNC_REQUEST queue chainManager::syncRequestReceived
        VOTE_REQUEST run committeeManager::voteRequest

        VOTE queue chainManager::voteReceived
        BLOCK queue chainManager::blockReceived
        SYNC_REPLY queue chainManager::syncReplyReceived

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
        application.before {
            val hex = it.header("hex")
            if (networkHistory.containsKey(hex)) throw ForbiddenResponse("NO MEANS NO")
            else if(hex != null) networkHistory[hex] = System.currentTimeMillis()
        }
        application.exception(Exception::class.java) { exception, _ -> exception.printStackTrace() }
    }


    /**
     *  Set networking to respond using given lambda block to provided path on GET request.
     *
     * @param block Response lambda that will execute on GET request.
     */
    private infix fun String.get(block: Context.() -> Unit): Javalin = application.get(this, block)

    /**ValidatorManager
     *  Set networking to respond using given lambda block to provided path on POST request.
     *
     * @param block Response lambda that will execute on POST request.
     */
    private infix fun String.post(block: Context.() -> Unit): Javalin = application.post(this, block)

    private infix fun EndPoint.run(block: Context.() -> Unit): Javalin = when (requestType) {
        GET -> path get block
        POST -> path post block
    }

    private infix fun EndPoint.queue(block: String.() -> Unit) = when (requestType) {
        GET -> path get { }
        POST -> path post { messageQueue.put(body() to block) }
    }
}