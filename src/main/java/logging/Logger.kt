package logging

/**
 * Created by Mihael Berčič
 * on 26/03/2020 15:35
 * using IntelliJ IDEA
 */

object Logger {


    val red = "\u001b[31m"
    val blue = "\u001B[34;1m"
    val cyan = "\u001b[36m"
    val green = "\u001b[32m"
    val black = "\u001b[30m"
    val yellow = "\u001b[33m"
    val magenta = "\u001b[35m"
    val white = "\u001b[37m"
    val reset = "\u001B[0m"

    fun debug(any: Any) = println("$blue${padRight("[DEBUG]")}$reset $any")
    fun info(any: Any) = println("$green${padRight("[INFO]")}$reset $any")
    fun error(any: Any) = println("$red${padRight("[ERROR]")}$reset $any")
    fun trace(any: Any) = println("$yellow${padRight("[TRACE]")}$reset $any")

    private fun padRight(string: String) = string.padEnd(10)

}