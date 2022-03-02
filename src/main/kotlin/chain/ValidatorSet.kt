package chain

import Configuration
import chain.data.Block
import chain.data.ChainTask
import chain.data.SlotDuty
import logging.Logger
import chain.data.Cluster
import network.data.Node
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.max

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
        if (isTrustedNode) scheduledChanges[localNode.publicKey] = true
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
        Logger.info("Scheduled inclusion change of ${publicKey.take(5)} to $add.")
    }

    /** Returns all scheduled changes. */
    fun getScheduledChanges(): Map<String, Boolean> {
        return scheduledChanges
    }

    /** Generates clusters based on k-means algorithm. Note: Distance is randomized at the moment. */
    fun generateClusters(blockProducer: String, configuration: Configuration, lastBlock: Block): List<Cluster> {
        return lock.withLock {
            val random = Random(lastBlock.seed)
            val nodes = validators.minus(blockProducer)
            val clusterCount = max(1, nodes.size / configuration.nodesPerCluster)
            var centroids = nodes.shuffled(random).take(clusterCount).toSet()
            val clusters = mutableMapOf<String, MutableMap<String, Int>>()

            for (iteration in 0 until configuration.maxIterations) {
                clusters.clear()
                nodes.minus(centroids).shuffled(random).forEach { validator ->
                    val distances = centroids.map { it to random.nextInt() }
                    val chosenCentroid = distances.minByOrNull { it.second } ?: return@forEach
                    val publicKey = chosenCentroid.first
                    val distance = chosenCentroid.second
                    clusters.computeIfAbsent(publicKey) { mutableMapOf() }[validator] = distance
                }
                // TODO compute differences based on geo-sharding.
                centroids = clusters.values.mapNotNull { distances ->
                    val averageDistance = distances.values.average()
                    distances.minByOrNull { (_, distance) -> abs(averageDistance - distance) }?.key
                }.toSet()
            }
            clusters.map { (representative, nodes) -> Cluster(representative, nodes.keys) }
        }
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

            val ourRole = when {
                blockProducerNode == ourKey -> SlotDuty.PRODUCER
                committee.contains(ourKey) -> SlotDuty.COMMITTEE
                else -> SlotDuty.VALIDATOR
            }
            ChainTask(ourRole, blockProducerNode, committee)
        }
    }
}