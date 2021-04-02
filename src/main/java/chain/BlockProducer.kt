package chain

import data.Block
import data.Configuration
import data.State
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto

/**
 * Created by Mihael Valentin Berčič
 * on 24/09/2020 at 14:08
 * using IntelliJ IDEA
 */
class BlockProducer(private val crypto: Crypto, private val configuration: Configuration, private val currentState: State) {

    private val currentTime: Long get() = System.currentTimeMillis()

    /**
     * Computes genesis (first, initial) block for the blockchain.
     *
     * @param vdfProof Initial difficulty VDF proof of "FFFF".
     * @return Genesis block that is used to start the chain.
     */
    fun genesisBlock(vdfProof: String): Block = Block(
        epoch = 0,
        slot = 0,
        difficulty = configuration.initialDifficulty,
        timestamp = currentTime,
        committeeIndex = 0,
        votes = 0,
        blockProducer = DigestUtils.sha256Hex(crypto.publicKey),
        validatorChanges = currentState.inclusionChanges.toMap(),
        vdfProof = vdfProof
    )

    /**
     * Computes the next block in chain.
     *
     * @param previousBlock Last chain block that is used for the next block computation [ => chain].
     * @param vdfProof VDF Proof computed from the previous block.
     * @param votes Votes that have been sourced for the given block.
     * @return Newly computed block.
     */
    fun createBlock(previousBlock: Block, vdfProof: String = ""): Block = Block(
        epoch = currentState.epoch,
        slot = currentState.slot,
        difficulty = configuration.initialDifficulty,
        timestamp = currentTime,
        committeeIndex = currentState.committeeIndex,
        vdfProof = vdfProof,
        blockProducer = DigestUtils.sha256Hex(crypto.publicKey),
        validatorChanges = currentState.inclusionChanges.toMap(),
        precedentHash = previousBlock.hash
    )

    fun createSkipBlock(previousBlock: Block): Block {
        val epoch = currentState.epoch
        val slot = currentState.slot
        val precedentHash = previousBlock.hash
        return Block(
            epoch, slot, configuration.initialDifficulty,
            timestamp = previousBlock.timestamp + configuration.slotDuration,
            committeeIndex = currentState.committeeIndex,
            blockProducer = "SkipBlock",
            validatorChanges = currentState.inclusionChanges.toMap(),
            precedentHash = precedentHash,
            hash = DigestUtils.sha256Hex("$epoch-SKIP-$slot$precedentHash")
        )

    }

    fun adjustDifficulty(previousBlock: Block): Int {
        return 10000

        val deltaT: Long = System.currentTimeMillis() - previousBlock.timestamp
        val ratio: Double = deltaT / configuration.slotDuration.toDouble()
        var difficulty: Double = 0.0
        when {
            ratio > 0 -> {
                difficulty = previousBlock.difficulty + (previousBlock.difficulty * (1 - ratio))
                if (difficulty <= 0) {
                    return 100
                }
                return difficulty.toInt()
            }
            ratio < 0 -> {
                difficulty = previousBlock.difficulty - (previousBlock.difficulty * (1 - ratio))
                if (difficulty <= 0) {
                    return 100
                }
                return difficulty.toInt()
            }
            else -> {
                return previousBlock.difficulty
            }
        }
    }
}