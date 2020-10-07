package manager

import abstraction.Message
import io.javalin.http.Context
import logging.Logger
import messages.RequestInclusionBody
import org.apache.commons.codec.digest.DigestUtils
import utils.getMessage

class ValidatorManager(private val applicationManager: ApplicationManager) {

    private val nodeNetwork by lazy { applicationManager.networkManager.nodeNetwork }

    fun validatorSetInclusionRequest(context: Context) {
        val message: Message<RequestInclusionBody> = context.getMessage()
        val publicKey = message.publicKey
        Logger.consensus("Received inclusion request from: ${DigestUtils.sha256Hex(publicKey)}")
        applicationManager.apply {
            validatorSetChanges[publicKey] = true
            val currentValidatorsSize = currentValidators.size
            val newValidators = validatorSetChanges.filter { it.value }.count()
            if (currentValidatorsSize + newValidators >= configuration.validatorsCount && chainManager.chain.isEmpty()) {
                blockProducer.apply {
                    val vdf = applicationManager.kotlinVDF.findProof(configuration.initialDifficulty, "FFFF", 0)

                    val block = vdf.genesisBlock
                    Logger.debug("Broadcasting genesis block...")
                    nodeNetwork.broadcast("/block", nodeNetwork.createNewBlockMessage(block))
                    chainManager.addBlock(block)
                }
            }
        }
    }

    fun requestInclusion() {
        Logger.debug("Requesting inclusion...")
        val message = nodeNetwork.createValidatorInclusionRequestMessage(applicationManager.crypto.publicKey)
        nodeNetwork.broadcast("/include", message)
    }

}