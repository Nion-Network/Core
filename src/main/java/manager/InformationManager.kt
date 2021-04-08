package manager

import data.Block
import data.DockerStatistics
import data.EndPoint
import data.Message
import logging.Logger
import org.apache.commons.codec.digest.DigestUtils
import utils.levenshteinDistance
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
        val random = Random(lastBlock.getRandomSeed)
        var centroids = validators.shuffled(random).take(k)
        val clusters = mutableMapOf<String, MutableMap<String, Int>>()
        var lastState = clusters

        for (iteration in 0 until maxIterations) {
            validators.forEach { validator ->
                val distances = centroids.map { it to (it levenshteinDistance validator) }
                val chosenCentroid = distances.minBy { it.second }!! // Not possible for validator collection to be empty.
                val publicKey = chosenCentroid.first
                val distance = chosenCentroid.second
                clusters.computeIfAbsent(publicKey) { mutableMapOf() }[validator] = distance
                //if (networkManager.isTrustedNode) dashboard.logCluster(lastBlock.epoch, validator, publicKey)
            }
            if (lastState == clusters) break
            lastState = clusters
            clusters.clear()

            centroids = clusters.values.mapNotNull { distances ->
                val averageDistance = distances.values.average()
                distances.minBy { (_, distance) -> abs(averageDistance - distance) }?.key
            }
        }
        return clusters.entries.associate { it.key to it.value.keys.toList() }
    }

    fun prepareForStatistics(blockProducer: String, validators: Collection<String>, lastBlock: Block) {
        val clusters = generateClusters(configuration.clusterCount, configuration.maxIterations, validators, lastBlock)
        val myPublicKey = crypto.publicKey
        val isRepresentative = clusters.keys.contains(myPublicKey)

        if (isRepresentative) runAfter((configuration.slotDuration) / 3) {
            latestNetworkStatistics.add(dockerManager.latestStatistics)
            val message = networkManager.generateMessage(latestNetworkStatistics.toList())
            knownNodes[blockProducer]?.sendMessage(EndPoint.RepresentativeStatistics, message)
            Logger.info("Sending info to ${knownNodes[blockProducer]?.ip} with ${latestNetworkStatistics.size}")
        } else {
            val myRepresentative = clusters.entries.firstOrNull { (_, nodes) -> nodes.contains(myPublicKey) }?.key
            if (myRepresentative != null) reportStatistics(myRepresentative)
        }
    }

    fun dockerStatisticsReceived(message: Message<DockerStatistics>) {
        latestNetworkStatistics.add(message.body)
        Logger.trace("Docker stats received... Adding to the latest list: ${latestNetworkStatistics.size}")
    }

    fun representativeStatisticsReceived(message: Message<Array<DockerStatistics>>) {
        latestNetworkStatistics.addAll(message.body)
        Logger.trace("Representative stats received... Adding to the latest list: ${latestNetworkStatistics.size}")
    }

    private fun reportStatistics(destinationKey: String) {
        val node = knownNodes[destinationKey] ?: return
        val latestStatistics = dockerManager.latestStatistics
        val message = networkManager.generateMessage(latestStatistics)
        Logger.trace("Reporting statistics to our cluster representative! ${DigestUtils.sha256Hex(destinationKey)}")
        node.sendMessage(EndPoint.NodeStatistics, message)
    }

}