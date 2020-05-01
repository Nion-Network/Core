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

        fun sendMessageTo(url: String, path: String = "/", message: Message, type: NetworkRequest = NetworkRequest.POST): Pair<Int, String> = urlRequest(type, "$url$path", message.asJson)

        fun urlRequest(type: NetworkRequest, url: String, body: String = "", customBlock: HttpURLConnection.() -> Unit = {}): Pair<Int, String> = (URL(url).openConnection() as HttpURLConnection).let { connection ->
            println("Sending body to $url\n$body")
            connection.requestMethod = type.name
            connection.doOutput = body.isNotEmpty()
            connection.doInput = true
            if (body.isNotEmpty()) connection.outputStream.bufferedWriter().use {
                println("Writing $body")
                it.write(body)
            }
            connection.apply(customBlock) // Customization
            connection.disconnect()
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
infix fun <T> Context.bodyAs(any: Class<T>): T = body() fromJsonTo any
infix fun <T> String.fromJsonTo(any: Class<T>): T = Main.gson.fromJson(this, any)
val Context.bodyAsMessage: Message get() = Main.gson.fromJson(body(), Message::class.java)