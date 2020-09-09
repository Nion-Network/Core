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
            blockChain.pendingInclusionRequests.add(publicKey)
            Logger.consensus("Received inclusion request from: ${DigestUtils.sha256Hex(publicKey)}")
        }
    }

    fun requestInclusion(publicKey: String) {
        Logger.debug("Requesting inclusion...")
        nodeNetwork.createValidatorInclusionRequestMessage(publicKey).also { message ->
            nodeNetwork.pickRandomNodes(5).forEach { it.sendMessage("/include", message) }
        }
    }

    fun receivedVdf(context: Context) {
        val ip = context.ip()
        val isLocal = ip == "127.0.0.1"
        val message = if (isLocal) null else context.getMessage<VdfProofBody>()
        val body = message?.body ?: Main.gson.fromJson<VdfProofBody>(context.body(), VdfProofBody::class.java)
        val proof = body.proof
        val block = body.block

        val receivedFrom = if (isLocal) "Locally" else ip
        val messageToSend = message ?: nodeNetwork.createVdfProofMessage(proof, block)

        Logger.consensus("VDF proof has been received [$receivedFrom] (proof = ${DigestUtils.sha256Hex(proof)})")

        if (block < blockChain.chain.size) {
            Logger.info("VDF proof is old and we're not checking it...")
            return
        }

        if (blockChain.updateVdf(proof, block)) {
            Logger.consensus("Broadcasting proof")
            nodeNetwork.broadcast("/vdf", messageToSend)
            Logger.consensus("Sending proof ${DigestUtils.sha256Hex(proof)}")
        } else Logger.error("updateVdf returned false!")
    }
}