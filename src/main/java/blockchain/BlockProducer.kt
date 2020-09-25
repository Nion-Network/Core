package blockchain

import manager.ApplicationManager

/**
 * Created by Mihael Valentin Berčič
 * on 24/09/2020 at 14:08
 * using IntelliJ IDEA
 */

class BlockProducer(applicationManager: ApplicationManager) {

    private val currentState = applicationManager.currentState
    private val currentTime: Long get() = System.currentTimeMillis()

    val genesisBlock: Block get() = Block(0, 0, 0, currentTime, 0)

    fun createBlock(previousBlock: Block): Block = Block(
            epoch = currentState.currentEpoch,
            slot = currentState.ourSlot,
            difficulty = currentState.currentDifficulty,
            timestamp = currentTime,
            committeeIndex = currentState.committeeIndex,
            precedentHash = previousBlock.hash
    )
}