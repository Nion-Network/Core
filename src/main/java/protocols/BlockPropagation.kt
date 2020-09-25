package protocols

import abstraction.Message
import blockchain.Block
import configuration.Configuration
import io.javalin.http.Context
import logging.Logger
import messages.NewBlockMessageBody
import messages.RequestBlocksMessageBody
import messages.ResponseBlocksMessageBody
import network.NodeNetwork
import utils.Crypto
import utils.getMessage

/*
class BlockPropagation(private val nodeNetwork: NodeNetwork, private val crypto: Crypto, private val blockChain: BlockChain, private val configuration: Configuration) {


    //TODO: Add message queue to check weather the message was already received and stop propagation

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

    }
}

*/