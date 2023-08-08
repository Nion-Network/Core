import chain.ChainBuilder
import kotlinx.serialization.ExperimentalSerializationApi
import logging.Dashboard
import logging.Logger
import network.data.Endpoint
import network.data.MessageProcessing
import network.data.messages.Message
import utils.asHex
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
@ExperimentalSerializationApi
class Nion(configuration: Configuration) : ChainBuilder(configuration) {

    private val processingQueue = LinkedBlockingQueue<() -> Unit>()

    private val endpoints = mutableMapOf<Endpoint, (Message) -> Unit>(
        Endpoint.Ping to { Logger.info("We were pinged! ${String(it.body)}") },
        Endpoint.NodeStatistics to ::dockerStatisticsReceived,
        Endpoint.NewBlock to ::blockReceived,
        Endpoint.InclusionRequest to ::inclusionRequested,
        Endpoint.SyncRequest to ::synchronizationRequested,
        Endpoint.SyncReply to ::synchronizationReply,
        Endpoint.VoteRequest to ::voteRequested,
        Endpoint.Vote to ::voteReceived
    )

    init {
        Thread(this::processQueuedActions).start()
    }

    /** Takes queued actions from [processingQueue] and executes them under supervision. */
    private fun processQueuedActions() {
        while (true) tryAndReport { processingQueue.take().invoke() }
    }

    fun launch() {
        if (localAddress.isLoopbackAddress) {
            Dashboard.reportException(Exception("Local address: $localAddress!"))
            return
        }
        attemptBootstrap()
        attemptInclusion()
    }

    private fun attemptBootstrap() {
        if (isTrustedNode || isBootstrapped) return

        Logger.info("Attempting bootstrapping to ${configuration.trustedNodeIP}:${configuration.trustedNodePort}.")
        bootstrap(configuration.trustedNodeIP, configuration.trustedNodePort)
        runAfter(Random.nextLong(10000, 20000), this::attemptBootstrap)
    }

    override fun processMessage(message: Message) {
        val verified = crypto.verify(message.body, message.signature, message.publicKey)
        Logger.debug("We received a message on ${message.endpoint} [$verified]")
        if (verified) {
            val endpoint = message.endpoint
            // TODO remove: is for testing and presentation
            if (endpoint == Endpoint.NewBlock) {
                Dashboard.receivedMessage(message.uid.asHex, endpoint, localNode.publicKey, System.currentTimeMillis())
            }

            // TODO -------------------------------------
            val execution = endpoints[endpoint] ?: return // throw Exception("Endpoint $endpoint has no handler set.")
            if (endpoint.processing == MessageProcessing.Queued) processingQueue.put { execution(message) }
            else launchCoroutine { execution(message) }
        }

    }
}

