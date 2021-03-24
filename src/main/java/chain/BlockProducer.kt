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

    private val initialDifficulty = configuration.initialDifficulty
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
        difficulty = initialDifficulty,
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
    fun createBlock(previousBlock: Block, vdfProof: String = "", votes: Int = 0, isSkipBlock: Boolean = false): Block = Block(
        epoch = currentState.epoch,
        slot = currentState.slot,
        difficulty = initialDifficulty,
        timestamp = if (isSkipBlock) previousBlock.timestamp else currentTime,
        committeeIndex = currentState.committeeIndex,
        vdfProof = vdfProof,
        votes = votes,
        blockProducer = if (isSkipBlock) "" else DigestUtils.sha256Hex(crypto.publicKey),
        validatorChanges = currentState.inclusionChanges.toMap(),
        precedentHash = previousBlock.hash
    )

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