package network

import abstraction.NetworkRequest
import configuration.Configuration
import io.javalin.Javalin
import io.javalin.http.Context
import logging.Logger
import protocols.DHT
import utils.Utils
import java.net.InetAddress
import java.security.KeyPair

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:58
 * using IntelliJ IDEA
 */
class NetworkManager(configuration: Configuration, keyPair: KeyPair) {

    private val nodeNetwork = NodeNetwork(configuration.maxNodes, keyPair)
    private val application = Javalin.create { it.showJavalinBanner = false }.start(configuration.listeningPort)

    private val myIP = InetAddress.getLocalHost().hostAddress

    // Protocols
    private val dhtProtocol: DHT = DHT(nodeNetwork)

    init {

        Logger.debug("My IP is $myIP")

        "/ping" get { status(200) }
        "/join" post { dhtProtocol.joinRequest(this) }
        "/joined" post { dhtProtocol.onJoin(this) }


        // Join request to trusted Node after setup
        if (myIP != configuration.trustedNodeIP) {
            Utils.urlRequest(NetworkRequest.POST, "http://${configuration.trustedNodeIP}:${configuration.trustedNodePort}/join")
            while (!nodeNetwork.isInNetwork) {
                Logger.trace("Waiting to be accepted into the network...")
                Thread.sleep(1000)
            }
            Logger.debug("We're in the network. Happy networking!")
        } else Logger.debug("We're the trusted node! Very important...")


    }

    /**
     * Set javalin application's context to response to the string (path) with the context block.
     *
     * @param block the application will use when the GET path is visited.
     */
    infix fun String.get(block: Context.() -> Unit) = application.get(this, block)
    infix fun String.post(block: Context.() -> Unit) = application.post(this, block)
}