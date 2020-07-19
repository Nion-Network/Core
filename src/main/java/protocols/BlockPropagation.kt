package protocols

import abstraction.Message
import common.BlockChain
import common.BlockData
import configuration.Configuration
import io.javalin.http.Context
import logging.Logger
import messages.NewBlockMessageBody
import messages.RequestBlocksMessageBody
import messages.ResponseBlocksMessageBody
import network.NodeNetwork
import utils.Crypto
import utils.Utils
import utils.getMessage

class BlockPropagation(private val nodeNetwork: NodeNetwork, private val crypto: Crypto, private val blockChain: BlockChain, private val configuration: Configuration) {

    //TODO: Add message queue to check weather the message was already received and stop propagation
    init {
    }

    fun broadcast(block: BlockData) {
        try {
            nodeNetwork.pickRandomNodes(5).forEach { it.sendMessage("/newBlock", nodeNetwork.createNewBlockMessage(block)) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun receivedNewBlock(context: Context) {
        val message: Message<NewBlockMessageBody> = context.getMessage()
        val newBlockMessage: NewBlockMessageBody = message.body
        val block: BlockData = newBlockMessage.block
        val height: Int = block.height
        val hash: String? = block.hash

        Logger.chain("Received block: $height : $hash")
        blockChain.addBlock(block);
        if (blockChain.chain.find { it.hash.equals(hash) } != null) {
            Logger.debug("Broadcasting block $hash (We want to, but we're not broadcasting...)")
            //nodeNetwork.pickRandomNodes(configuration.broadcastSpread).forEach { it.sendMessage("/newBlock", message) }
        } else {
            Logger.debug("Not broadcasting old block $hash")
        }
    }

    fun receivedSyncRequest(context: Context) {
        val message = context.getMessage<RequestBlocksMessageBody>()
        val blockMessage = message.body
        Logger.trace("Received request for blockchain sync from height: ${blockMessage.height}")

        val responseBlocksMessageBody = nodeNetwork.createResponseBlocksMessage(blockChain.chain.subList(blockMessage.height, blockChain.chain.size));
        val returnAddress: String = blockMessage.returnToHttpAddress;
        Utils.sendMessageTo(returnAddress, "/syncBlockchainReply", responseBlocksMessageBody)
    }

    fun requestBlocks(height: Int) {
        Logger.info("Requesting new blocks from $height")
        nodeNetwork.pickRandomNodes(1).forEach { it.sendMessage("/syncBlockchainRequest", nodeNetwork.createRequestBlocksMessage(height)) }
    }

    fun processBlocks(context: Context) {
        val message = context.getMessage<ResponseBlocksMessageBody>()
        val body = message.body
        val blocks = body.blocks
        Logger.trace("Received  ${blocks.size} blocks to sync")
        blockChain.syncChain(blocks);
    }
}
