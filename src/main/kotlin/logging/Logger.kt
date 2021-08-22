package logging

import data.DebugType
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
        if (!isLoggingEnabled) return
        if (currentDebug == DebugType.ALL || currentDebug == debugType) {
            val typeString = LocalDateTime.now().format(timeFormatter).padEnd(11) + " | " + padRight(debugType.name)
            println("$color$typeString$reset$message")
        }
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