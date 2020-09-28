package logging

import java.util.*

/**
 * Created by Mihael Berčič
 * on 26/03/2020 15:35
 * using IntelliJ IDEA
 */

val Long.toChunkedTimeStamp get() = toString().drop(4).chunked(3).joinToString(" ")
val timestamp: String get() = System.currentTimeMillis().toChunkedTimeStamp


object Logger {

    private var currentDebug: DebugType = DebugType.ALL

    private enum class DebugType { ALL, DEBUG, INFO, ERROR, TRACE, CHAIN, CONSENSUS }

    private const val red = "\u001b[31m"
    private const val blue = "\u001B[34;1m"
    private const val cyan = "\u001b[36m"
    private const val green = "\u001b[32m"
    private const val black = "\u001b[30m"
    private const val yellow = "\u001b[33m"
    private const val magenta = "\u001b[35m"
    private const val white = "\u001b[37m"
    private const val reset = "\u001B[0m"

    private const val informationString = " -------------------------------------------\n" +
            "| ${red}Debug information$reset                         |\n" +
            "| d = debug    e = error     c = chain      |\n" +
            "| i = info     t = trace     x = consensus  |\n" +
            "| ------------------------------------------|\n" +
            "| a = ALL                                   |\n" +
            " -------------------------------------------\n"

    /**
     * Prints the given message with the coloring and debug information provided.
     *
     * @param debugType
     * @param message
     * @param color Text color
     */
    private fun log(debugType: DebugType, message: Any, color: String = black) {
        if (currentDebug == DebugType.ALL || currentDebug == debugType) {
            val typeString = padRight(timestamp) + padRight(debugType.name)
            println("$color$typeString$reset$message")
        }
    }

    /**
     * Starts a console input listening thread used for controlling future printing output.
     *
     */
    fun startInputListening() = Thread {
        println(informationString)
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
                println("Logging level has changed to $currentDebug")
                println(informationString)

            }
        }
    }.start()

    fun info(message: Any) = log(DebugType.INFO, message, green)
    fun debug(message: Any) = log(DebugType.DEBUG, message, blue)
    fun error(message: Any) = log(DebugType.ERROR, message, red)
    fun trace(message: Any) = log(DebugType.TRACE, message, yellow)
    fun chain(message: Any) = log(DebugType.CHAIN, message, cyan)
    fun consensus(message: Any) = log(DebugType.CONSENSUS, message, magenta)

    private fun padRight(string: String) = string.padEnd(12)

}