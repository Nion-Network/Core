import chain.ChainBuilder
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Logger
import network.data.Endpoint
import network.data.MessageProcessing
import network.data.communication.Message
import utils.launchCoroutine
import utils.runAfter
import utils.tryAndReport
import java.util.concurrent.LinkedBlockingQueue
import kotlin.random.Random


/**
 * Created by Mihael Valentin Berčič
 * on 26/03/2020 12:35
 * using IntelliJ IDEA
 */
class Nion(configuration: Configuration) : ChainBuilder(configuration) {

    // Perhaps future change of Nion : ChainBuilder...
    private val processingQueue = LinkedBlockingQueue<() -> Unit>()

    private val endpoints = mutableMapOf<Endpoint, (Message) -> Unit>(
        Endpoint.NodeStatistics to ::dockerStatisticsReceived,
        Endpoint.NewBlock to ::blockReceived,
        Endpoint.InclusionRequest to ::inclusionRequested,
        Endpoint.SyncRequest to ::synchronizationRequested,
        Endpoint.SyncReply to ::synchronizationReply
    )

    init {
        Thread(this::processQueuedActions).start()
    }

    /** Takes queued actions from [processingQueue] and executes them under supervision. */
    private fun processQueuedActions() {
        while (true) tryAndReport { processingQueue.take().invoke() }
    }

    override fun launch() {
        super.launch()
        attemptBootstrap()
        attemptInclusion()
    }

    private fun attemptBootstrap() {
        if (isTrustedNode || isBootstrapped) return
        Logger.info("Attempting bootstrapping.")
        bootstrap(configuration.trustedNodeIP, configuration.trustedNodePort)
        runAfter(Random.nextLong(10000, 20000), this::attemptBootstrap)
    }


    override fun onMessageReceived(endpoint: Endpoint, data: ByteArray) {
        Logger.info("OnMessageReceived called for $endpoint.")
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

