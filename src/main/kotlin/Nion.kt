import data.Configuration
import data.communication.Message
import data.network.Endpoint
import data.network.MessageProcessing
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import network.ChainBuilder
import network.DistributedHashTable
import network.MessageEndpoint
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by Mihael Valentin Berčič
 * on 26/03/2020 12:35
 * using IntelliJ IDEA
 */
class Nion(configuration: Configuration) : DistributedHashTable(configuration) {

    private val endpoints = mutableMapOf<Endpoint, (Message) -> Unit>()
    private val chainBuilder = ChainBuilder(this)
    private val queue = LinkedBlockingQueue<() -> Unit>()

    init {
        registerEndpoints(this, chainBuilder)
        Thread(this::invokeFromQueue).start()
    }

    private fun invokeFromQueue() {
        try {
            while (true) queue.take().invoke()
        } catch (e: Exception) {
            Dashboard.reportException(e)
        }
    }

    private fun registerEndpoints(vararg instances: Any) {
        instances.forEach { instance ->
            val methods = instance.javaClass.methods.filter { it.annotations.any { annotation -> annotation is MessageEndpoint } }
            methods.forEach { method ->
                val annotation = method.getAnnotation(MessageEndpoint::class.java)
                endpoints[annotation.endpoint] = { method.invoke(instance, it) }
            }
        }
    }

    override fun onMessageReceived(endpoint: Endpoint, data: ByteArray) {
        val message = ProtoBuf.decodeFromByteArray<Message>(data)
        val verified = crypto.verify(message.body, message.signature, message.publicKey)
        if (verified) {
            val execution = endpoints[endpoint] ?: throw Exception("Endpoint $endpoint has no handler set.")
            if (endpoint.processing == MessageProcessing.Queued) queue.put { execution(message) }
            else GlobalScope.launch {
                try {
                    execution(message)
                } catch (e: Exception) {
                    Dashboard.reportException(e)
                }
            }
        }
    }
}

