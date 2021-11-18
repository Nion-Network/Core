package network

import data.Configuration
import data.chain.Block
import data.communication.Message
import data.communication.TransmissionType
import data.docker.DockerContainer
import data.docker.DockerStatistics
import data.network.Endpoint
import logging.Logger
import utils.runAfter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Created by Mihael Valentin Berčič
 * on 18/11/2021 at 12:32
 * using IntelliJ IDEA
 */
abstract class DockerProxy(configuration: Configuration) : MigrationStrategy(configuration) {

    private val networkLock = ReentrantLock(true)
    private val localContainers = ConcurrentHashMap<String, DockerContainer>()
    private val networkStatistics = ConcurrentHashMap<Long, MutableList<DockerStatistics>>()

    private fun addNetworkStatistics(vararg statistics: DockerStatistics) {
        networkLock.withLock {
            statistics.forEach { dockerStatistics ->
                val list = networkStatistics.computeIfAbsent(dockerStatistics.slot) { mutableListOf() }
                list.add(dockerStatistics)
            }
        }
        Logger.info("Added ${statistics.size} statistics...")
    }

    fun getNetworkStatistics(slot: Long): List<DockerStatistics> {
        return networkLock.withLock { networkStatistics[slot]?.toList() ?: emptyList() }
    }

    fun sendDockerStatistics(block: Block, blockProducer: String, clusters: List<Cluster>) {
        val slot = block.slot
        val localStatistics = DockerStatistics(localNode.publicKey, localContainers.values.toList(), slot)
        val ourPublicKey = localNode.publicKey
        val isRepresentative = clusters.any { it.representative == ourPublicKey }
        val ourCluster = clusters.firstOrNull { it.representative == ourPublicKey || it.nodes.contains(ourPublicKey) }
        Logger.info("Sending docker statistics[$isRepresentative]: ${ourCluster?.nodes?.size ?: 0}")
        if (!isRepresentative) {
            if (ourCluster != null) send(Endpoint.NodeStatistics, TransmissionType.Unicast, arrayOf(localStatistics), ourCluster.representative)
        } else runAfter(configuration.slotDuration / 2) {
            val statistics = getNetworkStatistics(slot).plus(localStatistics)
            send(Endpoint.NodeStatistics, TransmissionType.Unicast, statistics, blockProducer)
        }

    }

    @MessageEndpoint(Endpoint.NodeStatistics)
    fun dockerStatisticsReceived(message: Message) {
        val receivedStatistics = message.decodeAs<Array<DockerStatistics>>()
        addNetworkStatistics(*receivedStatistics)
    }

}