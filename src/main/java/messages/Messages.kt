package messages

import abstraction.Node
import common.BlockData

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 16:33
 * using IntelliJ IDEA
 */

data class WelcomeMessageBody(val acceptorNode: Node)
data class NewBlockMessageBody(val block: BlockData)
data class RequestBlocksMessageBody(val returnIp: String, val returnPort: Int,val height: Int){
    val returnToHttpAddress: String get() = "http://$returnIp:$returnPort"
}
data class ResponseBlocksMessageBody(val blocks: List<BlockData>)
data class RequestInclusionBody(val publicKey: String)
data class VdfProofBody(val proof: String)
data class QueryMessageBody(val returnIp: String, val returnPort: Int, val searchingPublicKey: String) {
    val returnToHttpAddress: String get() = "http://$returnIp:$returnPort"
}

data class FoundMessageBody(val foundIp: String, val foundPort: Int, val forPublicKey: String)