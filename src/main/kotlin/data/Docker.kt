package data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Created by Mihael Valentin Berčič
 * on 27/11/2020 at 20:25
 * using IntelliJ IDEA
 */

/**
 * Holds information about currently running containers.
 *
 * @property containers
 */
@Serializable
data class DockerStatistics(
    val publicKey: String,
    val containers: MutableMap<String, ContainerStats>,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val totalCPU: Int get() = containers.values.sumOf { it.cpuUsage.roundToInt() }

    override fun toString() = "Node ... $totalCPU% CPU with ${containers.size} containers"
}

/**
 * Holds information for individual container running.
 *
 * @property id
 * @property name
 * @property cpuUsage Expressed in percentages.
 * @property memoryUsage Expressed in percentages.
 * @property pids Number of processes the container is running.
 */
@Serializable
data class ContainerStats(
    val id: String,
    val name: String,
    var cpuUsage: Double,
    val memoryUsage: Double,
    val pids: Int,
    val updated: Long = System.currentTimeMillis()
)

@Serializable
data class Migration(val from: String, val to: String, val containerName: String)