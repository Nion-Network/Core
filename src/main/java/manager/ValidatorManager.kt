package manager

import abstraction.Message
import io.javalin.http.Context
import logging.Logger
import messages.RequestInclusionBody
import utils.getMessage

class ValidatorManager(private val applicationManager: ApplicationManager) {

    private val nodeNetwork by lazy { applicationManager.networkManager.nodeNetwork }
    private val minValidatorsCount by lazy { applicationManager.configuration.validatorsCount }
    private val initialDifficulty by lazy { applicationManager.configuration.initialDifficulty }

    fun validatorSetInclusionRequest(context: Context) {
        val message: Message<RequestInclusionBody> = context.getMessage()
        val publicKey = message.publicKey

        Logger.consensus("Received one inclusion request... ") // from: ${DigestUtils.sha256Hex(publicKey)}")

        applicationManager.apply {
            validatorSetChanges[publicKey] = true

            val currentValidatorsSize = currentValidators.size
            val newValidators = validatorSetChanges.filter { it.value }.count()

            if (currentValidatorsSize + newValidators >= minValidatorsCount && chainManager.isChainEmpty) {
                blockProducer.apply {
                    val vdfProof = applicationManager.vdfManager.findProof(initialDifficulty, "FFFF", 0)
                    val block = genesisBlock(vdfProof)
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