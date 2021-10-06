package data

import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 07/10/2021 at 00:54
 * using IntelliJ IDEA

 * Holds information for individual container running.
 *
 * @property id
 * @property name
 * @property cpuUsage Expressed in percentages.
 * @property memoryUsage Expressed in percentages.
 * @property pids Number of processes the container is running.
 */
@Serializable
data class ContainerStatistics(
    val id: String,
    val name: String,
    var cpuUsage: Double,
    val memoryUsage: Double,
    val pids: Int,
    val updated: Long = System.currentTimeMillis()
)