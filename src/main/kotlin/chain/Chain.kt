package chain

import chain.data.Block
import logging.Logger
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
        return lock.withLock { blocks.takeLastWhile { it.slot > fromSlot } }.take(100).dropLast(1)
    }

    /** Attempts to add each block one by one to the chain. */
    fun addBlocks(vararg newBlocks: Block): Boolean {
        return lock.withLock {
            newBlocks.forEach { newBlock ->
                val lastBlock = blocks.lastOrNull()
                val lastHash = lastBlock?.hash ?: "FFFF".toByteArray()
                val difficulty = lastBlock?.difficulty ?: initialDifficulty
                val isLegitimate = verifiableDelay.verifyProof(lastHash, difficulty, newBlock.vdfProof)
                if (isLegitimate) {
                    blocks.add(newBlock)
                    Logger.chain("Block[${newBlock.votes}/$committeeSize] added [${newBlock.slot}].")
                } else {
                    if (newBlock.slot > (lastBlock?.slot ?: 0) && !lastHash.contentEquals(newBlock.hash)) {
                        Logger.trace("Proof is not legitimate for block ${newBlock.slot}!")
                        Logger.info("Last hash: $lastHash")
                        Logger.info("Last block: ${lastBlock?.slot}")
                        Logger.info("New hash: ${newBlock.hash}")
                        Logger.info("Precedent hash: ${newBlock.precedentHash}")
                        Logger.info("Precedent vs lastBlock ${newBlock.precedentHash.contentEquals(lastHash)}")
                    }
                    return@withLock false
                }
            }
            return@withLock true
        }
    }

}