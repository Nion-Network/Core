package manager

import data.Configuration
import data.ContainerStats
import data.DockerStatistics
import io.javalin.http.Context
import logging.Logger
import utils.Crypto
import java.io.BufferedReader
import java.io.File

/**
 * Created by Mihael Valentin Berčič
 * on 27/11/2020 at 17:11
 * using IntelliJ IDEA
 */
class DockerManager(private val crypto: Crypto, private val configuration: Configuration) {

    private val runtime = Runtime.getRuntime()
    private val statsRegex = "^(?<id>.*?)\\s(?<name>.*?)\\s(?<cpu>[0-9.]+?)%\\s(?<memory>[0-9.]+)%\\s(?<pids>\\d+)$".toRegex(RegexOption.MULTILINE)
    private val gibberishRegex = Regex("(Loaded image ID: )|(sha256:)")

    val ourContainers: MutableList<String> = mutableListOf()

    var latestStatistics: DockerStatistics = DockerStatistics(crypto.publicKey, emptyList())
        private set

    init {
        runtime.apply {
            exec("pkill -f dockerStats")
            exec("bash dockerStats.sh &")
        }
    }


    fun runImage(context: Context) {
        Logger.debug("Requested to run new image!")
        val image = context.queryParam("image") ?: return
        runImage(image)
    }

    private fun runImage(name: String) {
        Logger.info("Trying to run: $name")

        val toRun = name.replace(gibberishRegex, "")
        val containerId = runtime.exec("docker run -d $toRun").inputStream.bufferedReader().use(BufferedReader::readLine)
        Logger.debug("Started a new container: $containerId")
        ourContainers.add(containerId.take(12))
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
        val filteredContainers = containerStats.filter { ourContainers.contains(it.id) }.toList()
        // Logger.debug("Filtered containers: $filteredContainers")
        latestStatistics = DockerStatistics(crypto.publicKey, filteredContainers)
    }

    fun runMigratedImage(context: Context) {
        Logger.info("Running a migrated image using CRIU: ${configuration.useCriu}")
        val imageName = context.header("name") ?: return
        val fileBytes = context.bodyAsBytes()
        val fileName = "$imageName-temp.tar"
        println("File bytes size: ${fileBytes.size}")
        val storedFile = File(fileName).apply { writeBytes(fileBytes) }
        val imageId = runtime.exec("docker load -i $fileName").inputStream.bufferedReader().use(BufferedReader::readLine)
        runImage(imageId)
        storedFile.delete()
    }

    fun saveImage(name: String): File {
        runtime.apply {
            return if (configuration.useCriu) {
                val checkpointName = "$name-checkpoint"
                throw Exception("CRIU is not yet supported!")
            } else {
                exec("docker stop $name").waitFor()
                val savedOutput = exec("docker commit $name").inputStream.bufferedReader().use(BufferedReader::readText)
                val saved = savedOutput.replace("sha256:", "").dropLast(1)
                val fileName = "$name-export.tar"
                val savedFile = File(fileName)
                ProcessBuilder("docker", "save", saved).apply {
                    redirectOutput(savedFile)
                    redirectError(File("error.txt"))
                    start().waitFor()
                }
                savedFile
            }
        }
    }

    /*
     The following functions are purely for aesthetics and does not provide any functional improvement.
     NOTE: They should only be used when the developer knows the data will 100% exist in MatchGroupCollection.
    */
    private infix fun String.stringFrom(matchGroupCollection: MatchGroupCollection) = matchGroupCollection[this]!!.value
    private infix fun String.doubleFrom(matchGroupCollection: MatchGroupCollection) = matchGroupCollection[this]!!.value.toDouble()
    private infix fun String.intFrom(matchGroupCollection: MatchGroupCollection) = matchGroupCollection[this]!!.value.toInt()
}