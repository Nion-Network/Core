package network

import io.javalin.Javalin
import io.javalin.http.Context
import protocols.DHT
import java.security.KeyPair

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:58
 * using IntelliJ IDEA
 */
class NetworkManager(port: Int, maxNodes: Int, private val keyPair: KeyPair) {

    private val nodeNetwork = NodeNetwork(maxNodes, keyPair)
    private val application = Javalin.create { it.showJavalinBanner = false }.start(port)


    // Protocols
    private val dhtProtocol: DHT = DHT(nodeNetwork)

    init {

        // TODO Send Join Request to main Node and wait for response on /joined

        "/ping" get { status(200) }
        "/join" post { dhtProtocol.joinRequest(this) }
        "/joined" post { dhtProtocol.onJoin(this) }
    }

    /**
     * Set javalin application's context to response to the string (path) with the context block.
     *
     * @param block the application will use when the GET path is visited.
     */
    infix fun String.get(block: Context.() -> Unit) = application.get(this, block)
    infix fun String.post(block: Context.() -> Unit) = application.post(this, block)
}