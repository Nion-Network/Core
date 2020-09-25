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
            currentValidators.add(publicKey)
            if (currentValidators.size >= configuration.validatorsCount) {
                val genesisBlock = blockProducer.genesisBlock
                chainManager.addBlock(genesisBlock)
                println("Running vdf!")
                vdf.runVDF(genesisBlock.difficulty, genesisBlock.hash, genesisBlock.epoch)
            }
        }
    }

    fun requestInclusion() {
        Logger.debug("Requesting inclusion...")
        val message = nodeNetwork.createValidatorInclusionRequestMessage(applicationManager.crypto.publicKey)
        nodeNetwork.broadcast("/include", message)
    }

}