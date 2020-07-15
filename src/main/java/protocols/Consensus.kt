package protocols

import Main
import abstraction.Message
import common.BlockChain
import io.javalin.http.Context
import logging.Logger
import messages.RequestInclusionBody
import messages.VdfProofBody
import network.NodeNetwork
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import utils.getMessage


class Consensus(private val nodeNetwork: NodeNetwork, private val crypto: Crypto, private val blockChain: BlockChain) {

    fun validatorSetInclusionRequest(context: Context) {
        val message: Message<RequestInclusionBody> = context.getMessage()
        message.body.apply {
            blockChain.addInclusionRequest(publicKey)
            Logger.consensus("Received inclusion request from: ${DigestUtils.sha256Hex(publicKey)}")
        }
    }

    fun requestInclusion(publicKey: String) {
        nodeNetwork.createValidatorInclusionRequestMessage(publicKey).also { message ->
            nodeNetwork.pickRandomNodes(5).forEach { it.sendMessage("/include", message) }
        }
    }

    fun receivedVdf(context: Context, blockChain: BlockChain) {
        val message = if (context.ip() != "127.0.0.1") context.getMessage<VdfProofBody>() else null
        val body = message?.body ?: Main.gson.fromJson<VdfProofBody>(context.body(), VdfProofBody::class.java)
        val proof = body.proof
        val block = body.block

        Logger.consensus("Received VDF proof: ${DigestUtils.sha256Hex(proof)}")

        Logger.error(context.body())

        if (blockChain.updateVdf(proof, block)) {
            Logger.consensus("Broadcasting proof")
            nodeNetwork.pickRandomNodes(5).forEach { it.sendMessage("/vdf", nodeNetwork.createVdfProofMessage(proof, block)) }
        }
    }
}