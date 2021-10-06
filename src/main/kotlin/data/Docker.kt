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
    val containers: MutableMap<String, ContainerStatistics>,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val totalCPU: Int get() = containers.values.sumOf { it.cpuUsage.roundToInt() }

    override fun toString() = "Node ... $totalCPU% CPU with ${containers.size} containers"
}