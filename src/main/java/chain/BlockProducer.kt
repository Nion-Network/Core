package chain

import data.Block
import data.Configuration
import logging.Logger
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Mihael Valentin Berčič
 * on 24/09/2020 at 14:08
 * using IntelliJ IDEA
 */
class BlockProducer(private val crypto: Crypto, private val configuration: Configuration, isTrustedNode: Boolean) {

    private val currentTime: Long get() = System.currentTimeMillis()

    val inclusionChanges: MutableMap<String, Boolean> = ConcurrentHashMap<String, Boolean>().apply {
        if (isTrustedNode) this[crypto.publicKey] = true
    }

    val currentValidators: MutableSet<String> = ConcurrentHashMap.newKeySet()

    var isIncluded = isTrustedNode
        private set

    /**
     * Computes genesis (first, initial) block for the blockchain.
     *
     * @param vdfProof Initial difficulty VDF proof of "FFFF".
     * @return Genesis block that is used to start the chain.
     */
    fun genesisBlock(vdfProof: String): Block = Block(
        slot = 1,
        difficulty = configuration.initialDifficulty,
        timestamp = currentTime,
        committeeIndex = 0,
        blockProducer = DigestUtils.sha256Hex(crypto.publicKey),
        validatorChanges = inclusionChanges.toMap(),
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
    fun createBlock(previousBlock: Block, vdfProof: String = "", slot: Int, committeeIndex: Int = 0): Block = Block(
        slot,
        difficulty = configuration.initialDifficulty,
        timestamp = currentTime,
        committeeIndex = committeeIndex,
        vdfProof = vdfProof,
        blockProducer = DigestUtils.sha256Hex(crypto.publicKey),
        validatorChanges = inclusionChanges.toMap(),
        precedentHash = previousBlock.hash
    )

    fun createSkipBlock(previousBlock: Block): Block = Block(
        slot = previousBlock.slot + 1,
        difficulty = configuration.initialDifficulty,
        timestamp = previousBlock.timestamp + configuration.slotDuration,
        committeeIndex = previousBlock.committeeIndex,
        blockProducer = "SKIP_BLOCK",
        validatorChanges = inclusionChanges.toMap(),
        precedentHash = previousBlock.hash,
        hash = DigestUtils.sha256Hex("${previousBlock.slot}-SKIP-${previousBlock.precedentHash}")
    )

    fun validatorChange(publicKey: String, isAdded: Boolean) {
        if (publicKey == crypto.publicKey) isIncluded = isAdded
        if (isAdded) currentValidators.add(publicKey) else currentValidators.remove(publicKey)
        inclusionChanges.remove(publicKey)
        Logger.info("${publicKey.subSequence(120, 140)} has been ${if (isAdded) "added" else "removed"}. Total: ${currentValidators.size}")
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