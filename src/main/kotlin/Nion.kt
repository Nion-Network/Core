import data.Configuration
import data.communication.Message
import data.communication.TransmissionType
import data.communication.WelcomeMessage
import data.network.Endpoint
import data.network.MessageProcessing
import data.network.Node
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import network.ChainBuilder
import network.DockerProxy
import utils.launchCoroutine
import utils.tryAndReport
import java.util.concurrent.LinkedBlockingQueue


/**
 * Created by Mihael Valentin Berčič
 * on 26/03/2020 12:35
 * using IntelliJ IDEA
 */
class Nion(configuration: Configuration) : DockerProxy(configuration) {

    // Perhaps future change of Nion : ChainBuilder...

    private val chainBuilder = ChainBuilder(this)
    private val queue = LinkedBlockingQueue<() -> Unit>()
    private var isInNetwork = isTrustedNode

    private val endpoints = mutableMapOf<Endpoint, (Message) -> Unit>(
        Endpoint.NodeQuery to ::onQuery,
        Endpoint.QueryReply to ::onQueryReply,
        Endpoint.Welcome to ::onWelcome,
        Endpoint.JoinRequest to ::joinRequestReceived,
        Endpoint.NodeStatistics to ::dockerStatisticsReceived,
        Endpoint.NewBlock to chainBuilder::blockReceived,
        Endpoint.InclusionRequest to chainBuilder::inclusionRequested,
        Endpoint.SyncRequest to chainBuilder::synchronizationRequested,
        Endpoint.SyncReply to chainBuilder::synchronizationReply
    )

    init {
        // TODO Remove after final decision of known trusted public key.
        addNewNodes(Node(configuration.trustedNodeIP, configuration.trustedNodePort, "trusted"))
        Thread(this::invokeFromQueue).start()
    }

    private fun requestNetworkInclusion() {
        if (isInNetwork) {
            knownNodes.remove("trusted")
            return
        }
        send(Endpoint.JoinRequest, TransmissionType.Unicast, localNode)
        Thread.sleep(10_000)
        requestNetworkInclusion()
    }

    private fun onWelcome(message: Message) {
        val welcomeMessage = message.decodeAs<WelcomeMessage>()
        val acceptorNode = welcomeMessage.acceptor
        val newNodes = welcomeMessage.knownNodes
        addNewNodes(acceptorNode, *newNodes.toTypedArray())
        isInNetwork = true
        chainBuilder.requestInclusion()
    }

    private fun invokeFromQueue() {
        while (true) tryAndReport { queue.take().invoke() }
    }

    override fun onMessageReceived(endpoint: Endpoint, data: ByteArray) {
        val message = ProtoBuf.decodeFromByteArray<Message>(data)
        val verified = crypto.verify(message.body, message.signature, message.publicKey)
        if (verified) {
            val execution = endpoints[endpoint] ?: throw Exception("Endpoint $endpoint has no handler set.")
            if (endpoint.processing == MessageProcessing.Queued) queue.put { execution(message) }
            else launchCoroutine { execution(message) }
        }
    }

    override fun launch() {
        super.launch()
        requestNetworkInclusion()
    }
}

