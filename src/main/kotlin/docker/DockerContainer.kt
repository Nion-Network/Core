package docker

import kotlinx.serialization.Serializable
import utils.CircularList

/**
 * Created by Mihael Valentin Berčič
 * on 07/10/2021 at 00:54
 * using IntelliJ IDEA

 * Holds information for individual localContainerIdentifier running.
 *
 * @property id
 * @property name
 * @property cpuUsage Expressed in percentages.
 * @property memoryUsage Expressed in percentages.
 * @property processes Number of processes the localContainerIdentifier is running.
 */
@Serializable
data class DockerContainer(
    val id: String,
    var processes: Int,
    val memoryUsage: CircularList<Double>,
    val cpuUsage: CircularList<Double>,
    var updated: Long = System.currentTimeMillis(),
    var latestSnapshot: String? = null
) {
    val averageCpuUsage get(): Double = cpuUsage.elements().average().takeIf { !it.isNaN() } ?: 0.0
    val averageMemoryUsage get(): Double = memoryUsage.elements().average().takeIf { !it.isNaN() } ?: 0.0
}