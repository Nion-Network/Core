package docker

import data.Configuration
import data.chain.Block
import data.docker.ContainerMigration
import data.docker.MigrationPlan
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import network.DistributedHashTable
import network.Network
import utils.coroutineAndReport
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket

/**
 * Created by Mihael Valentin Berčič
 * on 27/11/2020 at 17:11
 * using IntelliJ IDEA
 */
class DockerMigrationStrategy(
    private val dht: DistributedHashTable,
    private val dockerDataProxy: DockerDataProxy,
    private val network: Network,
    private val configuration: Configuration
) {

    private val migrationSocket = ServerSocket(network.listeningPort + 1)

    init {
        startListeningForMigrations()
    }

    /** Saves the image of the container([container]) and is stored as either checkpoint or .tar data. */
    private fun saveContainer(container: String): File {
        val arguments = if (configuration.useCriu) arrayOf("-c", container) else arrayOf(container)
        ProcessBuilder("bash", "SaveContainer.sh", *arguments).start().waitFor()
        return File("/tmp/$container.tar")
    }

    /** Sends the requested container from [migrationPlan] to the next node. */
    fun migrateContainer(migrationPlan: MigrationPlan, block: Block) {
        Logger.info("We have to send container ${migrationPlan.container} to ${migrationPlan.to}")
        dht.searchFor(migrationPlan.to) { receiver ->
            val container = migrationPlan.container
            val startedAt = System.currentTimeMillis()
            val containerMapping = dockerDataProxy.getMapping(container)
            val file = saveContainer(containerMapping)
            val savedAt = System.currentTimeMillis()
            val containerMigration = ContainerMigration(container, block.slot, startedAt, savedAt)
            val encoded = ProtoBuf.encodeToByteArray(containerMigration)
            Socket(receiver.ip, network.listeningPort + 1).use { socket ->
                DataOutputStream(socket.getOutputStream()).apply {
                    writeInt(encoded.size)
                    write(encoded)
                    writeLong(file.length())
                    file.inputStream().use { it.transferTo(this) }
                }
            }
            file.delete()
            dockerDataProxy.removeContainer(container)
        }
    }

    /** Reads and executes migration from [socket] sent by another node. */
    private fun executeMigration(socket: Socket) {
        DataInputStream(socket.getInputStream()).use { dataInputStream ->
            val encodedLength = dataInputStream.readInt()
            val migrationData = dataInputStream.readNBytes(encodedLength)
            val fileLength = dataInputStream.readLong()
            val migrationInformation = ProtoBuf.decodeFromByteArray<ContainerMigration>(migrationData)

            val image = migrationInformation.image
            val migratedContainer = migrationInformation.container

            val outputFile = File("/tmp/$migratedContainer.tar").apply {
                outputStream().use { dataInputStream.transferTo(it) }
            }

            val saveTime = migrationInformation.savedAt - migrationInformation.start
            val transmitDuration = System.currentTimeMillis() - migrationInformation.transmitAt
            val resumeStart = System.currentTimeMillis()
            val arguments = if (configuration.useCriu) arrayOf("-c", migratedContainer, image) else arrayOf(migratedContainer)
            val newContainer = ProcessBuilder("bash", "RunContainer.sh", *arguments)
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().use { it.readLine() }
            val resumeDuration = System.currentTimeMillis() - resumeStart
            val elapsed = System.currentTimeMillis() - migrationInformation.start
            val localIp = socket.localSocketAddress.toString()
            val remoteIp = socket.remoteSocketAddress.toString()
            val totalSize = encodedLength + fileLength
            dockerDataProxy.addContainerMapping(migratedContainer, newContainer)
            Dashboard.newMigration(localIp, remoteIp, migratedContainer, elapsed, saveTime, transmitDuration, resumeDuration, totalSize, migrationInformation.slot)
            outputFile.delete()
        }
    }

    /** Starts the thread listening on socket for receiving migrations. */
    private fun startListeningForMigrations() {
        Thread {
            while (true) {
                val socket = migrationSocket.accept()
                coroutineAndReport {
                    socket.use { executeMigration(it) }
                }
            }
        }.start()
    }

}