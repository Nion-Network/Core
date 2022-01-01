package utils

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import logging.Dashboard
import logging.Logger
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.schedule
import kotlin.concurrent.withLock
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/**
 * Created by Mihael Berčič
 * on 26/03/2020 14:45
 * using IntelliJ IDEA
 */

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

val String.asBitSet: BitSet get() = BitSet.valueOf(toBigInteger(16).toByteArray().reversedArray())

object TreeUtils {

    fun computeDepth(k: Int, index: Number): Int {
        val indexDouble = index.toDouble()
        return (ceil(log10((indexDouble + 1.0) * (k - 1) + 1) / log10(k.toDouble())) - 1).toInt()
    }

    fun computeTotalNodesOnDepth(k: Int, depth: Int): Int {
        return ((k.toDouble().pow(depth + 1.0) - 1) / (k - 1)).toInt()
    }

    fun computeMinimumIndexAtDepth(k: Int, totalAtDepth: Int, depth: Int): Int {
        return (totalAtDepth - k.toDouble().pow(depth)).toInt()
    }

    fun computeMaximumIndexAtDepth(totalAtDepth: Int): Int {
        return totalAtDepth - 1
    }

}

private val shaLock = ReentrantLock(true)

/** Digests [data] using SHA-256 hashing algorithm. */
fun sha256(data: ByteArray): ByteArray {
    return shaLock.withLock {
        MessageDigest.getInstance("SHA-256").let {
            it.update(data)
            it.digest()
        }
    }
}

/** Digests [data] using SHA-256 hashing algorithm. */
fun sha256(data: String) = sha256(data.encodeToByteArray())

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

fun launchCoroutine(block: () -> Unit) {
    GlobalScope.launch {
        tryAndReport(block)
    }
}

fun <T> Lock.tryWithLock(action: () -> T) = tryAndReport { withLock(action) }

fun <T> tryAndReport(block: () -> T): T? {
    try {
        return block()
    } catch (e: Exception) {
        Dashboard.reportException(e)
        Logger.reportException(e)
    }
    return null
}