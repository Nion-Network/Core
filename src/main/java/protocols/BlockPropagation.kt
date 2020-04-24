package protocols
import common.Block
import io.javalin.http.Context
import network.NodeNetwork
import utils.Crypto

class BlockPropagation(private val nodeNetwork: NodeNetwork, private val crypto: Crypto) {
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

    }
}
