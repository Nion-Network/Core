import data.Configuration
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logging.Logger
import java.io.File

/**
 * Created by Mihael Valentin Berčič
 * on 06/11/2021 at 19:43
 * using IntelliJ IDEA
 */
fun main(args: Array<String>) {
    System.setProperty("kotlinx.coroutines.scheduler", "off")
    val configuration = Json.decodeFromString<Configuration>(File("./config.json").readText())
    Logger.toggleLogging(configuration.loggingEnabled)

    Nion(configuration).apply {
        launch()
    }

}