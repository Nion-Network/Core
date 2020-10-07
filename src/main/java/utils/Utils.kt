package utils

import abstraction.Message
import abstraction.NetworkRequestType
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.javalin.http.Context
import org.apache.commons.codec.digest.DigestUtils
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Created by Mihael Berčič
 * on 26/03/2020 14:45
 * using IntelliJ IDEA
 */

val networkHistory = hashMapOf<String, Long>()


class Utils {

    companion object {

        val gson = GsonBuilder()
                .setPrettyPrinting() // For debugging...
                .create()

        fun <T> sendMessageTo(url: String, path: String = "/", message: Message<T>, type: NetworkRequestType = NetworkRequestType.POST): Pair<Int, String> = urlRequest(type, "$url$path", message.asJson) {
            this.addRequestProperty("hex", DigestUtils.sha256Hex(message.signature))
        }

        private fun urlRequest(type: NetworkRequestType, url: String, body: String = "", customBlock: HttpURLConnection.() -> Unit = {}): Pair<Int, String> = (URL(url).openConnection() as HttpURLConnection).let { connection ->
            connection.requestMethod = type.name
            connection.apply(customBlock) // Customization
            connection.doOutput = body.isNotEmpty()
            connection.doInput = true

            if (body.isNotEmpty()) connection.outputStream.bufferedWriter().use { it.write(body) }
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

inline fun <reified T> Context.getMessage(): Message<T> = Utils.gson.fromJson<Message<T>>(body(), TypeToken.getParameterized(Message::class.java, T::class.java).type)