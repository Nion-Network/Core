package blockchain

import manager.ApplicationManager

/**
 * Created by Mihael Valentin Berčič
 * on 24/09/2020 at 14:08
 * using IntelliJ IDEA
 */

class BlockProducer(private val applicationManager: ApplicationManager) {

    private val currentState = applicationManager.currentState
    private val currentTime: Long get() = System.currentTimeMillis()

    val String.genesisBlock: Block get() = Block(0, 0, applicationManager.configuration.initialDifficulty, currentTime, 0, validatorChanges = applicationManager.validatorSetChanges, vdfProof = this)

    fun createBlock(previousBlock: Block, vdfProof: String = ""): Block = Block(
            epoch = currentState.currentEpoch,
            slot = currentState.ourSlot,
            difficulty = currentState.currentDifficulty,
            timestamp = System.currentTimeMillis(),
            committeeIndex = currentState.committeeIndex,
            vdfProof = vdfProof,
            validatorChanges = applicationManager.validatorSetChanges,
            precedentHash = previousBlock.hash
    )
}