package protocols

import common.Block
import common.BlockChain
import configuration.Configuration
import io.javalin.http.Context
import logging.Logger
import messages.NewBlockMessageBody
import messages.RequestBlocksMessageBody
import messages.ResponseBlocksMessageBody
import network.NodeNetwork
import utils.Crypto
import utils.Utils
import utils.bodyAsMessage
import utils.fromJsonTo

class BlockPropagation(private val nodeNetwork: NodeNetwork, private val crypto: Crypto, private val blockChain: BlockChain, private val configuration: Configuration) {

    //TODO: Add message queue to check weather the message was already received and stop propagation
    init {
    }

    fun broadcast(block: Block) {
        try {
            nodeNetwork.pickRandomNodes(5).forEach { it.sendMessage("/newBlock", nodeNetwork.createNewBlockMessage(block)) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //TODO: Decode message from context and decide to propagate further or stop
    fun receivedNewBlock(context: Context) {
        val message = context.bodyAsMessage
        val blockMessage: NewBlockMessageBody = message.body fromJsonTo NewBlockMessageBody::class.java
        Logger.chain("Received block: ${blockMessage.block.height}")
        blockChain.addBlock(blockMessage.block);
        if (blockChain.chain.contains(blockMessage.block)) {
            nodeNetwork.pickRandomNodes(configuration.broadcastSpread).forEach { it.sendMessage("/newBlock", message) }
        }
    }

    fun receivedSyncRequest(context: Context) {

        val message = context.bodyAsMessage
        val blockMessage: RequestBlocksMessageBody = message.body fromJsonTo RequestBlocksMessageBody::class.java
        Logger.trace("Received request for blockchain sync from height: ${blockMessage.height}")
        val responseBlocksMessageBody = nodeNetwork.createResponseBlocksMessage(blockChain.chain.subList(blockMessage.height, blockChain.chain.size));
        val returnAddress: String = blockMessage.returnToHttpAddress;
        Utils.sendMessageTo(returnAddress, "/syncBlockchainReply", responseBlocksMessageBody)
    }

    fun requestBlocks(height: Int) {
        try {
            Logger.info("Requesting new blocks from $height")
            nodeNetwork.pickRandomNodes(1).forEach { it.sendMessage("/syncBlockchainRequest", nodeNetwork.createRequestBlocskMessage(height)) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun processBlocks(context: Context) {
        try {
            val message = context.bodyAsMessage
            val responseBlocksMessageBody: ResponseBlocksMessageBody = message.body.fromJsonTo(ResponseBlocksMessageBody::class.java)
            Logger.trace("Received  ${responseBlocksMessageBody.blocks.size} blocks to sync")
            blockChain.syncChain(responseBlocksMessageBody.blocks);
        } catch (e: java.lang.Exception) {
            e.printStackTrace();
        }
    }
}
