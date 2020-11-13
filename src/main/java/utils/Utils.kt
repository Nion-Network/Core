package utils

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import data.Message
import data.NetworkRequestType
import io.javalin.http.Context
import logging.Logger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.concurrent.schedule

/**
 * Created by Mihael Berčič
 * on 26/03/2020 14:45
 * using IntelliJ IDEA
 */

class Utils {

    companion object {

        val gson = GsonBuilder().setPrettyPrinting().create()

        fun <T> sendMessageTo(url: String, path: String = "/", message: Message<T>, type: NetworkRequestType = NetworkRequestType.POST): Pair<Int, String> = urlRequest(type, "$url$path", message.asJson) {
            this.addRequestProperty("hex", message.hex)
        }

        private fun urlRequest(type: NetworkRequestType, url: String, body: String = "", customBlock: HttpURLConnection.() -> Unit = {}): Pair<Int, String> {
            val connection = (URL(url).openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = type.name
                connection.apply(customBlock) // Customization
                connection.doOutput = body.isNotEmpty()
                connection.doInput = true
                connection.connectTimeout = 1000
                connection.connect()
                if (body.isNotEmpty()) connection.outputStream.bufferedWriter().use {
                    it.write(body)
                    it.close()
                }
                connection.disconnect()
                return connection.responseCode to connection.responseMessage
            } catch (e: Exception) {
                Logger.error("URL error to $url $e")
            } finally {
                connection.disconnect()
            }
            return 0 to "FUCKTWAT"
        }

        /**
         * Returns the file's contents specified with path
         *
         * @param patḣ
         * @return File's contents
         */
        fun readFile(path: String): String = File(path).readText()
    }

}

/**
 * Runs the provided timer task after a specific amount of milliseconds ran.
 *
 * @param delay Time to delay in milliseconds.
 * @param block Lambda to execute after the delay.
 */
fun runAfter(delay: Long, block: () -> Unit) = Timer().schedule(delay) { block.invoke() }

/**
 * Parses the message with body of type T from the http request using gson (if possible).
 *
 * @param T Message's body type
 * @return Message with body type of T
 */
inline fun <reified T> Context.getMessage(): Message<T> = Utils.gson.fromJson<Message<T>>(body(), TypeToken.getParameterized(Message::class.java, T::class.java).type)