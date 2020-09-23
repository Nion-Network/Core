package state

import common.Block
import configuration.Configuration
import utils.Crypto
import utils.VDF

/**
 * Created by Mihael Valentin Berčič
 * on 23/09/2020 at 19:01
 * using IntelliJ IDEA
 */


data class State(var currentEpoch: Int, var ourSlot: Int, var committeeIndex: Int, var currentDifficulty: Int)

class BlockProducer(private val crypto: Crypto, private val vdf: VDF, private val configuration: Configuration, private val currentState: State) {

    private val currentTime: Long get() = System.currentTimeMillis()

    val genesisBlock: Block get() = Block(0, 0, 0, 0, currentTime, 0)

    fun createBlock(previousBlock: Block): Block = Block(
            epoch = currentState.currentEpoch,
            slot = currentState.ourSlot,
            height = previousBlock.height + 1,
            difficulty = currentState.currentDifficulty,
            timestamp = currentTime,
            committeeIndex = currentState.committeeIndex,
            precedentHash = previousBlock.hash
    )
}