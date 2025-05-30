package network.rpc

import Configuration
import io.javalin.Javalin
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import network.kademlia.Kademlia
import java.util.concurrent.TimeUnit


/**
 * Created by Mihael Berčič
 * on 29/07/2022 at 10:44
 * using IntelliJ IDEA
 */
open class RPCManager(configuration: Configuration) : Kademlia(configuration) {

    val subscribedClients: HashMap<Topic, MutableList<WsContext>> = hashMapOf()
    private val pendingClients: MutableList<WsContext> = mutableListOf()
    private val subscribedTopics: HashMap<WsContext, MutableList<Topic>> = hashMapOf()

    init {
        if (isTrustedNode) {
            val webServer = Javalin.create {
                it.showJavalinBanner = false
            }.start(configuration.webSocketPort)
            webServer.ws("/") {
                it.onConnect(this::onConnect)
                it.onClose(this::onClose)
            }
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

    /**
     * Removes the subscribed client from the notification list.
     *
     * This function unregisters a client, ensuring it no longer receives
     * notifications or updates from the system.
     */
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

enum class Topic {
    Block,
    Migration,
    LocalApplication,
    Logging
}