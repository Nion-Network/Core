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
import utils.getMessage

class BlockPropagation(private val nodeNetwork: NodeNetwork, private val crypto: Crypto, private val blockChain: BlockChain, private val configuration: Configuration) {


    //TODO: Add message queue to check weather the message was already received and stop propagation

    fun broadcast(block: BlockData) {
        Logger.debug("We're broadcasting block ${block.hash}")
        val newBlockMessage = nodeNetwork.createNewBlockMessage(block)
        nodeNetwork.broadcast("/newBlock", newBlockMessage, true)
    }

    fun receivedNewBlock(context: Context) {
        val message: Message<NewBlockMessageBody> = context.getMessage()
        val newBlockMessage: NewBlockMessageBody = message.body
        val block: BlockData = newBlockMessage.block
        val height: Int = block.height
        val hash: String = block.hash

        Logger.debug("A new block has been received at height $height... | $hash")
        if (blockChain.addBlock(block)) {
            Logger.info("Broadcasting an accepted block...")
            nodeNetwork.broadcast("/newBlock", message, true)
        } else Logger.debug("The block received has not been added to the chain...")
    }

    fun receivedSyncRequest(context: Context) {
        val message = context.getMessage<RequestBlocksMessageBody>()
        val blockMessage = message.body
        Logger.debug("Received request for blockchain sync from height: ${blockMessage.height}")

        Logger.debug("Sending back a response with blocks to sync...")
        val responseBlocksMessageBody = nodeNetwork.createResponseBlocksMessage(blockChain.chain.drop(blockMessage.height))
        blockMessage.node.sendMessage("/syncBlockchainReply", responseBlocksMessageBody)
    }

    fun requestBlocks(height: Int) {
        Logger.info("Requesting new blocks from $height")
        nodeNetwork.pickRandomNodes(1).forEach { it.sendMessage("/syncBlockchainRequest", nodeNetwork.createRequestBlocksMessage(height)) }
    }

    fun processBlocks(context: Context) {
        val message = context.getMessage<ResponseBlocksMessageBody>()
        val body = message.body
        val blocks = body.blocks
        Logger.trace("Received ${blocks.size} blocks to sync")
        blockChain.syncChain(blocks)
    }
}
