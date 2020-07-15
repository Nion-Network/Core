package messages

import abstraction.Node
import common.BlockData

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 16:33
 * using IntelliJ IDEA
 */

data class IdentificationMessage(val node: Node)
data class WelcomeMessageBody(val acceptorNode: Node)
data class NewBlockMessageBody(val block: BlockData)
data class JoinRequest(val node: Node)

data class ResponseBlocksMessageBody(val blocks: List<BlockData>)
data class RequestInclusionBody(val publicKey: String)
data class VdfProofBody(val proof: String)

data class QueryMessageBody(val returnIp: String, val returnPort: Int, val searchingPublicKey: String) : ReturnableMessage(returnIp, returnPort)
data class RequestBlocksMessageBody(val returnIp: String, val returnPort: Int, val height: Int) : ReturnableMessage(returnIp, returnPort)

abstract class ReturnableMessage(ip: String, port: Int) {
    val returnToHttpAddress: String = "http://$ip:$port"
}

data class FoundMessage(val foundIp: String, val foundPort: Int, val forPublicKey: String)