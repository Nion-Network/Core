import data.Configuration
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logging.Dashboard
import logging.Logger.info
import logging.Logger.toggleLogging
import manager.NetworkManager
import java.io.File

/**
 * Created by Mihael Valentin Berčič
 * on 26/03/2020 12:35
 * using IntelliJ IDEA
 */
fun main(args: Array<String>) {
    System.setProperty("kotlinx.coroutines.scheduler", "off")
    val pathArgumentIndex = args.indexOf("-c")
    val portArgumentIndex = args.indexOf("-p")
    val loggingArgumentIndex = args.indexOf("-l")

    val isPathSpecified = pathArgumentIndex >= 0
    val isPortSpecified = portArgumentIndex >= 0
    val isLoggingEnabled = loggingArgumentIndex >= 0

    val configurationPath = if (isPathSpecified) args[pathArgumentIndex + 1] else "./config.json"
    val listeningPort = if (isPortSpecified) args[portArgumentIndex + 1].toInt() else 5000

    toggleLogging(isLoggingEnabled)
    info("Path for config file specified: $isPathSpecified")
    info("Using $listeningPort port.")
    info("Using $configurationPath configuration file...")

    val configurationJson = File(configurationPath).readText()
    val configuration: Configuration = Json.decodeFromString(configurationJson)
    val dashboard = Dashboard(configuration)

    try {
        val network = NetworkManager(configuration, dashboard, listeningPort)
        network.start()
    } catch (e: Exception) {
        e.printStackTrace()
        dashboard.reportException(e)
    }
}