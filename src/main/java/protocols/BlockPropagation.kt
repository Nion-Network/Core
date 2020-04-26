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
import javax.xml.ws.Response

class BlockPropagation(private val nodeNetwork: NodeNetwork, private val crypto: Crypto,private val blockChain: BlockChain, private val configuration: Configuration) {

    //TODO: Add message queue to check weather the message was already received and stop propagation
    init {
    }
    fun broadcast(block:Block) {
        try {
            nodeNetwork.pickRandomNodes(5).forEach { it.sendMessage("/newBlock", nodeNetwork.createNewBlockMessage(block)) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    //TODO: Decode message from context and decide to propagate further or stop
    fun receivedNewBlock(context: Context){
        val message = context.bodyAsMessage
        val blockMessage: NewBlockMessageBody = message.body fromJsonTo NewBlockMessageBody::class.java
        Logger.chain("Received block: ${blockMessage.block.height}}")
        if(blockChain.chain.contains(blockMessage.block)){
            nodeNetwork.pickRandomNodes(configuration.broadcastSpread).forEach { it.sendMessage("/newBlock", message) }
        }
    }

    fun receivedSyncRequest(context: Context){
        val message = context.bodyAsMessage
        val blockMessage: RequestBlocksMessageBody = message.body fromJsonTo RequestBlocksMessageBody::class.java
        val responseBlocksMessageBody = nodeNetwork.createResponseBlocksMessage(blockChain.chain.subList(blockMessage.height, blockChain.chain.size));
        var returnAddress: String = blockMessage.returnToHttpAddress;
        //returnAddress =  "http://${nodeNetwork.nodeMap.get(message.publicKey).ip}: ${nodeNetwork.nodeMap.get(message.publicKey).port}"}
        Utils.sendMessageTo(returnAddress, "/syncBlockchainReply", responseBlocksMessageBody)
    }

    fun requestBlocks(height:Int){
        try {
            nodeNetwork.pickRandomNodes(1).forEach { it.sendMessage("/syncBlockchainRequest", nodeNetwork.createRequestBlocskMessage(height)) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun processBlocks(context: Context){
        val responseBlocksMessageBody :ResponseBlocksMessageBody = context.bodyAsMessage.body.fromJsonTo(ResponseBlocksMessageBody::class.java)
        blockChain.syncChain(responseBlocksMessageBody.blocks);
    }
}
