package manager

import data.*
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import utils.Crypto
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

    /** Saves the image of the container([container]) and is stored as either checkpoint or .tar data. */
    private fun saveContainer(container: String): File {
        val arguments = if (configuration.useCriu) arrayOf("-c", container) else arrayOf(container)
        ProcessBuilder("bash", "SaveContainer.sh", *arguments).start().waitFor()
        return File("/tmp/$container.tar")
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
        Logger.info("We have to send container ${migrationPlan.container} to ${migrationPlan.to}")
        dht.searchFor(migrationPlan.to) { receiver ->
            val container = migrationPlan.container
            val file = saveContainer(container)
            val containerMigration = ContainerMigration(container, block.slot, file.readBytes())
            val encoded = ProtoBuf.encodeToByteArray(containerMigration)
            Socket(receiver.ip, networkManager.listeningPort + 1).use { socket ->
                DataOutputStream(socket.getOutputStream()).apply {
                    writeInt(encoded.size)
                    write(encoded)
                }
            }
            file.deleteRecursively()
            latestStatistics.remove(container)
        }
    }

    fun executeMigration(socket: Socket) {
        DataInputStream(socket.getInputStream()).use { dataInputStream ->
            val encodedLength = dataInputStream.readInt()
            val data = dataInputStream.readNBytes(encodedLength)
            val containerMigration = ProtoBuf.decodeFromByteArray<ContainerMigration>(data)
            val image = containerMigration.image
            val containerName = containerMigration.container
            // TODO Perform a check if migration is legitimate

            File("/tmp/$containerName.tar").writeBytes(containerMigration.data)

            val arguments = if (configuration.useCriu) arrayOf("-c", containerName, image) else arrayOf(containerName, image)
            ProcessBuilder("bash", "RunContainer.sh", *arguments).start().waitFor()
            val elapsed = System.currentTimeMillis() - containerMigration.start
            dashboard.newMigration(socket.localSocketAddress.toString(), socket.remoteSocketAddress.toString(), containerName, elapsed, containerMigration.slot)
        }
    }
}