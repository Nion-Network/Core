package manager

import data.Message
import logging.Logger

class ValidatorManager(private val networkManager: NetworkManager, private val chainManager: ChainManager) {

    private val configuration = networkManager.configuration
    private val currentState = networkManager.currentState
    private val vdfManager = networkManager.vdf

    private val minValidatorsCount = configuration.validatorsCount
    private val initialDifficulty = configuration.initialDifficulty
    private val currentValidators = currentState.currentValidators
    private val inclusionChanges = currentState.inclusionChanges
    private val blockProducer = chainManager.blockProducer

    fun inclusionRequest(message: Message<String>) {
        val publicKey = message.publicKey

        Logger.consensus("Received one inclusion request... ")

        currentState.inclusionChanges[publicKey] = true
        networkManager.broadcast("/include", message)

        val currentValidatorsSize = currentValidators.size
        val newValidators = inclusionChanges.filter { it.value }.count()

        val isEnoughIncluded = currentValidatorsSize + newValidators >= minValidatorsCount
        val isChainEmpty = chainManager.isChainEmpty
        if (isChainEmpty && configuration.isTrustedNode && isEnoughIncluded) {
            val vdfProof = vdfManager.findProof(initialDifficulty, "FFFF")
            val block = blockProducer.genesisBlock(vdfProof)
            Logger.debug("Broadcasting genesis block...")
            networkManager.knownNodes.forEach { Logger.info("Sending genesis block to: ${it.value.ip}") }
            networkManager.broadcast("/block", networkManager.generateMessage(block))
            chainManager.addBlock(block)
        }
    }

    fun requestInclusion() {
        Logger.debug("Requesting inclusion...")
        val message = networkManager.generateMessage(networkManager.crypto.publicKey)
        networkManager.broadcast("/include", message)
    }

}