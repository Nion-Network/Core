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

    fun outputTree(k: Int, nodes: List<Any>): String {
        val stringBuilder = StringBuilder()
        val depth = computeDepth(k, nodes.lastIndex)
        val distances = mutableMapOf<Int, Double>()
        nodes.reversed().forEachIndexed { i, key ->
            val index = nodes.size - 1 - i
            val currentDepth = computeDepth(k, index)
            val totalNodes = computeTotalNodesOnDepth(k, currentDepth)
            val minimumIndex = computeMinimumIndexAtDepth(k, totalNodes, currentDepth)
            val maximumIndex = computeMaximumIndexAtDepth(totalNodes)
            val neighbourIndex = (index + 1).takeIf { it <= maximumIndex } ?: minimumIndex
            if (currentDepth == depth) {
                val center = (minimumIndex + maximumIndex) / 2.0
                val distance = index - center
                distances[index] = distance
            } else {
                val children = findChildren(k, index).mapNotNull { nodes.getOrNull(it) }
                val median = children.sumOf { distances[nodes.indexOf(it)]!! } / k
                val childrenConnections = children.joinToString("\n") { "\"$key\" -> \"$it\"" }
                distances[index] = median
                stringBuilder.append(childrenConnections + "\n")
            }
            val ourDistance = distances[index]!!
            val neighbourChildren = findChildren(k, neighbourIndex).filter { it <= nodes.size - 1 }
            val neighbourChildrenConnections = neighbourChildren.joinToString("\n") { "\"$key\" -> \"${nodes[it]}\" [color=\"red\"]" }

            stringBuilder.apply {
                val length = "$key".length
                append(neighbourChildrenConnections + "\n")
                nodes.getOrNull(neighbourIndex)?.apply {
                    append("\"$key\" -> \"$this\" [color=\"green\"]\n")
                }
                append("\"$key\" [pos=\"${length * ourDistance},-${length * currentDepth}!\"]\n")
            }
        }
        return stringBuilder.toString()
        // Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(stringBuilder.toString()), null)
    }
}