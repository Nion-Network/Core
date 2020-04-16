package utils

import abstraction.NetworkRequest
import org.sonatype.aether.transfer.TransferEvent
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

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

        fun urlRequest(type: NetworkRequest, url: String, body: String = "", customBlock: HttpsURLConnection.() -> Unit) {
            val connection: HttpsURLConnection = (URL(url).openConnection() as HttpsURLConnection).apply {
                requestMethod = type.name
                if(body.isNotEmpty()) doInput = true
            }
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