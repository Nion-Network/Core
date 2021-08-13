package manager

import communication.Message
import communication.TransmissionType
import data.Block
import data.ChainTask
import data.DockerStatistics
import data.Endpoint
import logging.Logger
import org.apache.commons.codec.digest.DigestUtils
import utils.runAfter
import java.lang.Integer.max
import kotlin.math.abs
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 05/12/2020 at 10:57
 * using IntelliJ IDEA
 */
class InformationManager(private val networkManager: NetworkManager) {

    private val crypto = networkManager.crypto
    private val knownNodes = networkManager.knownNodes
    private val dashboard = networkManager.dashboard
    private val dockerManager = networkManager.docker
    private val configuration = networkManager.configuration

    val latestNetworkStatistics = mutableListOf<DockerStatistics>()

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

    fun prepareForStatistics(task: ChainTask, validators: Collection<String>, lastBlock: Block) {
        val clusterCount = max(1, validators.size / configuration.nodesPerCluster)
        val clusters = generateClusters(task, clusterCount, configuration.maxIterations, validators, lastBlock)
        val myPublicKey = crypto.publicKey
        val isRepresentative = clusters.keys.contains(myPublicKey)

        if (networkManager.isTrustedNode) dashboard.logCluster(lastBlock, task, clusters)

        if (task.blockProducer == crypto.publicKey) return

        if (isRepresentative) runAfter((configuration.slotDuration) / 3) {
            latestNetworkStatistics.add(dockerManager.latestStatistics)
            val node = knownNodes[task.blockProducer] ?: return@runAfter
            networkManager.sendUDP(Endpoint.RepresentativeStatistics, latestNetworkStatistics.toList(), TransmissionType.Unicast, node)
            Logger.info("Sending info to ${knownNodes[task.blockProducer]?.ip} with ${latestNetworkStatistics.size}")
        } else {
            val myRepresentative = clusters.entries.firstOrNull { (_, nodes) -> nodes.contains(myPublicKey) }?.key
            if (myRepresentative != null) reportStatistics(myRepresentative)
        }
    }

    fun dockerStatisticsReceived(message: Message<DockerStatistics>) {
        latestNetworkStatistics.add(message.body)
        Logger.info("Docker stats received... Adding to the latest list: ${latestNetworkStatistics.size}")
    }

    fun representativeStatisticsReceived(message: Message<Array<DockerStatistics>>) {
        latestNetworkStatistics.addAll(message.body)
        Logger.info("Representative stats received... Adding to the latest list: ${latestNetworkStatistics.size}")
    }

    private fun reportStatistics(destinationKey: String) {
        val node = knownNodes[destinationKey] ?: return
        val latestStatistics = dockerManager.latestStatistics
        Logger.info("Reporting statistics to our cluster representative! ${DigestUtils.sha256Hex(destinationKey)}")
        networkManager.sendUDP(Endpoint.NodeStatistics, latestStatistics, TransmissionType.Unicast, node)
    }

}