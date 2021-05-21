package manager

import communication.TransmissionType
import data.*
import logging.Logger
import org.apache.commons.codec.digest.DigestUtils
import utils.runAfter
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

    // <BlockHash, Stats..>
    val latestNetworkStatistics = mutableListOf<DockerStatistics>()

    private fun generateClusters(k: Int, maxIterations: Int, validators: Collection<String>, lastBlock: Block): Map<String, List<String>> {
        val random = Random(lastBlock.seed)
        var centroids = validators.shuffled(random).take(k)
        val clusters = mutableMapOf<String, MutableMap<String, Int>>()
        var lastState = clusters

        for (iteration in 0 until maxIterations) {
            validators.shuffled(random).forEach { validator ->
                val distances = centroids.map { it to random.nextInt() }
                val chosenCentroid = distances.minBy { it.second }!! // Not possible for validator collection to be empty.
                val publicKey = chosenCentroid.first
                val distance = chosenCentroid.second
                clusters.computeIfAbsent(publicKey) { mutableMapOf() }[validator] = distance
            }
            if (lastState == clusters) break
            lastState = clusters
            clusters.clear()

            centroids = clusters.values.mapNotNull { distances ->
                val averageDistance = distances.values.average()
                distances.minByOrNull { (_, distance) -> abs(averageDistance - distance) }?.key
            }
        }
        return clusters.entries.associate { it.key to it.value.keys.toList() }
    }

    fun prepareForStatistics(task: ChainTask, validators: Collection<String>, lastBlock: Block) {
        val clusters = generateClusters(configuration.clusterCount, configuration.maxIterations, validators, lastBlock)
        val myPublicKey = crypto.publicKey
        val isRepresentative = clusters.keys.contains(myPublicKey)

        if (networkManager.isTrustedNode) dashboard.logCluster(lastBlock, task, clusters)

        if (isRepresentative) runAfter((configuration.slotDuration) / 4) {
            latestNetworkStatistics.add(dockerManager.latestStatistics)
            val message = networkManager.generateMessage(latestNetworkStatistics.toList())
            val node = knownNodes[task.blockProducer] ?: return@runAfter
            networkManager.sendUDP(Endpoint.RepresentativeStatistics, message, TransmissionType.Unicast, node)
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
        val message = networkManager.generateMessage(latestStatistics)
        Logger.info("Reporting statistics to our cluster representative! ${DigestUtils.sha256Hex(destinationKey)}")
        networkManager.sendUDP(Endpoint.NodeStatistics, message, TransmissionType.Unicast, node)
    }

}