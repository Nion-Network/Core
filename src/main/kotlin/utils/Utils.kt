package utils

import communication.Message
import data.Block
import data.NetworkRequestType
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.*
import kotlin.concurrent.schedule

/**
 * Created by Mihael Berčič
 * on 26/03/2020 14:45
 * using IntelliJ IDEA
 */

class Utils {

    companion object {


        private val digits = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D',
            'E', 'F'
        )

        /** Taken from DigestUtils dependency.
         *
         * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in order.
         * The returned array will be double the length of the passed array, as it takes two characters to represent any given byte.
         * */
        val ByteArray.asHex
            get() :String {
                val l = size
                val out = CharArray(l shl 1)
                var i = 0
                var j = 0
                while (i < l) {
                    out[j++] = digits[0xF0 and this[i].toInt() ushr 4]
                    out[j++] = digits[0x0F and this[i].toInt()]
                    i++
                }
                return out.joinToString("")
            }

        fun sha256(data: String) = sha256(data.encodeToByteArray())

        fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").let {
            it.update(data)
            it.digest()
        }

        fun sendFileTo(url: String, path: String = "/", file: File, containerName: String, type: NetworkRequestType = NetworkRequestType.POST): Pair<Int, String> =
            urlRequest(type, "$url$path", file) {
                this.addRequestProperty("hex", sha256(file.absolutePath).asHex)
                this.addRequestProperty("name", containerName)
                this.addRequestProperty("Content-Type", "multipart/form-data;")

                println("Request property hex: ${getRequestProperty("hex")}")
                println("Request property name: ${getRequestProperty("name")}")
            }

        private fun urlRequest(type: NetworkRequestType, url: String, body: Any, customBlock: HttpURLConnection.() -> Unit = {}): Pair<Int, String> {
            val connection = (URL(url).openConnection() as HttpURLConnection)
            try {
                connection.requestMethod = type.name
                connection.apply(customBlock) // Customization
                connection.doOutput = true
                connection.doInput = true
                // connection.connectTimeout = 1000
                connection.connect()
                connection.outputStream.use {
                    when (body) {
                        is String -> if (body.isNotEmpty()) it.write(body.toByteArray())
                        is File -> {
                            FileInputStream(body).apply {
                                transferTo(it)
                                close()
                            }
                        }
                        else -> {
                        }
                    }
                    it.flush()
                    it.close()
                }
                connection.disconnect()
                return connection.responseCode to connection.responseMessage
            } catch (e: Exception) {
                // Logger.error("URL error to $url $e")
            } finally {
                connection.disconnect()
            }
            return 0 to "What?"
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

// TODO Comment
fun levenshteinDistance(block: Block, lhs: String, rhs: String): Int {
    val len0 = lhs.length + 1
    val len1 = rhs.length + 1
    var cost = IntArray(len0)
    var newCost = IntArray(len0)

    for (i in 0 until len0) cost[i] = i
    for (j in 1 until len1) {
        newCost[0] = j
        for (i in 1 until len0) {
            val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
            val replaceCost = cost[i - 1] + match
            val insertCost = cost[i] + 1
            val deleteCost = newCost[i - 1] + 1
            newCost[i] = insertCost.coerceAtMost(deleteCost).coerceAtMost(replaceCost)
        }
        val swap = cost
        cost = newCost
        newCost = swap
    }
    return cost[len0 - 1]
}

/**
 * Runs the provided timer task after a specific amount of milliseconds ran.
 *
 * @param delay Time to delay in milliseconds.
 * @param block Lambda to execute after the delay.
 */
fun runAfter(delay: Long, block: () -> Unit) = Timer().schedule(delay) { block.invoke() }

/**
 * Decodes the [Message<T>] using ProtoBuf from the ByteArray.
 *
 * @param T What data does the Message contain.
 * @return Message with data of type T.
 */
inline fun <reified T> ByteArray.asMessage(): Message<T> = ProtoBuf.decodeFromByteArray(this)