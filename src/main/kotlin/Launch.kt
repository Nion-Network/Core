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

        val passedConfiguration = args.getOrNull(0) ?: throw Exception("Configuration should be passed to the docker container.")
        val configuration = Json.decodeFromString<Configuration>(passedConfiguration)
        Logger.toggleLogging(configuration.loggingEnabled)
        args.getOrNull(1)?.toInt()?.apply {
            configuration.port = this
        }
        Nion(configuration).apply {
            launch()
        }
    }

}
