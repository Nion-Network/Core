package docker

import data.chain.Block
import data.docker.ContainerStatistics
import data.docker.DockerStatistics
import logging.Dashboard
import utils.Crypto
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Mihael Valentin Berčič
 * on 22/10/2021 at 10:22
 * using IntelliJ IDEA
 */
class DockerDataProxy(private val crypto: Crypto) {

    private val localStatistics = ConcurrentHashMap<String, ContainerStatistics>()
    private val containerMappings = ConcurrentHashMap<String, String>()

    init {
        listenForDockerStatistics()
    }

    fun getMapping(identifier: String) = containerMappings[identifier] ?: identifier

    /** Adds the [container][identifier] to mappings. */
    fun addContainerMapping(networkIdentifier: String, localIdentifier: String) {
        containerMappings[networkIdentifier] = localIdentifier
        containerMappings[localIdentifier] = networkIdentifier
    }

    /** Removes the [container][identifier] from mappings and statistics history. */
    fun removeContainer(identifier: String) {
        localStatistics.remove(identifier)
        containerMappings[identifier]?.let { containerMappings.remove(it) }
        containerMappings.remove(identifier)
    }

    /** Returns latest [DockerStatistics] of [localStatistics]. */
    fun getLatestLocalStatistics(lastBlock: Block): DockerStatistics {
        val containers = localStatistics
            .filterValues { System.currentTimeMillis() - it.updated <= 1000 }
            .map { (id, container) -> container.copy(id = containerMappings[id] ?: id) }
        return DockerStatistics(crypto.publicKey, containers, lastBlock.slot)
    }


    /** Starts a process of `docker stats` and keeps the [localStatistics] up to date. */
    private fun listenForDockerStatistics() {
        Thread {
            val process = ProcessBuilder()
                .command("docker", "stats", "--no-trunc", "--format", "{{.ID}} {{.CPUPerc}} {{.MemPerc}} {{.PIDs}}")
                .redirectErrorStream(true)
                .start()

            val buffer = ByteBuffer.allocate(100_000)
            val escapeSequence = byteArrayOf(0x1B, 0x5B, 0x32, 0x4A, 0x1B, 0x5B, 0x48)
            var escapeIndex = 0
            process.inputStream.use { inputStream ->
                while (true) {
                    try {
                        val byte = inputStream.read().toByte()
                        if (byte < 0) break
                        buffer.put(byte)
                        if (byte == escapeSequence[escapeIndex]) escapeIndex++ else escapeIndex = 0
                        if (escapeIndex != escapeSequence.size) continue
                        val length = buffer.position() - escapeSequence.size
                        if (length > 0) String(buffer.array(), 0, length).split("\n").map { line ->
                            if (line.isNotEmpty()) {
                                val fields = line.split(" ")
                                if (fields.none { it == "--" || it.isEmpty() }) {
                                    val containerId = fields[0]
                                    val cpuPercentage = fields[1].trim('%').toDouble()
                                    val memoryPercentage = fields[2].trim('%').toDouble()
                                    val processes = fields[3].toInt()
                                    localStatistics[containerId] = ContainerStatistics(containerId, cpuPercentage, memoryPercentage, processes)
                                }
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
        }.start()
    }
}