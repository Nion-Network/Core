package utils

import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/**
 * Created by mihael
 * on 02/01/2022 at 12:31
 * using IntelliJ IDEA
 */
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

    fun findChildren(k: Int, index: Int): IntRange {
        val firstChildIndex = k * index + 1
        val lastChildIndex = k * (index + 1)
        return firstChildIndex..lastChildIndex
    }
}