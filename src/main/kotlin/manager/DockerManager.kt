package manager

import data.*
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import utils.Crypto
import utils.Utils
import utils.Utils.Companion.asHex
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Created by Mihael Valentin Berčič
 * on 27/11/2020 at 17:11
 * using IntelliJ IDEA
 */
class DockerManager(
    private val dht: DistributedHashTable,
    private val crypto: Crypto,
    private val networkManager: NetworkManager,
    private val dashboard: Dashboard,
    private val configuration: Configuration
) {

    val latestStatistics = mutableMapOf<String, ContainerStatistics>()

    init {
        listenForDockerStatistics()
    }

    /** Saves the image of the container([container]) and is stored as either checkpoint or .tar file. */
    private fun saveContainer(container: String): File {
        if (configuration.useCriu) {
            val checkpointName = "$container-checkpoint"

            ProcessBuilder("docker", "checkpoint", "create", "--checkpoint-dir=/tmp", container, checkpointName).start().waitFor()
            ProcessBuilder("tar", "-C", "/tmp", "-cf", "/tmp/$checkpointName.tar", checkpointName).start().waitFor()
            File("/tmp/$checkpointName").deleteRecursively()
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
                .command("docker", "stats", "--no-trunc", "--format", "{{.ID}} {{.Name}} {{.CPUPerc}} {{.MemPerc}} {{.PIDs}}")
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
                        if (length > 0) {
                            String(buffer.array(), 0, length).split("\n").map { line ->
                                if (line.isNotEmpty()) {
                                    val fields = line.split(" ")
                                    val containerId = fields[0]
                                    val containerName = fields[1]
                                    val cpuPercentage = fields[2].trim('%').toDouble()
                                    val memoryPercentage = fields[3].trim('%').toDouble()
                                    val processes = fields[4].toInt()
                                    val container = ContainerStatistics(containerId, containerName, cpuPercentage, memoryPercentage, processes)
                                    latestStatistics[containerId] = container
                                }
                            }
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

    fun migrateContainer(migrationPlan: MigrationPlan, block: Block) {
        dht.searchFor(migrationPlan.to) { receiver ->
            val containerName = migrationPlan.containerName
            Logger.info("We have to send container $containerName to ${receiver.ip}")
            val file = saveContainer(containerName)
            val startOfMigration = System.currentTimeMillis()
            sendContainer(receiver, containerName, file, block)
            val migrationDuration = System.currentTimeMillis() - startOfMigration
            dashboard.newMigration(Utils.sha256(receiver.publicKey).asHex, Utils.sha256(crypto.publicKey).asHex, containerName, migrationDuration, block.slot)
            file.deleteRecursively()
            latestStatistics.remove(containerName)
        }
    }

    fun executeMigration(socket: Socket) {
        DataInputStream(socket.getInputStream()).apply {
            val encodedLength = readInt()
            val data = readNBytes(encodedLength)
            val containerMigration = ProtoBuf.decodeFromByteArray<ContainerMigration>(data)
            val containerName = containerMigration.container
            // TODO Perform a check if migration is legitimate
            if (configuration.useCriu) {
                val fileLocation = "/tmp/${containerMigration.container}-checkpoint.tar"
                File(fileLocation).writeBytes(containerMigration.file)
                val containerId = ProcessBuilder("docker", "create", containerMigration.image).start().let {
                    it.waitFor()
                    it.inputStream.bufferedReader().use { reader -> reader.readLines().last() }
                }
                ProcessBuilder("tar", "-xf", fileLocation, "-C", "/var/lib/docker/containers/$containerId/checkpoints/").start().waitFor()
                dashboard.newMigration(containerId, "--checkpoint=$containerName-checkpoint", socket.localSocketAddress.toString(), 22, 22)
                ProcessBuilder("docker", "start", "--checkpoint=$containerName-checkpoint", containerId).start().waitFor()
            }
        }
    }


    private fun sendContainer(node: Node, container: String, file: File, block: Block) {
        val containerMigration = ContainerMigration(container, block.slot, file.readBytes())
        val encoded = ProtoBuf.encodeToByteArray(containerMigration)
        Socket(node.ip, networkManager.listeningPort + 1).use { socket ->
            DataOutputStream(socket.getOutputStream()).apply {
                writeInt(encoded.size)
                write(encoded)
            }
        }
    }

}