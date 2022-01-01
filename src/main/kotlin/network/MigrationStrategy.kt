package network

import data.Configuration
import data.chain.Block
import data.docker.ContainerMigration
import data.docker.DockerContainer
import data.docker.MigrationPlan
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import utils.tryAndReport
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Mihael Valentin Berčič
 * on 18/11/2021 at 12:31
 * using IntelliJ IDEA
 */
abstract class MigrationStrategy(configuration: Configuration) : Server(configuration) {

    protected val networkMappings = ConcurrentHashMap<String, String>()
    protected val localContainers = ConcurrentHashMap<String, DockerContainer>()
    protected val imageMappings = ConcurrentHashMap<String, String>()

    init {
        Thread(::startListeningForMigrations).start()
    }

    /** Saves the image of the localContainerIdentifier([container]) and is stored as either checkpoint or .tar data. */
    private fun saveContainer(container: String): File {
        val arguments = if (configuration.useCriu) arrayOf("-c", container) else arrayOf(container)
        ProcessBuilder("bash", "SaveContainer.sh", *arguments).start().waitFor()
        return File("/tmp/$container.tar")
    }

    /** Sends the requested localContainerIdentifier from [migrationPlan] to the next node. */
    fun migrateContainer(migrationPlan: MigrationPlan, block: Block) {
        Logger.info("We have to send localContainerIdentifier ${migrationPlan.container} to ${migrationPlan.to}")
        query(migrationPlan.to) { receiver ->
            val networkContainerIdentifier = migrationPlan.container
            val startedAt = System.currentTimeMillis()
            val localContainerIdentifier = networkMappings[networkContainerIdentifier] ?: networkContainerIdentifier
            val file = saveContainer(localContainerIdentifier)
            val savedAt = System.currentTimeMillis()
            val containerMigration = ContainerMigration(networkContainerIdentifier, localContainerIdentifier, block.slot, startedAt, savedAt)
            val encoded = ProtoBuf.encodeToByteArray(containerMigration)
            Socket(receiver.ip, configuration.port + 1).use { socket ->
                DataOutputStream(socket.getOutputStream()).apply {
                    writeInt(encoded.size)
                    write(encoded)
                    writeLong(file.length())
                    file.inputStream().use { it.transferTo(this) }
                }
            }
            file.delete()
            localContainers.remove(localContainerIdentifier)
            localContainers.remove(networkContainerIdentifier)
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
            val networkContainerIdentifier = migrationInformation.networkContainer
            val localContainerIdentifier = migrationInformation.localContainerIdentifier

            val outputFile = File.createTempFile("nion-", "-migration.tar").apply {
                outputStream().use { dataInputStream.transferTo(it) }
            }
            val filePath = outputFile.absolutePath

            val saveTime = migrationInformation.savedAt - migrationInformation.start
            val transmitDuration = System.currentTimeMillis() - migrationInformation.transmitAt
            val resumeStart = System.currentTimeMillis()
            val arguments = if (configuration.useCriu) arrayOf("-c", localContainerIdentifier, image, filePath) else arrayOf(localContainerIdentifier, filePath)
            val newContainer = ProcessBuilder("bash", "RunContainer.sh", *arguments)
                .redirectErrorStream(true)
                .start()
                .inputStream.bufferedReader().use { it.readLine() }
            val resumeDuration = System.currentTimeMillis() - resumeStart
            if (resumeDuration < 8000) {
                Dashboard.reportException(Exception("${localNode.ip} $newContainer"))
            }
            val elapsed = System.currentTimeMillis() - migrationInformation.start
            val localIp = socket.localSocketAddress.toString()
            val remoteIp = socket.remoteSocketAddress.toString()
            val totalSize = encodedLength + fileLength
            networkMappings[networkContainerIdentifier] = newContainer
            networkMappings[newContainer] = networkContainerIdentifier
            Dashboard.newMigration(localIp, remoteIp, networkContainerIdentifier, elapsed, saveTime, transmitDuration, resumeDuration, totalSize, migrationInformation.slot)
        }
    }

    /** Starts the thread listening on socket for receiving migrations. */
    private fun startListeningForMigrations() {
        while (true) tryAndReport {
            executeMigration(tcpSocket.accept())
        }
    }


}