package data.docker

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
    val containers: List<ContainerStatistics>,
    val slot: Long,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val totalCPU: Int get() = containers.sumOf { it.cpuUsage.roundToInt() }

    override fun toString() = "Node ... $totalCPU% CPU with ${containers.size} containers"
}