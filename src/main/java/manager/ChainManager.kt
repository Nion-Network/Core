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

    private val chain = mutableListOf<Block>()
    private val vdf by lazy { applicationManager.vdf }
    private val nodeNetwork by lazy { applicationManager.networkManager.nodeNetwork }
    private val validatorManager by lazy { applicationManager.validatorManager }

    fun addBlock(block: Block) = chain.add(block).apply { Logger.chain("Added block: ${block.epoch}") }

    val lastBlock: Block? get() = chain.lastOrNull()

    fun runVDF() {
        println(chain.lastOrNull())
        chain.lastOrNull()?.apply { runVDF(this) }
    }

    private fun runVDF(onBlock: Block) = vdf.runVDF(onBlock.difficulty, onBlock.hash, onBlock.epoch)

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
                block.validatorChanges.forEach { (publicKey, isAdded) ->
                    if (isAdded) applicationManager.currentValidators.add(publicKey)
                    else applicationManager.currentValidators.remove(publicKey)
                }
            } ?: if (block.epoch == 0 && block.slot == 0 && block.precedentHash.isEmpty()) addBlock(block)
        }
        validatorManager.requestInclusion()
        val lastBlock = chain.lastOrNull()
        lastBlock?.apply { vdf.runVDF(lastBlock.difficulty, lastBlock.hash, lastBlock.epoch) }
    }

    fun blockReceived(context: Context){
        val message = context.getMessage<NewBlockMessageBody>()
        val body = message.body
        val newBlock = body.block

        val epoch = newBlock.epoch
        val slot = newBlock.slot

        Logger.chain("Received block at [$epoch]:[$slot] to add...")
        chain.add(newBlock)
    }

}
