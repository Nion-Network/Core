package chain

import data.Configuration
import data.chain.Block
import data.chain.ChainTask
import data.chain.SlotDuty
import data.communication.InclusionRequest
import logging.Logger
import network.DistributedHashTable
import utils.Crypto
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 25/10/2021 at 09:24
 * using IntelliJ IDEA
 */
class ChainHistory(
    private val dht: DistributedHashTable,
    private val crypto: Crypto,
    private val configuration: Configuration,
    private val chainBuilder: ChainBuilder,
    private val isTrustedNode: Boolean
) {

    var isInValidatorSet = isTrustedNode
        private set

    private val validatorSet = Collections.synchronizedSet(mutableSetOf<String>())
    private val chain = Collections.synchronizedList(mutableListOf<Block>())
    private var chainStarted = false

    private val inclusionChanges = ConcurrentHashMap<String, Boolean>()

    init {
        if (isInValidatorSet) inclusionChanges[crypto.publicKey] = true
    }

    fun getLastBlock(): Block? {
        return synchronized(chain) { chain.lastOrNull() }
    }

    fun isChainEmpty(): Boolean {
        return synchronized(chain) { chain.isEmpty() }
    }

    fun getValidatorSize(): Int {
        return synchronized(validatorSet) { validatorSet.size }
    }

    fun getValidators(): List<String> {
        return synchronized(validatorSet) { validatorSet.toList() }
    }

    fun addBlock(block: Block) {
        synchronized(chain) {
            val lastBlock = chain.lastOrNull()
            val lastSlot = lastBlock?.slot ?: 0
            if (block.slot <= lastSlot) return
            if (block.slot > lastSlot + 1) {
                chainBuilder.requestSync()
                return
            }
            chain.add(block)
            block.validatorChanges.forEach(this::modifyValidatorSet)
            // TODO migrations

            Logger.chain("Block [${block.slot}]\t[${block.votes}] has been added.")
            val nextTask = calculateNextTask(block, configuration.committeeSize)
            chainBuilder.executeTask(block, nextTask)
        }
    }

    fun addBlocks(blocks: Array<Block>) {
        synchronized(chain) {
            Logger.trace("Sync in progress...")
            blocks.forEach { block ->
                val lastBlock = chain.lastOrNull()
                val lastSlot = lastBlock?.slot ?: 0
                if (block.slot == lastSlot + 1) {
                    chain.add(block)
                    block.validatorChanges.forEach(this::modifyValidatorSet)
                    Logger.chain("Block [${block.slot}]\t[${block.votes}] has been added.")
                }
            }
            Logger.trace("Sync finished...")
        }
    }

    fun getInclusionChanges(): Map<String, Boolean> {
        return inclusionChanges.toMap()
    }

    /** Computes the task for the next block creation using current block information. */
    fun calculateNextTask(block: Block, committeeSize: Int): ChainTask {
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

    private fun modifyValidatorSet(publicKey: String, hasBeenAdded: Boolean) {
        if (hasBeenAdded) {
            validatorSet.add(publicKey)
            dht.searchFor(publicKey)
        } else validatorSet.remove(publicKey)
        if (publicKey == crypto.publicKey) isInValidatorSet = hasBeenAdded
        inclusionChanges.remove(publicKey)

    }

    fun inclusionRequested(inclusionRequest: InclusionRequest) {
        if (!isInValidatorSet) return
        val currentSlot = getLastBlock()?.slot ?: 0
        if (currentSlot == inclusionRequest.currentSlot) inclusionChanges[inclusionRequest.nodePublicKey] = true

        val newInclusions = inclusionChanges.count { it.value }
        val isEnoughIncluded = getValidatorSize() + newInclusions >= configuration.committeeSize + 1
        if (isTrustedNode && !chainStarted && isChainEmpty() && isEnoughIncluded) {
            chainStarted = true
            chainBuilder.produceGenesisBlock()
        }
    }

    fun getBlocks(fromSlot: Long): List<Block> {
        return chain.dropWhile { it.slot <= fromSlot }
    }

    fun getLastBlocks(amount: Int): List<Block> {
        return chain.takeLast(amount)
    }
}