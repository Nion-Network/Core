package protocols

import abstraction.Message
import common.BlockChain
import io.javalin.http.Context
import logging.Logger
import messages.RequestInclusionBody
import messages.VdfProofBody
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

    fun received_vdf (context: Context,  blockChain: BlockChain){
        var vdfProofBody : VdfProofBody;
        if(context.ip().toString().equals("127.0.0.1")){
            vdfProofBody = Main.gson.fromJson(context.body(),VdfProofBody::class.java)
        }else {
            val message: Message = context.bodyAsMessage
            vdfProofBody = message.body fromJsonTo VdfProofBody::class.java
        }
        Logger.consensus("Received VDF proof for block ${vdfProofBody.block} : ${DigestUtils.sha256Hex(vdfProofBody.proof)}")
        if(blockChain.updateVdf(vdfProofBody.proof, vdfProofBody.block)){
            val vdfMessage: Message = nodeNetwork.createVdfProofMessage(vdfProofBody.proof, vdfProofBody.block)
            Logger.consensus("Broadcasting proof")
            nodeNetwork.pickRandomNodes(5).forEach{it.sendMessage("/vdf",vdfMessage)}
        }
    }
}