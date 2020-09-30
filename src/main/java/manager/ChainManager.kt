package manager

import blockchain.Block
import io.javalin.http.Context
import logging.Logger
import messages.NewBlockMessageBody
import messages.RequestBlocksMessageBody
import messages.ResponseBlocksMessageBody
import utils.getMessage

/**
 * Created by Mihael Valentin Berčič
 * on 25/09/2020 at 16:58
 * using IntelliJ IDEA
 */
class ChainManager(private val applicationManager: ApplicationManager) {

    val chain = mutableListOf<Block>()
    private val configuration by lazy { applicationManager.configuration }
    private val vdf by lazy { applicationManager.vdf }
    private val nodeNetwork by lazy { applicationManager.networkManager.nodeNetwork }
    private val validatorManager by lazy { applicationManager.validatorManager }
    private val timeManager by lazy { applicationManager.timerManager }

    // TODO move
    val mySlotDuties: MutableMap<Int, Doodie> = mutableMapOf()
    private val blockProducer by lazy { applicationManager.blockProducer }

    private var isTimerSetup = false

    @Synchronized
    fun startTheTimer() {
        if (!isTimerSetup) {
            Logger.info("Starting the timer...")
            runTimer()
            isTimerSetup = true
        }
    }

    private fun runTimer() {
        val slotDuration = configuration.slotDuration
        val state = applicationManager.currentState
        val currentSlot = state.ourSlot

        val currentBlockIndex = currentSlot + state.currentEpoch * configuration.slotCount
        val slotBlock = chain.getOrNull(currentBlockIndex - 1)

        when (mySlotDuties[currentSlot]) {
            Doodie.PRODUCER -> {
                val newBlock = slotBlock?.let { blockProducer.createBlock(it) } ?: blockProducer.genesisBlock
                val message = nodeNetwork.createNewBlockMessage(newBlock)
                nodeNetwork.broadcast("/block", message)
                addBlock(newBlock)
                applicationManager.validatorSetChanges.clear()
            }
            Doodie.COMMITTEE -> {
                Logger.info("This slot we're the committee and for now we don't do shit.")
            }
            Doodie.VALIDATOR -> {
                Logger.info("This slot we're the validators and for now we don't do shit.")
            }
            null -> {
            }
        }

        if (++state.ourSlot <= configuration.slotCount) {
            val oldBlock = chain.getOrNull(currentBlockIndex)
            val delay = oldBlock?.let { slotDuration - (System.currentTimeMillis() - it.timestamp) } ?: slotDuration
            Logger.debug("Task for [${state.currentEpoch}][$currentSlot] is ${mySlotDuties[currentSlot]} --- Chain size: ${chain.size} Next: $delay ms")
            timeManager.runAfter(delay) { runTimer() }
        } else {
            state.currentEpoch++
            state.ourSlot = 0
            runVDF()
            isTimerSetup = false
        }

    }

    fun addBlock(block: Block) {
        applicationManager.currentValidators.apply {
            block.validatorChanges.forEach { (publicKey, change) ->
                if (change) add(publicKey.apply { Logger.info("Adding one public key!") }) else remove(publicKey.apply { Logger.info("Deleting one public key!") })
            }
        }
        chain.add(block)
    }

    val lastBlock: Block? get() = chain.lastOrNull()

    fun runVDF() {
        chain.lastOrNull()?.apply { runVDF(this) }
    }

    fun runVDF(onBlock: Block) = vdf.runVDF(onBlock.difficulty, onBlock.hash, onBlock.epoch)

    fun isVDFCorrect(proof: String) = chain.lastOrNull()?.let { lastBlock ->
        vdf.verifyProof(lastBlock.difficulty, lastBlock.hash, proof)
    } ?: false

    fun requestSync(fromHeight: Int) {
        Logger.info("Requesting new blocks from $fromHeight")
        val message = nodeNetwork.createRequestBlocksMessage(fromHeight)
        nodeNetwork.sendMessageToRandomNodes("/syncRequest", 1, message)
    }

    fun syncRequestReceived(context: Context) {
        val message = context.getMessage<RequestBlocksMessageBody>()
        val blockMessage = message.body
        Logger.debug("Received request for sync from epoch: ${blockMessage.epoch}")

        Logger.debug("Sending back a response with blocks to sync...")
        val blocks = chain.drop(blockMessage.epoch)
        val responseBlocksMessageBody = nodeNetwork.createResponseBlocksMessage(blocks)
        blockMessage.node.sendMessage("/syncReply", responseBlocksMessageBody)
    }

    fun syncReplyReceived(context: Context) {
        val message = context.getMessage<ResponseBlocksMessageBody>()
        val body = message.body
        val blocks = body.blocks

        Logger.info("We have ${blocks.size} blocks to sync...")
        blocks.forEach { block ->
            val lastBlock = chain.lastOrNull()
            lastBlock?.apply {
                addBlock(block)
                applicationManager.currentState.ourSlot = block.slot
                applicationManager.currentState.currentEpoch = block.epoch
            } ?: if (block.epoch == 0 && block.slot == 0 && block.precedentHash.isEmpty()) addBlock(block)
        }
        validatorManager.requestInclusion()
    }

    fun blockReceived(context: Context) {
        val message = context.getMessage<NewBlockMessageBody>()
        val body = message.body
        val newBlock = body.block

        val epoch = newBlock.epoch
        val slot = newBlock.slot

        Logger.debug("New block received: ${newBlock.epoch}x${newBlock.slot}")
        addBlock(newBlock)
        startTheTimer()
    }

}

enum class Doodie { PRODUCER, COMMITTEE, VALIDATOR }
/*
val lastBlock = chain.lastOrNull()
lastBlock?.apply { vdf.runVDF(lastBlock.difficulty, lastBlock.hash, lastBlock.epoch) }
*/