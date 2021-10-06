package chain

import data.Block
import data.Configuration
import logging.Logger
import utils.Crypto
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Mihael Valentin Berčič
 * on 24/09/2020 at 14:08
 * using IntelliJ IDEA
 */
class BlockProducer(private val crypto: Crypto, private val configuration: Configuration, isTrustedNode: Boolean) {

    val currentValidators = mutableSetOf<String>()
    var isIncluded = isTrustedNode
        private set

    val inclusionChanges: MutableMap<String, Boolean> = ConcurrentHashMap<String, Boolean>().apply {
        if (isTrustedNode) this[crypto.publicKey] = true
    }

    /** Computes genesis (first, initial) block using the specified vdf proof. */
    fun genesisBlock(vdfProof: String): Block {
        return Block(
            slot = 1,
            difficulty = configuration.initialDifficulty,
            timestamp = System.currentTimeMillis(),
            committeeIndex = 0,
            blockProducer = sha256(crypto.publicKey).asHex,
            validatorChanges = inclusionChanges.toMap(),
            vdfProof = vdfProof
        )
    }

    /** Computes the next block in chain using previous block information, newly computed vdf proof and the next slot. */
    fun createBlock(previousBlock: Block, vdfProof: String, slot: Long, committeeIndex: Int = 0): Block {
        return Block(
            slot,
            difficulty = configuration.initialDifficulty,
            timestamp = System.currentTimeMillis(),
            committeeIndex = committeeIndex,
            vdfProof = vdfProof,
            blockProducer = sha256(crypto.publicKey).asHex,
            validatorChanges = inclusionChanges.toMap(),
            precedentHash = previousBlock.hash
        )
    }

    /** Computes a special skip block, which has a unique hash and the block producer as all members of the committee make the same block. */
    fun createSkipBlock(previousBlock: Block): Block {
        return Block(
            slot = previousBlock.slot + 1,
            difficulty = configuration.initialDifficulty,
            timestamp = previousBlock.timestamp + configuration.slotDuration,
            committeeIndex = previousBlock.committeeIndex,
            blockProducer = "SKIP_BLOCK",
            validatorChanges = inclusionChanges.toMap(),
            precedentHash = previousBlock.hash,
            hash = sha256("${previousBlock.slot}-SKIP-${previousBlock.precedentHash}").asHex
        )
    }

    /** Performs modifications to the current set of validators as well as inclusion changes map based on [isAdded] flag for the [publicKey] */
    fun validatorChange(publicKey: String, isAdded: Boolean) {
        if (publicKey == crypto.publicKey) isIncluded = isAdded
        if (isAdded) currentValidators.add(publicKey) else currentValidators.remove(publicKey)
        inclusionChanges.remove(publicKey)
        Logger.info("${publicKey.subSequence(120, 140)} has been ${if (isAdded) "added" else "removed"}. Total: ${currentValidators.size}")
    }

    /** Computes the vdf computation difficulty based on the time of the previous block computation. */
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