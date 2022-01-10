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
 * @property pids Number of processes the localContainerIdentifier is running.
 */
@Serializable
data class DockerContainer(
    val id: String,
    val pids: Int,
    val memoryUsage: CircularList<Double>,
    var cpuUsage: CircularList<Double>,
    var updated: Long = System.currentTimeMillis()
) {

    val averageCpuUsage get(): Double = cpuUsage.elements().average()

    val averageMemoryUsage get(): Double = memoryUsage.elements().average()

}