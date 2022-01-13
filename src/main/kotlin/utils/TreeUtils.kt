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

    /** Computes at which depth the [index] is located in a [k]-tree. */
    fun computeDepth(k: Int, index: Number): Int {
        val indexDouble = index.toDouble()
        return (ceil(log10((indexDouble + 1.0) * (k - 1) + 1) / log10(k.toDouble())) - 1).toInt()
    }

    /** Computes count of total nodes at [depth] in a [k]-tree. */
    fun computeTotalNodesOnDepth(k: Int, depth: Int): Int {
        return ((k.toDouble().pow(depth + 1.0) - 1) / (k - 1)).toInt()
    }

    /** Computes minimum index at [depth] in a [k]-tree. */
    fun computeMinimumIndexAtDepth(k: Int, totalNodesAtDepth: Int, depth: Int): Int {
        return (totalNodesAtDepth - k.toDouble().pow(depth)).toInt()
    }

    /** Returns the maximum index at the depth. Equals to Total Nodes on Depth - 1. */
    fun computeMaximumIndexAtDepth(totalNodesAtDepth: Int): Int {
        return totalNodesAtDepth - 1
    }

    /** Returns indexes of children of the parent at [index] in a [k]-tree. */
    fun findChildren(k: Int, index: Int): IntRange {
        val firstChildIndex = k * index + 1
        val lastChildIndex = k * (index + 1)
        return firstChildIndex..lastChildIndex
    }
}