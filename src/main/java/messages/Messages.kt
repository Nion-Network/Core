package messages

import abstraction.Node
import blockchain.Block

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 16:33
 * using IntelliJ IDEA
 */

data class IdentificationMessage(val node: Node)
data class NewBlockMessageBody(val block: Block)

data class ResponseBlocksMessageBody(val blocks: List<Block>)
data class RequestInclusionBody(val publicKey: String)
data class VdfProofBody(val proof: String, val block: Int)

data class QueryMessageBody(val node: Node, val searchingPublicKey: String)
data class RequestBlocksMessageBody(val node: Node, val height: Int)

data class FoundMessage(val foundIp: String, val foundPort: Int, val forPublicKey: String)