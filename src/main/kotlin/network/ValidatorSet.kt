package network

import data.chain.Block
import data.chain.ChainTask
import data.chain.SlotDuty
import data.network.Node
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
    private val scheduledChanges = ConcurrentHashMap<String, Boolean>()
    private val validators = mutableSetOf<String>()
    var isInValidatorSet = isTrustedNode
        private set

    init {
        if (isTrustedNode) scheduledChanges[localNode.publicKey] = true
    }

    val validatorCount get() = lock.withLock { validators.size }

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

    fun scheduleChange(publicKey: String, add: Boolean) {
        scheduledChanges[publicKey] = add
    }

    fun getScheduledChanges(): Map<String, Boolean> {
        return scheduledChanges
    }

    /** Computes the task for the next block creation using current block information. */
    fun computeNextTask(block: Block, committeeSize: Int): ChainTask {
        return lock.withLock {
            val seed = block.seed
            val random = Random(seed)
            val ourKey = localNode.publicKey

            val validatorSetCopy = validators.shuffled(random).toMutableList()
            val blockProducerNode = validatorSetCopy[0].apply { validatorSetCopy.remove(this) }
            val committee = validatorSetCopy.take(committeeSize)

            val ourRole = when {
                blockProducerNode == ourKey -> SlotDuty.PRODUCER
                committee.contains(ourKey) -> SlotDuty.COMMITTEE
                else -> SlotDuty.VALIDATOR
            }
            ChainTask(ourRole, blockProducerNode, committee)
        }
    }
}