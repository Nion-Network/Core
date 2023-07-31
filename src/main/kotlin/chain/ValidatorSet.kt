package chain

import chain.data.Block
import chain.data.ChainTask
import chain.data.SlotDuty
import logging.Logger
import network.data.Node
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Created by Mihael Valentin Berčič
 * on 16/11/2021 at 16:21
 * using IntelliJ IDEA
 */
class ValidatorSet(private val localNode: Node, isTrustedNode: Boolean) {

    private val lock = ReentrantLock(true)
    private val validators = mutableSetOf<String>()
    private val scheduledChanges = ConcurrentHashMap<String, Boolean>()

    val validatorCount get() = lock.withLock { validators.size }
    val activeValidators: List<String> get() = lock.withLock { validators.toList() }

    var isInValidatorSet = isTrustedNode
        private set


    init {
        if (isTrustedNode) {
            scheduledChanges[localNode.publicKey] = true
            Logger.info("We're the trusted node...")
        }
    }

    /** Returns shuffled validator set using passed [random]. */
    fun shuffled(random: kotlin.random.Random): List<String> {
        return lock.withLock { validators.shuffled(random) }
    }

    /** Goes over all blocks added and validator set is modified based on changes that happened in those blocks. */
    fun inclusionChanges(vararg blocks: Block) {
        lock.withLock {
            blocks.forEach { block ->
                block.validatorChanges.forEach { (publicKey, wasAdded) ->
                    if (wasAdded) validators.add(publicKey)
                    else validators.remove(publicKey)
                    scheduledChanges.remove(publicKey)
                    if (publicKey == localNode.publicKey) isInValidatorSet = wasAdded
                }
            }
        }
    }

    fun clearScheduledChanges() {
        scheduledChanges.clear()
    }

    /** Schedules a change of validator set to be used in the next block. */
    fun scheduleChange(publicKey: String, add: Boolean) {
        scheduledChanges[publicKey] = add
        Logger.info("[Waiting: ${scheduledChanges.size}] Scheduled inclusion change of ${publicKey.substring(30..35)} to $add.")
    }

    /** Returns all scheduled changes. */
    fun getScheduledChanges(): Map<String, Boolean> {
        return scheduledChanges
    }

    /** Computes the task for the next action creation using current block information. */
    fun computeNextTask(block: Block, committeeSize: Int): ChainTask {
        return lock.withLock {
            val seed = block.seed
            val random = Random(seed)
            val ourKey = localNode.publicKey

            val validatorSetCopy = validators.sorted().shuffled(random).toMutableList()
            val blockProducerNode = validatorSetCopy.removeAt(0)
            val committee = validatorSetCopy.take(committeeSize)
            val quorum = if (block.slot % 10 == 0L) validatorSetCopy.take(committeeSize) else emptyList()

            val ourRole = when {
                blockProducerNode == ourKey -> SlotDuty.Producer
                committee.contains(ourKey) -> SlotDuty.Committee
                quorum.contains(ourKey) -> SlotDuty.Quorum
                else -> SlotDuty.Validator
            }
            ChainTask(ourRole, blockProducerNode, committee, quorum)
        }
    }
}