package manager

import data.Block
import data.Configuration
import data.ContainerStats
import data.Migration
import logging.Dashboard
import logging.Logger
import utils.Crypto
import utils.Utils
import utils.Utils.Companion.asHex
import java.io.BufferedReader
import java.io.File
import java.nio.ByteBuffer

/**
 * Created by Mihael Valentin Berčič
 * on 27/11/2020 at 17:11
 * using IntelliJ IDEA
 */
class DockerManager(private val dht: DistributedHashTable, private val crypto: Crypto, private val dashboard: Dashboard, private val configuration: Configuration) {

    // TODO remove runtime
    private val runtime = Runtime.getRuntime()
    private val gibberishRegex = Regex("(Loaded image ID: )|(sha256:)")
    val latestStatistics = mutableMapOf<String, ContainerStats>()

    init {
        listenForDockerStatistics()
    }

    /** Saves the image of the container([container]) and is stored as either checkpoint or .tar file. */
    private fun saveContainer(container: String): File {
        if (configuration.useCriu) {
            val checkpointName = "$container-checkpoint"

            ProcessBuilder("docker", "checkpoint", "create", "--checkpoint-dir=/tmp", container, checkpointName).start().waitFor()
            ProcessBuilder("tar", "-cf", "$checkpointName.tar", "/tmp/$checkpointName").start().waitFor()
            return File("/tmp/$checkpointName.tar")
        } else {
            ProcessBuilder("docker", "stop", container).start().waitFor()
            val commitOutput = ProcessBuilder("docker", "commit", container).start().inputStream.use { it.bufferedReader().use(BufferedReader::readLine) }
            val saved = commitOutput.replace("sha256:", "").dropLast(1)
            val savedFile = File.createTempFile("container-", ".tar")
            ProcessBuilder("docker", "save", saved).apply {
                redirectOutput(savedFile)
                redirectError(File("error.txt"))
                start().waitFor()
            }
            return savedFile
        }
    }

    /** Starts a process of `docker stats` and keeps the [latestStatistics] up to date. */
    private fun listenForDockerStatistics() {
        Thread {
            val process = ProcessBuilder()
                .command("docker", "stats", "--format", "{{.ID}} {{.Name}} {{.CPUPerc}} {{.MemPerc}} {{.PIDs}}")
                .redirectErrorStream(true)
                .start()

            val buffer = ByteBuffer.allocate(5000)
            val escapeSequence = byteArrayOf(0x1B, 0x5B, 0x32, 0x4A, 0x1B, 0x5B, 0x48)
            var escapeIndex = 0
            process.inputStream.use { inputStream ->
                while (true) {
                    try {
                        val byte = inputStream.read().toByte()
                        if (byte < 0) break
                        buffer.put(byte)
                        if (byte == escapeSequence[escapeIndex]) escapeIndex++ else escapeIndex = 0
                        val length = buffer.position() - escapeSequence.size
                        if (escapeIndex != escapeSequence.size) continue
                        if (length > 0) {
                            val line = String(buffer.array(), 0, length).trim().split(" ")
                            val containerId = line[0]
                            val containerName = line[1]
                            val cpuPercentage = line[2].trim('%').toDouble()
                            val memoryPercentage = line[3].trim('%').toDouble()
                            val processes = line[4].toInt()
                            val container = ContainerStats(containerId, containerName, cpuPercentage, memoryPercentage, processes)
                            latestStatistics[containerId] = container
                        }
                        buffer.clear()
                        escapeIndex = 0
                    } catch (e: Exception) {
                        dashboard.reportException(e)
                    }
                }
            }
        }.start()
    }

    fun migrateContainer(migration: Migration, block: Block) {
        dht.searchFor(migration.to) { receiver ->
            val containerName = migration.containerName
            val savedContainer = saveContainer(containerName)

            Logger.info("We have to send container $containerName to ${receiver.ip}")
            val startOfMigration = System.currentTimeMillis();
            // send migrated file
            val migrationDuration = System.currentTimeMillis() - startOfMigration;

            dashboard.newMigration(Utils.sha256(receiver.publicKey).asHex, Utils.sha256(crypto.publicKey).asHex, containerName, migrationDuration, block.slot)
            savedContainer.delete()
            latestStatistics.remove(containerName)
        }
    }

}