import data.Configuration
import data.communication.Message
import data.network.Endpoint
import data.network.MessageProcessing
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Logger
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
    private val processingQueue = LinkedBlockingQueue<() -> Unit>()

    private val endpoints = mutableMapOf<Endpoint, (Message) -> Unit>(
        Endpoint.NodeStatistics to ::dockerStatisticsReceived,
        Endpoint.NewBlock to chainBuilder::blockReceived,
        Endpoint.InclusionRequest to chainBuilder::inclusionRequested,
        Endpoint.SyncRequest to chainBuilder::synchronizationRequested,
        Endpoint.SyncReply to chainBuilder::synchronizationReply
    )

    init {
        Thread(this::processQueuedActions).start()
    }

    /** Takes queued actions from [processingQueue] and executes them under supervision. */
    private fun processQueuedActions() {
        while (true) tryAndReport { processingQueue.take().invoke() }
    }

    /** Acts accordingly to the [Endpoint] on how to process the received [Message]. */
    override fun onMessageReceived(endpoint: Endpoint, data: ByteArray) {
        val message = ProtoBuf.decodeFromByteArray<Message>(data)
        val verified = crypto.verify(message.body, message.signature, message.publicKey)
        Logger.debug("We received a message on ${message.endpoint} [$verified]")
        if (verified) {
            val execution = endpoints[endpoint] ?: throw Exception("Endpoint $endpoint has no handler set.")
            if (endpoint.processing == MessageProcessing.Queued) processingQueue.put { execution(message) }
            else launchCoroutine { execution(message) }
        }
    }
}

