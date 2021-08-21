package manager

import data.Configuration
import data.ContainerStats
import data.DockerStatistics
import io.javalin.http.Context
import logging.Logger
import utils.Crypto
import java.io.BufferedReader
import java.io.File
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 27/11/2020 at 17:11
 * using IntelliJ IDEA
 */
class DockerManager(private val crypto: Crypto, private val configuration: Configuration) {

    private val runtime = Runtime.getRuntime()
    private val statsRegex =
        "^(?<id>[a-zA-Z0-9]+)\\s(?<name>.*?)\\s(?<cpu>[0-9.]+?)%\\s((?<memory>[0-9.]+)[a-zA-Z]{3}\\s/\\s(?<maxMemory>[0-9.]+[a-zA-Z]{3}))\\s(?<pids>[0-9]+)$".toRegex(RegexOption.MULTILINE)

    private val gibberishRegex = Regex("(Loaded image ID: )|(sha256:)")
    val ourContainers: MutableList<String> = mutableListOf()
    var latestStatistics: DockerStatistics = DockerStatistics(crypto.publicKey, mutableListOf())

    init {
        runtime.apply {
            // exec("pkill -f dockerStats")
            // exec("bash dockerStats.sh &")
        }
    }

    /** Docker image of name "image" from the http query is ran. */
    fun runImage(context: Context) {
        Logger.debug("Requested to run new image!")
        val image = context.queryParam("image") ?: return
        runImage(image)
    }

    /** Docker image of [name] is run. If it does not exist locally, it is pulled from the hub. */
    private fun runImage(name: String) {
        Logger.info("Trying to run: $name")

        val toRun = name.replace(gibberishRegex, "")
        val containerId = runtime.exec("docker run -d $toRun").inputStream.bufferedReader().use(BufferedReader::readLine)
        ourContainers.add(containerId.take(12))
        latestStatistics.containers.add(ContainerStats(containerId, containerId, 20.0, 20.0, Random.nextInt(100)))

        Logger.debug("Started a new container: $containerId")
        Logger.debug("Total running containers on our node: ${ourContainers.size}")
    }

    /** After receiving docker statistics, our [latest statistics][latestStatistics] are updated. */
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
        // latestStatistics = DockerStatistics(crypto.publicKey, filteredContainers)
    }

    /** Runs a freshly migrated image using either CRIU or not. The image is sent through a HTTP request. */
    fun runMigratedImage(context: Context) {
        Logger.info("Running a migrated image using CRIU: ${configuration.useCriu}")
        val imageName = context.header("name") ?: return
        val fileBytes = context.bodyAsBytes()
        val fileName = "$imageName-temp.tar"

        val storedFile = File(fileName).apply { writeBytes(fileBytes) }
        val imageId = runtime.exec("docker load -i $fileName").inputStream.bufferedReader().use(BufferedReader::readLine)
        runImage(imageId)
        storedFile.delete()
    }

    /** Saves the image of the container([name]) and is stored as either checkpoint or .tar file. */
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