import data.communication.Message
import data.communication.TransmissionType
import data.network.Endpoint
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Logger
import network.ChainBuilder
import network.DistributedHashTable
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by Mihael Valentin Berčič
 * on 26/03/2020 12:35
 * using IntelliJ IDEA
 */
class Nion : DistributedHashTable() {

    private val endpoints = mutableMapOf<Endpoint, (Message) -> Unit>()
    private val chainBuilder = ChainBuilder(this)

    init {
        Logger.toggleLogging(configuration.loggingEnabled)
        registerEndpoints(this, chainBuilder)
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

    inline infix fun <reified T> ByteArray.queue(noinline block: (T) -> Unit) {
        queue.put { block(ProtoBuf.decodeFromByteArray(this)) }
        println("Put into queue!")
        queue.take()()
    }

    val queue = LinkedBlockingQueue<() -> Unit>()

    override fun onMessageReceived(endpoint: Endpoint, data: ByteArray) {
        val message = ProtoBuf.decodeFromByteArray<Message>(data)
        val verified = crypto.verify(message.body, message.signature, message.publicKey)
        if (verified) endpoints[endpoint]?.invoke(message)
    }
}

fun main(args: Array<String>) {
    System.setProperty("kotlinx.coroutines.scheduler", "off")
    Nion().apply {
        launch()
        queryFor("kiha")
        send(Endpoint.Ping, TransmissionType.Unicast, "Hello!")
    }
}

annotation class MessageEndpoint(val endpoint: Endpoint)