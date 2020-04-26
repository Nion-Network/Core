package messages

import abstraction.Node
import common.Block

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 16:33
 * using IntelliJ IDEA
 */

data class WelcomeMessageBody(val acceptorNode: Node)
data class NewBlockMessageBody(val block: Block)
data class RequestBlocksMessageBody(val returnIp: String, val returnPort: Int,val height: Int){
    val returnToHttpAddress: String get() = "http://$returnIp:$returnPort"
}
data class ResponseBlocksMessageBody(val blocks: List<Block>)
data class QueryMessageBody(val returnIp: String, val returnPort: Int, val searchingPublicKey: String) {
    val returnToHttpAddress: String get() = "http://$returnIp:$returnPort"
}

data class FoundMessageBody(val foundIp: String, val foundPort: Int, val forPublicKey: String)