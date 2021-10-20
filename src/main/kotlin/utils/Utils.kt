package utils

import communication.Message
import data.Block
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
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


/** Launches a new coroutine that executes the [block] and reports any exceptions caught to the dashboard. */
fun coroutineAndReport(dashboard: Dashboard, block: () -> Unit) {
    GlobalScope.launch {
        try {
            block()
        } catch (e: Exception) {
            dashboard.reportException(e)
        }
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

/** Executes [block] after [delay in milliseconds][delay]. Reports an exception to [dashboard] (testing only). */
fun runAfter(dashboard: Dashboard, delay: Long, block: () -> Unit) {
    Timer().schedule(delay) {
        try {
            block.invoke()
        } catch (e: Exception) {
            dashboard.reportException(e)
        }
    }
}

/**
 * Decodes the [Message<T>] using ProtoBuf from the ByteArray.
 *
 * @param T What data does the Message contain.
 * @return Message with data of type T.
 */
inline fun <reified T> ByteArray.asMessage(): Message<T> {
    return ProtoBuf.decodeFromByteArray(this)
}