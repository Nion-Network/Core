package protocols
import common.Block
import common.BlockChain
import configuration.Configuration
import io.javalin.http.Context
import logging.Logger
import messages.NewBlockMessageBody
import messages.RequestBlocksMessageBody
import network.NodeNetwork
import utils.Crypto
import utils.bodyAsMessage
import utils.fromJsonTo

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
        Logger.chain("Received block ${context.body()}")
        val message = context.bodyAsMessage
        val blockMessage: NewBlockMessageBody = message.body fromJsonTo NewBlockMessageBody::class.java
        if(blockChain.chain.contains(blockMessage.block)){
            nodeNetwork.pickRandomNodes(configuration.broadcastSpread).forEach { it.sendMessage("/newBlock", message) }
        }
    }

    fun receivedGetBlocks(context: Context){
        val message = context.bodyAsMessage
        val blockMessage: RequestBlocksMessageBody = message.body fromJsonTo RequestBlocksMessageBody::class.java
        context.result(Main.gson.toJson(nodeNetwork.createResponseBlocksMessage(blockChain.chain.subList(0,blockMessage.height))));
        nodeNetwork.pickRandomNodes(configuration.broadcastSpread).forEach { it.sendMessage("/newBlock", message) }
    }

    fun requestBlocks(height:Int){
        try {
            nodeNetwork.pickRandomNodes(5).forEach { it.sendMessage("/getBlocks", nodeNetwork.createRequestBlocskMessage(height))}
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
