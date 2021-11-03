package utils

import logging.Dashboard
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

        /** Digests [data] using SHA-256 hashing algorithm. */
        fun sha256(data: ByteArray): ByteArray {
            return MessageDigest.getInstance("SHA-256").let {
                it.update(data)
                it.digest()
            }
        }

    }

}

/** Executes [block] after [delay in milliseconds][delay]. */
fun runAfter(delay: Long, block: () -> Unit) {
    Timer().schedule(delay) {
        try {
            block.invoke()
        } catch (e: Exception) {
            Dashboard.reportException(e)
        }
    }
}