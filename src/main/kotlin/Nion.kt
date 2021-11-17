import data.Configuration
import data.communication.Message
import data.communication.TransmissionType
import data.communication.WelcomeMessage
import data.network.Endpoint
import data.network.MessageProcessing
import data.network.Node
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import network.ChainBuilder
import network.DistributedHashTable
import network.MessageEndpoint
import java.io.PrintWriter
import java.io.StringWriter
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
    private var isInNetwork = isTrustedNode

    init {
        // TODO Remove after final decision of known trusted public key.
        addNewNodes(Node(configuration.trustedNodeIP, configuration.trustedNodePort, "trusted"))
        registerEndpoints(this, chainBuilder)
        Thread(this::invokeFromQueue).start()
    }

    private fun requestNetworkInclusion() {
        send(Endpoint.JoinRequest, TransmissionType.Unicast, localNode)
        Thread.sleep(10_000)
        if (!isInNetwork) requestNetworkInclusion()
    }

    @MessageEndpoint(Endpoint.Welcome)
    fun onWelcome(message: Message) {
        val welcomeMessage = message.decodeAs<WelcomeMessage>()
        val acceptorNode = welcomeMessage.acceptor
        val newNodes = welcomeMessage.knownNodes
        addNewNodes(acceptorNode, *newNodes.toTypedArray())
        isInNetwork = true
        chainBuilder.requestInclusion()
    }

    private fun invokeFromQueue() {
        try {
            while (true) queue.take().invoke()
        } catch (e: Exception) {
            Dashboard.reportException(e)
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            Logger.error(sw.toString())
        }
    }

    private fun registerEndpoints(vararg instances: Any) {
        instances.forEach { instance ->
            val methods = instance.javaClass.methods.filter { it.annotations.any { annotation -> annotation is MessageEndpoint } }
            methods.forEach { method ->
                val annotation = method.getAnnotation(MessageEndpoint::class.java)
                val endpoint = annotation.endpoint
                if (endpoints.containsKey(endpoint)) throw Exception("Endpoint $endpoint is already registered.")
                endpoints[endpoint] = { method.invoke(instance, it) }
                Logger.trace("Registered ${method.name} at $endpoint.")
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
                    val sw = StringWriter()
                    e.printStackTrace(PrintWriter(sw))
                    Logger.error(sw.toString())
                }
            }
        }
    }

    override fun launch() {
        super.launch()
        requestNetworkInclusion()
    }
}

