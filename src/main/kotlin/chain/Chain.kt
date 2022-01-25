package chain

import chain.data.Block
import logging.Logger
import utils.tryWithLock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Created by Mihael Valentin Berčič
 * on 16/11/2021 at 16:21
 * using IntelliJ IDEA
 */
class Chain(private val verifiableDelay: VerifiableDelay, private val initialDifficulty: Int, private val committeeSize: Int) {

    private val lock = ReentrantLock(true)
    private val blocks = mutableListOf<Block>()

    /** Returns the last block in the chain. */
    fun getLastBlock(): Block? {
        return lock.withLock { blocks.lastOrNull() }
    }

    /** Returns max 100 blocks [from slot][fromSlot].*/
    fun getLastBlocks(fromSlot: Long): List<Block> {
        return lock.withLock { blocks.takeLastWhile { it.slot > fromSlot } }.take(100)
    }

    /** Attempts to add each block one by one to the chain. */
    fun addBlocks(vararg newBlocks: Block): Boolean {
        newBlocks.forEach { nextBlock ->
            val lastBlock = getLastBlock()
            val lastHash = lastBlock?.hash ?: "FFFF".toByteArray()
            val difficulty = lastBlock?.difficulty ?: initialDifficulty
            val isLegitimate = nextBlock.slot == (lastBlock?.slot ?: 0) + 1 // verifiableDelay.verifyProof(lastHash, difficulty, nextBlock.vdfProof)
            if (!isLegitimate) {
                Logger.trace("Proof is not legitimate for block ${nextBlock.slot}!")
                Logger.info("Last hash: $lastHash")
                Logger.info("Last block: ${lastBlock?.slot}")
                Logger.info("New hash: ${nextBlock.hash}")
                Logger.info("Precedent hash: ${nextBlock.precedentHash}")
                Logger.info("Precedent vs lastBlock ${nextBlock.precedentHash.contentEquals(lastHash)}")
                return false
            }
            lock.tryWithLock { blocks.add(nextBlock) }
            Logger.chain("Block[${nextBlock.votes}/$committeeSize] added [${nextBlock.slot}].")
        }
        return true
    }

}