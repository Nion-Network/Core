package manager

import data.EndPoint
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
    private val blockProducer = chainManager.blockProducer

    fun inclusionRequest(message: Message<InclusionRequest>) {
        val publicKey = message.publicKey
        val inclusionRequest = message.body
        val canBeIncluded = chainManager.canBeIncluded(inclusionRequest)

        if (!canBeIncluded) return
        Logger.consensus("Inclusion request received with: Current[${currentState.epoch}][${currentState.slot}] vs Inc[${inclusionRequest.currentEpoch}][${inclusionRequest.currentSlot}]")

        currentState.inclusionChanges[publicKey] = true
        networkManager.broadcast(EndPoint.Include, message)

        val currentValidatorsSize = currentValidators.size
        val newValidators = currentState.inclusionChanges.filter { it.value }.count()

        val isEnoughIncluded = currentValidatorsSize + newValidators >= minValidatorsCount
        val isChainEmpty = chainManager.isChainEmpty
        if (networkManager.isTrustedNode && isChainEmpty && isEnoughIncluded) {
            val vdfProof = vdfManager.findProof(initialDifficulty, "FFFF")
            val block = blockProducer.genesisBlock(vdfProof)
            Logger.debug("Broadcasting genesis block...")
            networkManager.knownNodes.forEach { Logger.info("Sending genesis block to: ${it.value.ip}") }
            networkManager.broadcast(EndPoint.BlockReceived, networkManager.generateMessage(block))
        }
    }

    fun requestInclusion(producerKey: String) {
        val inclusionRequest = InclusionRequest(currentState.epoch, currentState.slot, networkManager.crypto.publicKey)
        Logger.debug("Requesting inclusion with ${inclusionRequest.currentEpoch} [${inclusionRequest.currentSlot}]...")
        val message = networkManager.generateMessage(inclusionRequest)
        networkManager.apply {
            dht searchFor producerKey
            val node = knownNodes[producerKey] ?: return
            sendMessage(node, EndPoint.Include, message)
        }
    }

}