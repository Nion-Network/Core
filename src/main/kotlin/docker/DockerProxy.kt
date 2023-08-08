package docker

import Configuration
import chain.data.Block
import kotlinx.serialization.ExperimentalSerializationApi
import logging.Dashboard
import logging.Logger
import network.data.Endpoint
import network.data.clusters.Cluster
import network.data.messages.Message
import network.rpc.Topic
import utils.CircularList
import utils.runAfter
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Created by Mihael Valentin Berčič
 * on 18/11/2021 at 12:32
 * using IntelliJ IDEA
 *
 * Class holds information about docker containers across the whole network as well as provides functionality regarding
 * network statistics aggregation.
 */
@ExperimentalSerializationApi
abstract class DockerProxy(configuration: Configuration) : MigrationStrategy(configuration) {

    private val networkLock = ReentrantLock(true)
    private val networkStatistics = ConcurrentHashMap<Long, MutableSet<DockerStatistics>>()

    init {
        Thread(::listenForDockerStatistics).start()
    }

    /**
     * Removes statistics that are outdated (previous slots).
     *
     * @param slot
     */
    private fun removeOutdatedStatistics(slot: Long) {
        networkStatistics.clear()
        val keys = networkStatistics.keys.filter { it < slot }
        for (key in keys) networkStatistics.remove(key)
    }

    /** Stores all [statistics] into latest [networkStatistics] using [networkLock]. */
    private fun addNetworkStatistics(vararg statistics: DockerStatistics) {
        networkLock.withLock {
            statistics.forEach { dockerStatistics ->
                val list = networkStatistics.computeIfAbsent(dockerStatistics.slot) { mutableSetOf() }
                list.add(dockerStatistics)
            }
            Logger.info("Added ${statistics.size} statistics...")
        }
    }

    /** Retrieves all [DockerStatistics] from [networkStatistics] for the [slot]. */
    fun getNetworkStatistics(slot: Long): Set<DockerStatistics> {
        return networkLock.withLock { networkStatistics[slot]?.toSet() ?: emptySet() }
    }

    /** Sends (new) local [DockerStatistics] to our centroid / block producer (if we're centroid).  */
    fun sendDockerStatistics(block: Block, blockProducer: String, clusters: Map<String, Cluster<String>>) {
        val slot = block.slot
        val currentTime = System.currentTimeMillis()
        localContainers.entries.removeIf { (_, container) -> currentTime - container.updated >= 1000 }

        val mapped: List<DockerContainer> = localContainers.values.map { it.copy(id = networkMappings[it.id] ?: it.id) }
        val localStatistics = DockerStatistics(localNode.publicKey, mapped, slot)
        val ourPublicKey = localNode.publicKey
        val ourCluster = clusters[ourPublicKey]
        if (ourCluster != null) {
            val clusterRepresentative = ourCluster.centroid
            val isRepresentative = clusterRepresentative == ourPublicKey
            removeOutdatedStatistics(slot)
            if (!isRepresentative) send(Endpoint.NodeStatistics, arrayOf(localStatistics), clusterRepresentative)
            else runAfter(configuration.slotDuration / 4) {
                val networkStatistics = getNetworkStatistics(slot).plus(localStatistics)
                send(Endpoint.NodeStatistics, networkStatistics, blockProducer)
            }
        } else Dashboard.reportException(Exception("Our cluster does not exist."))
    }

    /** On [Endpoint.NodeStatistics] received, all received statistics are added to [networkStatistics] using [networkLock]. */
    fun dockerStatisticsReceived(message: Message) {
        val receivedStatistics = message.decodeAs<Array<DockerStatistics>>()
        addNetworkStatistics(*receivedStatistics)
    }

    /** Starts a process of `docker stats` and keeps the [localStatistics] up to date. */
    private fun listenForDockerStatistics() {
        val numberOfElements = (configuration.slotDuration / 1000).toInt()

        val process = ProcessBuilder()
            .command("docker", "stats", "--no-trunc", "--format", "{{.ID}} {{.CPUPerc}} {{.MemPerc}} {{.PIDs}}")
            .redirectErrorStream(true)
            .start()

        val buffer = ByteBuffer.allocate(100_000)
        val escapeSequence = byteArrayOf(0x1B, 0x5B, 0x32, 0x4A, 0x1B, 0x5B, 0x48) // Escape sequence of CLI output.
        var escapeIndex = 0
        process.inputStream.use { inputStream ->
            while (true) {
                try {
                    val byte = inputStream.read().toByte()
                    if (byte < 0) break
                    buffer.put(byte)
                    if (byte == escapeSequence[escapeIndex]) escapeIndex++ else escapeIndex = 0
                    if (escapeIndex != escapeSequence.size) continue // If escape sequence was not detected, continue reading data.
                    val length = buffer.position() - escapeSequence.size
                    if (length > 0) String(buffer.array(), 0, length).split("\n").map { line ->
                        if (line.isNotEmpty()) {
                            val fields = line.split(" ")
                            val containerId = fields[0]
                            if (fields.none { it.contains("-") || it.isEmpty() }) {
                                val cpuPercentage = fields[1].trim('%').toDouble()
                                val memoryPercentage = fields[2].trim('%').toDouble()
                                val activeProcesses = fields[3].toInt()
                                val container = localContainers.computeIfAbsent(containerId) {
                                    DockerContainer(containerId, activeProcesses, CircularList(numberOfElements), CircularList(numberOfElements))
                                }
                                container.apply {
                                    cpuUsage.add(cpuPercentage)
                                    memoryUsage.add(memoryPercentage)
                                    updated = System.currentTimeMillis()
                                    processes = activeProcesses
                                }
                            } else localContainers[containerId]?.updated = System.currentTimeMillis()
                        }
                    }
                    buffer.clear()
                    escapeIndex = 0
                } catch (e: Exception) {
                    buffer.clear()
                    escapeIndex = 0
                    Dashboard.reportException(e)
                }
            }
        }

    }
}