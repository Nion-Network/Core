package utils

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
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

    fun outputTree(k: Int, nodes: List<Any?>): String {
        val stringBuilder = StringBuilder()
        val depth = computeDepth(k, nodes.lastIndex)
        val distances = mutableMapOf<Int, Double>()
        val totalNodes = computeTotalNodesOnDepth(k, depth)
        val neededNodes = (0 until totalNodes - nodes.size).map { null }.toList()
        val filled = nodes.reversed().plus(neededNodes)

        filled.forEachIndexed { i, key ->
            val index = filled.lastIndex - i
            val currentDepth = TreeUtils.computeDepth(k, index)
            val nodesOnDepth = TreeUtils.computeTotalNodesOnDepth(k, currentDepth)
            val minIndex = computeMinimumIndexAtDepth(k, nodesOnDepth, currentDepth)
            val maxIndex = computeMaximumIndexAtDepth(nodesOnDepth)

            if (currentDepth == depth) {
                val center = (minIndex + maxIndex) / 2.0
                val distance = index - center
                distances[index] = distance
            } else {
                val children = findChildren(k, index).filter { filled.lastIndex >= it }
                val median = children.sumOf { distances[it]!! } / k
                distances[index] = median
            }

            val distance = distances[index] ?: return@forEachIndexed
            val node = nodes.getOrNull(index) ?: return@forEachIndexed

            val neighbourIndex = (index + 1).takeIf { it <= maxIndex } ?: minIndex
            val children = findChildren(k, index).mapNotNull { nodes.getOrNull(it) }
            val neighbourChildren = findChildren(k, neighbourIndex).filter { it <= nodes.size - 1 }

            val childrenConnections = children.joinToString("\n") { "\"$node\" -> \"$it\"" }
            val neighbourChildrenConnections = neighbourChildren.joinToString("\n") { "\"$node\" -> \"${nodes[it]}\" [color=\"red\"]" }


            val length = "$node".length
            val neighbourNode = nodes.getOrNull(neighbourIndex)

            if (neighbourNode != null) stringBuilder.append("\"$node\" -> \"$neighbourNode\" [color=\"green\"]\n")
            if (childrenConnections.isNotEmpty()) stringBuilder.append(childrenConnections + "\n")
            if (neighbourChildrenConnections.isNotEmpty()) stringBuilder.append(neighbourChildrenConnections + "\n")

            stringBuilder.append("\"$node\" [pos=\"${length * distance},-${length * currentDepth}!\"]\n")
            // stringBuilder.append("\"$node\" [pos=\"${distance},-$currentDepth!\"]\n")
        }
        // Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(stringBuilder.toString()), null)
        return stringBuilder.toString()
    }
}