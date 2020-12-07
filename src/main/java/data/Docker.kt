package data

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
data class DockerStatistics(val publicKey:String, val containers: List<ContainerStats>)

/**
 * Holds information for individual container running.
 *
 * @property id
 * @property name
 * @property cpuUsage Expressed in percentages.
 * @property memoryUsage Expressed in percentages.
 * @property pids Number of processes the container is running.
 */
data class ContainerStats(val id: String, val name: String, val cpuUsage: Double, val memoryUsage: Double, val pids: Int)