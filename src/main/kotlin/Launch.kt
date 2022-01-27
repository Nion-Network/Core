import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logging.Logger
import utils.tryAndReport
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 06/11/2021 at 19:43
 * using IntelliJ IDEA
 */
fun main(args: Array<String>) {
    System.setProperty("kotlinx.coroutines.scheduler", "off")
    val configuration = Json.decodeFromString<Configuration>(File("./config.json").readText())
    Logger.toggleLogging(configuration.loggingEnabled)
    args.getOrNull(0)?.toInt()?.apply {
        configuration.passedPort = this
        println("Passed udpPort: $this...")
    }
    val port = 6969
    val server = ServerSocket(port)
    val queue = LinkedBlockingQueue<Socket>()
    var x = Random.nextBoolean()
    GlobalScope.launch {
        while (true) {
            val socket = server.accept()
            socket.use {
                println("Closed a socket.")
            }
        }
    }
    tryAndReport {
        Nion(configuration).apply {
            launch()
        }
    }
}