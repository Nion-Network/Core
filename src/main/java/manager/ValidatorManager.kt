package manager

import data.InclusionRequest
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

    fun inclusionRequest(message: Message<InclusionRequest>) {
        val publicKey = message.publicKey
        val inclusionRequest = message.body

        val isSameEpoch = inclusionRequest.currentEpoch == currentState.currentEpoch
        val isSameSlot = inclusionRequest.currentSlot == currentState.currentSlot
        val isSynced = isSameEpoch && isSameSlot

        println("Inclusion request received with: Current[${currentState.currentEpoch}][${currentState.currentSlot}] vs Inc[${inclusionRequest.currentEpoch}][${inclusionRequest.currentSlot}]")
        if (!isSynced) return
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

    fun requestInclusion(producerKey: String) {
        val inclusionRequest = InclusionRequest(currentState.currentEpoch, currentState.currentSlot, networkManager.crypto.publicKey)
        Logger.debug("Requesting inclusion with ${inclusionRequest.currentEpoch} [${inclusionRequest.currentSlot}]...")
        val message = networkManager.generateMessage(inclusionRequest)
        networkManager.apply {
            dht searchFor producerKey
            knownNodes[producerKey]?.sendMessage("/include", message)
        }
    }

}