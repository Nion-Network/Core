package utils

import Main
import abstraction.Message
import abstraction.NetworkRequest
import com.google.gson.reflect.TypeToken
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

        fun <T> sendMessageTo(url: String, path: String = "/", message: Message<T>, type: NetworkRequest = NetworkRequest.POST): Pair<Int, String> = urlRequest(type, "$url$path", message.asJson)

        private fun urlRequest(type: NetworkRequest, url: String, body: String = "", customBlock: HttpURLConnection.() -> Unit = {}): Pair<Int, String> = (URL(url).openConnection() as HttpURLConnection).let { connection ->
            connection.requestMethod = type.name
            connection.doOutput = body.isNotEmpty()
            connection.doInput = true

            if (body.isNotEmpty()) connection.outputStream.bufferedWriter().use { it.write(body) }
            connection.apply(customBlock) // Customization
            connection.disconnect()
            return connection.responseCode to connection.responseMessage
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

inline fun <reified T> Context.getMessage(): Message<T> = Main.gson.fromJson<Message<T>>(body(), TypeToken.getParameterized(Message::class.java, T::class.java).type)