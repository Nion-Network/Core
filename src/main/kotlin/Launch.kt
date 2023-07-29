import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logging.Logger
import utils.tryAndReport
import java.io.File

/**
 * Created by Mihael Valentin Berčič
 * on 06/11/2021 at 19:43
 * using IntelliJ IDEA
 */
@ExperimentalSerializationApi
fun main(args: Array<String>) {
    tryAndReport {
        System.setProperty("kotlinx.coroutines.scheduler", "off")

        val configuration = Json.decodeFromString<Configuration>(File("./config.json").readText())

        Logger.toggleLogging(configuration.loggingEnabled)
        args.getOrNull(0)?.toInt()?.apply {
            configuration.port = this
        }

        Nion(configuration).apply {
            launch()
        }
    }

}
