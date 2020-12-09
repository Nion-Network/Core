package manager

import data.ContainerStats
import data.DockerStatistics
import io.javalin.http.Context
import logging.Logger
import utils.Crypto

/**
 * Created by Mihael Valentin Berčič
 * on 27/11/2020 at 17:11
 * using IntelliJ IDEA
 */
class DockerManager(private val crypto: Crypto) {

    private val runtime = Runtime.getRuntime()
    private val statsRegex = "^(?<id>.*?)\\s(?<name>.*?)\\s(?<cpu>[0-9.]+?)%\\s(?<memory>[0-9.]+)%\\s(?<pids>\\d+)$".toRegex(RegexOption.MULTILINE)

    var latestStatistics: DockerStatistics = DockerStatistics(crypto.publicKey, emptyList())
        private set

    init {
        runtime.exec("bash dockerStats.sh")
    }


    fun runImage(context: Context) {
        val image = context.queryParam("image") ?: return
        val containerId = runtime.exec("docker run -d $image").inputStream.bufferedReader().use { it.readText() }
        Logger.debug("Started a new container: $containerId")
    }

    fun updateStats(context: Context) {
        val containerStats = statsRegex.findAll(context.body()).map {
            val groups = it.groups

            val numberOfProcesses = "pids" intFrom groups
            val cpuUsage = "cpu" doubleFrom groups
            val containerId = "id" stringFrom groups
            val containerName = "name" stringFrom groups
            val memoryUsage = "memory" doubleFrom groups

            ContainerStats(containerId, containerName, cpuUsage, memoryUsage, numberOfProcesses)
        }
        latestStatistics = DockerStatistics(crypto.publicKey, containerStats.toList())
    }

    fun exportContainer(name: String) {
        execute(true) { "docker stop $name" }
        execute(true) { "docker commit $name" }
        execute(true) { "docker export $name > $name-export.tar" }

    }

    private fun execute(wait: Boolean, command: () -> String) = runtime.exec(command()).apply { if (wait) this.waitFor() }

    /*
     The following functions are purely for aesthetics and does not provide any functional improvement.
     NOTE: They should only be used when the developer knows the data will 100% exist in MatchGroupCollection.
    */
    private infix fun String.stringFrom(matchGroupCollection: MatchGroupCollection) = matchGroupCollection[this]!!.value
    private infix fun String.doubleFrom(matchGroupCollection: MatchGroupCollection) = matchGroupCollection[this]!!.value.toDouble()
    private infix fun String.intFrom(matchGroupCollection: MatchGroupCollection) = matchGroupCollection[this]!!.value.toInt()
}