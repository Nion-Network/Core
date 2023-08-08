package network.rpc

import Configuration
import io.javalin.Javalin
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import network.data.messages.Message
import java.lang.Exception
import java.net.http.WebSocket
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import kotlin.collections.HashMap


/**
 * Created by Mihael Berčič
 * on 29/07/2022 at 10:44
 * using IntelliJ IDEA
 */
open class RPCManager(configuration: Configuration) {

    val subscribedClients: HashMap<Topic, MutableList<WsContext>> = hashMapOf()

    private val webServer = Javalin.create {
        it.showJavalinBanner = false
    }.start(configuration.webSocketPort)

    private val pendingClients: MutableList<WsContext> = mutableListOf()

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
        webSocket.enableAutomaticPings(5, TimeUnit.SECONDS)
        Topic.entries.forEach { topic ->
            subscribedClients
                .computeIfAbsent(topic) { mutableListOf() }
                .add(webSocket)
        }
        sendToSubscribed(Topic.Logging, "Hello, this is Nion node!")
    }

    /**
     * On Client disconnect, remove the context from all subscribed topics.
     */
    private fun onClose(webSocket: WsCloseContext) {
        removeConnection(webSocket)
    }

    fun removeConnection(webSocket: WsContext) {
        val subscriptions = subscribedTopics.remove(webSocket) ?: return
        subscriptions.forEach { topic ->
            subscribedClients[topic]?.remove(webSocket)
        }
    }

    /**
     * Sends serialised data to all clients that are subscribed to the [topic].
     */
    inline fun <reified T> sendToSubscribed(topic: Topic, data: T) {
        val message = RPCMessage(topic, data)
        val serialisedData = Json.encodeToString(message)
        val clientList = subscribedClients[topic] ?: return
        clientList.forEach {
            try {
                it.send(serialisedData)
            } catch (e: Exception) {
                removeConnection(it)
            }
        }
    }

}

@Serializable
data class RPCMessage<T>(val topic: Topic, val data: T)

enum class Topic {
    Block,
    Migration,
    LocalApplication,
    Logging
}