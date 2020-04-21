package utils

import Main
import abstraction.Message
import abstraction.NetworkRequest
import io.javalin.http.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Created by Mihael Berčič
 * on 26/03/2020 14:45
 * using IntelliJ IDEA
 */

class Utils {

    /*
    Since we'll be using Utils class from Java classes as well, we have to use
    a companion object (static).

    Usage:
        Java   -> Utils.Companion.readFile(...)
        Kotlin -> Utils.readFile(...)
    */

    companion object {

        fun urlRequest(type: NetworkRequest, url: String, body: String = "", customBlock: HttpURLConnection.() -> Unit = {}): Pair<Int, String> = (URL(url).openConnection() as HttpURLConnection).let { connection ->
            connection.requestMethod = type.name
            if (body.isNotEmpty()){
                connection.doOutput = true
                connection.outputStream.bufferedWriter().write(body)
            }
            connection.doInput = true
            connection.apply(customBlock) // Customization
            connection.responseCode to connection.responseMessage
        }

        /**
         * Returns the file's contents specified with path
         *
         * @param path
         * @return File's contents
         */
        fun readFile(path: String): String = File(path).readText()

    }

}

// Body extension methods
infix fun <T> Context.bodyAs(any: Class<T>): T = Main.gson.fromJson(body(), any)
val Context.bodyAsMessage: Message get() = Main.gson.fromJson(body(), Message::class.java)