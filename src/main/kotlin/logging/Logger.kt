package logging

import data.DebugType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Created by Mihael Berčič
 * on 26/03/2020 15:35
 * using IntelliJ IDEA
 *
 * Used for any type of logging.
 */
object Logger {

    private val myIP: String = InetAddress.getLocalHost().hostAddress

    @Serializable
    data class Log(val type: DebugType, val log: String, val ip: String, val timestamp: Long)

    private var isLoggingEnabled = false
    private var currentDebug: DebugType = DebugType.ALL
    private val timeFormatter = DateTimeFormatter.ofPattern("dd. MM | HH:mm:ss.SSS")

    const val red = "\u001b[31m"
    const val blue = "\u001B[34;1m"
    const val cyan = "\u001b[36m"
    const val green = "\u001b[32m"
    const val black = "\u001b[30m"
    const val yellow = "\u001b[33m"
    const val magenta = "\u001b[35m"
    const val white = "\u001b[37m"
    const val reset = "\u001B[0m"

    private val httpClient = HttpClient.newHttpClient()

    private const val informationString =
        " -------------------------------------------\n" +
                "| ${red}Debug information$reset                         |\n" +
                "| d = debug    e = error     c = chain      |\n" +
                "| i = info     t = trace     x = consensus  |\n" +
                "| ------------------------------------------|\n" +
                "| a = ALL                                   |\n" +
                " -------------------------------------------\n"

    /** Prints the given message with the coloring and debug information provided.*/
    private fun log(debugType: DebugType, message: Any, color: String = black) {
        val typeString = LocalDateTime.now().format(timeFormatter).padEnd(11) + " | " + padRight(debugType.name)
        val output = "$color$typeString$reset$message"
        if (isLoggingEnabled) println(output)
        val timestamp = System.currentTimeMillis()
        val log = Log(debugType, output, myIP, timestamp)
        val json = Json.encodeToString(log)
        val request = HttpRequest.newBuilder()
            .uri(URI("http://88.200.63.133:8101/logs"))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
    }

    /** Enables or disables software logging.  */
    fun toggleLogging(enable: Boolean) {
        try {
            isLoggingEnabled = enable
            // TODO: remove logging listener.
            if (enable) {
                Thread {
                    info(informationString)
                    val scanner = Scanner(System.`in`)
                    while (true) {
                        if (scanner.hasNext()) {
                            val next = scanner.next()
                            currentDebug = when (next[0]) {
                                'd' -> DebugType.DEBUG
                                'i' -> DebugType.INFO
                                'e' -> DebugType.ERROR
                                't' -> DebugType.TRACE
                                'c' -> DebugType.CHAIN
                                'x' -> DebugType.CONSENSUS
                                else -> DebugType.ALL
                            }
                            info("Logging level has changed to $currentDebug")
                            info(informationString)

                        }
                    }
                }.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun info(message: Any) = log(DebugType.INFO, message, green)
    fun debug(message: Any) = log(DebugType.DEBUG, message, blue)
    fun error(message: Any) = log(DebugType.ERROR, message, red)
    fun trace(message: Any) = log(DebugType.TRACE, message, yellow)
    fun chain(message: Any) = log(DebugType.CHAIN, message, cyan)
    fun consensus(message: Any) = log(DebugType.CONSENSUS, message, magenta)

    /** Pads the string with the default character of ' ' at the end. */
    private fun padRight(string: String) = string.padEnd(12)

}