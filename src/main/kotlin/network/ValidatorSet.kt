package network

import data.Configuration
import data.chain.Block
import data.chain.ChainTask
import data.chain.SlotDuty
import data.network.Node
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


    /** Generates clusters based on k-means algorithm. */
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
                    val chosenCentroid = distances.minByOrNull { it.second }!! // Not possible for validator collection to be empty.
                    val publicKey = chosenCentroid.first
                    val distance = chosenCentroid.second
                    clusters.computeIfAbsent(publicKey) { mutableMapOf() }[validator] = distance
                }
                centroids = clusters.values.mapNotNull { distances ->
                    val averageDistance = distances.values.average()
                    distances.minByOrNull { (_, distance) -> abs(averageDistance - distance) }?.key
                }.toSet()
            }
            clusters.map { (representative, nodes) -> Cluster(representative, nodes.keys) }
        }
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

data class Cluster(val representative: String, val nodes: Collection<String>)