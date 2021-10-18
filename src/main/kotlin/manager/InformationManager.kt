package manager

import communication.Message
import communication.TransmissionType
import data.Block
import data.ChainTask
import data.DockerStatistics
import data.Endpoint
import logging.Logger
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256
import utils.runAfter
import java.lang.Integer.max
import kotlin.math.abs
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 05/12/2020 at 10:57
 * using IntelliJ IDEA
 *
 * Class is used for handling statistics and any networking regarding system statistics.
 */
class InformationManager(private val dht: DistributedHashTable, private val networkManager: NetworkManager) {

    private val crypto = networkManager.crypto
    private val knownNodes = networkManager.knownNodes
    private val dashboard = networkManager.dashboard
    private val dockerManager = networkManager.docker
    private val configuration = networkManager.configuration

    val latestNetworkStatistics = mutableSetOf<DockerStatistics>()

    /** Reports our statistics to either the producer or our cluster representative. */
    fun prepareForStatistics(task: ChainTask, validators: Collection<String>, lastBlock: Block) {
        val clusterCount = max(1, validators.size / configuration.nodesPerCluster)
        val clusters = generateClusters(task, clusterCount, configuration.maxIterations, validators, lastBlock)
        val myPublicKey = crypto.publicKey
        val isRepresentative = clusters.keys.contains(myPublicKey)

        if (networkManager.isTrustedNode) dashboard.logCluster(lastBlock, task, clusters)

        if (task.blockProducer == crypto.publicKey) return

        val statistics = dockerManager.getLatestStatistics(lastBlock)
        latestNetworkStatistics.add(statistics)

        dashboard.vdfInformation("Stats: $isRepresentative")
        if (isRepresentative) runAfter((configuration.slotDuration) / 4) {
            dht.searchFor(task.blockProducer) {
                networkManager.sendUDP(Endpoint.RepresentativeStatistics, latestNetworkStatistics.toList(), TransmissionType.Unicast, it)
                Logger.info("Sending info to ${knownNodes[task.blockProducer]?.ip} with ${latestNetworkStatistics.size}")
            }
        } else {
            val myRepresentative = clusters.entries.firstOrNull { (_, nodes) -> nodes.contains(myPublicKey) }?.key
            dashboard.vdfInformation("Not representative: $myRepresentative")
            if (myRepresentative != null) dht.searchFor(myRepresentative) {
                Logger.info("Reporting statistics to our cluster representative! ${sha256(myRepresentative).asHex}")
                networkManager.sendUDP(Endpoint.NodeStatistics, statistics, TransmissionType.Unicast, it)
            }
        }
    }

    /** Adds docker statistics sent by other nodes to [latestNetworkStatistics]. */
    fun dockerStatisticsReceived(message: Message<DockerStatistics>) {
        latestNetworkStatistics.add(message.body)
        Logger.info("Docker stats received... Adding to the latest list: ${latestNetworkStatistics.size}")
    }

    /** Adds multiple statistics received by a cluster representative to [latestNetworkStatistics]. */
    fun representativeStatisticsReceived(message: Message<Array<DockerStatistics>>) {
        latestNetworkStatistics.addAll(message.body)
        Logger.info("Representative stats received... Adding to the latest list: ${latestNetworkStatistics.size}")
    }

    /** Generates clusters based on k-means algorithm. */
    private fun generateClusters(task: ChainTask, k: Int, maxIterations: Int, currentValidators: Collection<String>, lastBlock: Block): Map<String, List<String>> {
        val random = Random(lastBlock.seed)
        val validators = currentValidators.minus(task.blockProducer)
        var centroids = validators.shuffled(random).take(k - 1).plus(task.blockProducer)
        val clusters = mutableMapOf<String, MutableMap<String, Int>>()

        for (iteration in 0 until maxIterations) {
            clusters.clear()
            validators.minus(centroids).shuffled(random).forEach { validator ->
                val distances = centroids.map { it to random.nextInt() }
                val chosenCentroid = distances.minByOrNull { it.second }!! // Not possible for validator collection to be empty.
                val publicKey = chosenCentroid.first
                val distance = chosenCentroid.second
                clusters.computeIfAbsent(publicKey) { mutableMapOf() }[validator] = distance
            }
            centroids = clusters.values.mapNotNull { distances ->
                val averageDistance = distances.values.average()
                distances.minByOrNull { (_, distance) -> abs(averageDistance - distance) }?.key
            }
        }
        return clusters.entries.associate { it.key to it.value.keys.toList() }
    }

}