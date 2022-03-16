package docker

import Configuration
import chain.data.Block
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logging.Dashboard
import logging.Logger
import network.messaging.Server
import utils.tryAndReport
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Created by Mihael Valentin Berčič
 * on 18/11/2021 at 12:31
 * using IntelliJ IDEA
 */
@ExperimentalSerializationApi
abstract class MigrationStrategy(configuration: Configuration) : Server(configuration) {

    protected val networkMappings = ConcurrentHashMap<String, String>()
    protected val localContainers = ConcurrentHashMap<String, DockerContainer>()
    protected val imageMappings = ConcurrentHashMap<String, String>()

    init {
        Thread(::startListeningForMigrations).start() //  ToDo: Turn on.
    }

    /** Saves the image of the localContainerIdentifier([container]) and is stored as either checkpoint or .tar data. */
    private fun saveContainer(container: String): File {
        val arguments = if (configuration.useCriu) arrayOf("-c", container) else arrayOf(container)
        val process = ProcessBuilder("bash", "SaveContainer.sh", *arguments).start()
        process.waitFor()
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
            Socket(receiver.ip, receiver.migrationPort).use { socket ->
                DataOutputStream(socket.getOutputStream()).apply {
                    writeInt(encoded.size)
                    // TODO: Write image of the container. With length.
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
    private fun runMigratedContainer(socket: Socket) {
        DataInputStream(socket.getInputStream()).use { dataInputStream ->
            val encodedLength = dataInputStream.readInt()
            val migrationData = dataInputStream.readNBytes(encodedLength)
            val fileLength = dataInputStream.readLong()
            val migrationInformation = ProtoBuf.decodeFromByteArray<ContainerMigration>(migrationData)

            val image = migrationInformation.image
            val networkContainerIdentifier = migrationInformation.networkContainer
            val localContainerIdentifier = migrationInformation.localContainerIdentifier


            val outputFile = File("/tmp/$localContainerIdentifier.tar").apply {
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
            runMigratedContainer(migrationSocket.accept())
        }
    }

    /** Computes which migrations should happen based on provided docker statistics. */
    protected fun computeMigrations(previouslyMigratedContainers: List<String>, dockerStatistics: Set<DockerStatistics>): Map<String, MigrationPlan> {
        val migrations = mutableMapOf<String, MigrationPlan>()
        val mostUsedNode = dockerStatistics.maxByOrNull { it.totalCPU }
        val leastUsedNode = dockerStatistics.filter { it != mostUsedNode }.minByOrNull { it.totalCPU }

        if (leastUsedNode != null && mostUsedNode != null && leastUsedNode != mostUsedNode) {
            val leastConsumingApp = mostUsedNode.containers.minByOrNull { it.averageCpuUsage }

            if (leastConsumingApp != null) {
                val mostUsage = mostUsedNode.totalCPU
                val leastUsage = leastUsedNode.totalCPU
                val appUsage = leastConsumingApp.averageCpuUsage
                val beforeMigration = abs(mostUsage - leastUsage)
                val afterMigration = abs((mostUsage - appUsage) - (leastUsage + appUsage))
                val difference = abs(beforeMigration - afterMigration)

                if (difference > 1) {
                    val migration = MigrationPlan(mostUsedNode.publicKey, leastUsedNode.publicKey, leastConsumingApp.id)
                    migrations[mostUsedNode.publicKey] = migration
                }
            }
        }
        return migrations
    }
}