package network.rpc

import Configuration
import io.javalin.Javalin
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.http.WebSocket
import java.util.*
import java.util.concurrent.CompletionStage
import kotlin.collections.HashMap

open class RPCManager(configuration: Configuration) {

    private val webServer = Javalin.create {
        it.showJavalinBanner = false
    }.start(configuration.webSocketPort)

    private val pendingClients: MutableList<WsContext> = mutableListOf()
    private val subscribedClients: HashMap<Topic, MutableList<WsContext>> = hashMapOf()
    private val subscribedTopics: HashMap<WsContext, MutableList<Topic>> = hashMapOf()

    init {
        webServer.ws("/") {
            it.onConnect(this::onConnect)
            it.onClose(this::onClose)
        }
    }

    /**
     * On new WebSocket connection subscibe to all topics (for now, while in MVP).
     * After MVP the open WebSocket will be added to [pendingClients] and moved to appropriate topic subscriptions
     * once requested.
     */
    private fun onConnect(webSocket: WsConnectContext) {
        webSocket.send("Hello, this is Nion node!")
        Topic.entries.forEach { topic ->
            subscribedClients
                .computeIfAbsent(topic) { mutableListOf() }
                .add(webSocket)
        }
    }

    /**
     * On Client disconnect, remove the context from all subscribed topics.
     */
    private fun onClose(webSocket: WsCloseContext) {
        val subscriptions = subscribedTopics.remove(webSocket) ?: return
        subscriptions.forEach { topic ->
            subscribedClients[topic]?.remove(webSocket)
        }
    }

    /**
     * Sends serialised data to all clients that are subscribed to the [topic].
     */
    fun sendToSubscribed(topic: Topic, data: Any) {
        val serialisedData = Json.encodeToString(data)
        val clientList = subscribedClients[topic] ?: return
        clientList.forEach { it.send(serialisedData) }
    }

}

enum class Topic {
    Block,
    Migration,
    LocalApplication
}