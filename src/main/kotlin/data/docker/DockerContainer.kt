package data.docker

import kotlinx.serialization.Serializable

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
    var cpuUsage: Double,
    val memoryUsage: Double,
    val pids: Int,
    var updated: Long = System.currentTimeMillis()
)