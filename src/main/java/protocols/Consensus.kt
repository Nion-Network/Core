package protocols

import abstraction.Message
import common.BlockChain
import io.javalin.http.Context
import logging.Logger
import messages.RequestInclusionBody
import network.NodeNetwork
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import utils.bodyAsMessage
import utils.fromJsonTo

class Consensus (private val nodeNetwork: NodeNetwork, private val crypto: Crypto, private val blockChain: BlockChain
){
    fun validatorSetInclusionRequest (context: Context){
        val message: Message = context.bodyAsMessage
        val inclusionBody: RequestInclusionBody = message.body fromJsonTo RequestInclusionBody::class.java
        blockChain.addInclusionRequest(inclusionBody.publicKey)
        Logger.consensus("Received inclusion request from: ${DigestUtils.sha256Hex(inclusionBody.publicKey)}")
    }

    fun requestInclusion (publicKey: String){
        val message:Message = nodeNetwork.createValidatorInclusionRequestMessage(publicKey)
        nodeNetwork.pickRandomNodes(5).forEach{it.sendMessage("/include", message)}
    }
}