package network

import io.javalin.Javalin
import io.javalin.http.Context

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:58
 * using IntelliJ IDEA
 */
class NetworkManager(port: Int, maxNodes: Int) {

    private val nodeNetwork = NodeNetwork(maxNodes)
    private val application = Javalin.create { it.showJavalinBanner = false }.start(port)

    init {
        "/ping" get { status(200) }
        "/join" post { nodeNetwork.joinRequest(this) }
    }

    /**
     * Set javalin application's context to response to the string (path) with the context block.
     *
     * @param block the application will use when the GET path is visited.
     */
    infix fun String.get(block: Context.() -> Unit) = application.get(this, block)
    infix fun String.post(block: Context.() -> Unit) = application.post(this, block)
}