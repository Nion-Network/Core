package chain

import data.Configuration
import data.chain.Block
import data.chain.ChainTask
import data.chain.SlotDuty
import data.communication.InclusionRequest
import logging.Dashboard
import logging.Logger
import utils.Crypto
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 25/10/2021 at 09:24
 * using IntelliJ IDEA
 */
class ChainHistory(
    private val crypto: Crypto,
    private val configuration: Configuration,
    private val chainBuilder: ChainBuilder,
    isTrustedNode: Boolean
) {

    var isInValidatorSet = isTrustedNode
        private set

    private val validatorLock = ReentrantLock()
    private val validatorSet = mutableSetOf<String>()

    private val chainLock = ReentrantLock()
    private val chain = mutableListOf<Block>()
    private var chainStarted = false

    private val inclusionChanges = ConcurrentHashMap<String, Boolean>()

    init {
        if (isInValidatorSet) inclusionChanges[crypto.publicKey] = true
    }

    fun getLastBlock(): Block? {
        return chainLock.withLock { chain.lastOrNull() }
    }

    fun isChainEmpty(): Boolean {
        return chainLock.withLock { chain.isEmpty() }
    }

    fun getValidatorSize(): Int {
        return validatorLock.withLock { validatorSet.size }
    }

    fun addBlock(block: Block) {
        chainLock.withLock {
            val lastBlock = chain.lastOrNull()
            val lastSlot = lastBlock?.slot ?: 0
            if (block.slot <= lastSlot) return
            if (block.slot > lastSlot + 1) {
                chainBuilder.requestSync()
                Dashboard.vdfInformation("GOTTA SYNC")
                return
            }
            chain.add(block)
            block.validatorChanges.forEach(this::modifyValidatorSet)
            // TODO migrations

            Logger.chain("Block [${block.slot}]\t[${block.votes}] has been added.")
            val nextTask = calculateNextTask(block, configuration.committeeSize)
            chainBuilder.produceBlock(block, nextTask)
        }
    }

    fun addBlocks(blocks: Array<Block>) {
        chainLock.lock()
        Logger.trace("Sync in progress...")
        blocks.forEach { block ->
            val lastBlock = chain.lastOrNull()
            val lastSlot = lastBlock?.slot ?: 0
            if (block.slot != lastSlot + 1) return

            chain.add(block)
            block.validatorChanges.forEach(this::modifyValidatorSet)
            Logger.chain("Block [${block.slot}]\t[${block.votes}] has been added.")
        }
        Logger.trace("Sync finished...")
        chainLock.unlock()
    }

    fun getInclusionChanges(): Map<String, Boolean> {
        return inclusionChanges
    }

    /** Computes the task for the next block creation using current block information. */
    private fun calculateNextTask(block: Block, committeeSize: Int): ChainTask {
        validatorLock.withLock {
            val seed = block.seed
            val random = Random(seed)
            val ourKey = crypto.publicKey

            val validatorSetCopy = validatorSet.shuffled(random).toMutableList()
            val blockProducerNode = validatorSetCopy[0].apply { validatorSetCopy.remove(this) }
            val committee = validatorSetCopy.take(committeeSize)

            val ourRole = when {
                blockProducerNode == ourKey -> SlotDuty.PRODUCER
                committee.contains(ourKey) -> SlotDuty.COMMITTEE
                else -> SlotDuty.VALIDATOR
            }
            return ChainTask(ourRole, blockProducerNode, committee)
        }
    }

    private fun modifyValidatorSet(publicKey: String, hasBeenAdded: Boolean) {
        validatorLock.withLock {
            if (hasBeenAdded) validatorSet.add(publicKey)
            else validatorSet.remove(publicKey)
            if (publicKey == crypto.publicKey) isInValidatorSet = hasBeenAdded
            inclusionChanges.remove(publicKey)
        }
    }

    fun inclusionRequested(inclusionRequest: InclusionRequest) {
        if (!isInValidatorSet) return
        val currentSlot = getLastBlock()?.slot ?: 0
        if (currentSlot == inclusionRequest.currentSlot) inclusionChanges[inclusionRequest.nodePublicKey] = true

        val newInclusions = inclusionChanges.count { it.value }
        val isEnoughIncluded = getValidatorSize() + newInclusions >= configuration.committeeSize + 1
        if (!chainStarted && isChainEmpty() && isEnoughIncluded) {
            chainStarted = true
            chainBuilder.produceGenesisBlock()
        }
    }

    fun getBlocks(fromSlot: Long): List<Block> {
        return chainLock.withLock { chain.dropWhile { it.slot < fromSlot } }
    }

}