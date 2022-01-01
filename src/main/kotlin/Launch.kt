import data.Configuration
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logging.Dashboard
import logging.Logger
import utils.tryAndReport
import java.io.File

/**
 * Created by Mihael Valentin Berčič
 * on 06/11/2021 at 19:43
 * using IntelliJ IDEA
 */
fun main() {
    System.setProperty("kotlinx.coroutines.scheduler", "off")
    val configuration = Json.decodeFromString<Configuration>(File("./config.json").readText())
    Logger.toggleLogging(configuration.loggingEnabled)
    Dashboard.toString()
    tryAndReport {
        Nion(configuration).apply {
            if (!isTrustedNode) bootstrap(configuration.trustedNodeIP, configuration.trustedNodePort)
            launch()
        }
    }
}