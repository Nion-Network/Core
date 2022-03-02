import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utils.TreeUtils
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Created by mihael
 * on 31/12/2021 at 18:13
 * using IntelliJ IDEA
 */
class MarryTreeTest {

    private val k = 3
    private val index = 8
    private val depth = TreeUtils.computeDepth(k, index)
    private val totalOnDepth = TreeUtils.computeTotalNodesOnDepth(k, depth)

    @Test
    fun depthComputation() {
        assertEquals(2, depth)
    }

    @Test
    fun maxAtDepthComputation() {
        assertEquals(13, totalOnDepth)
    }

    @Test
    fun minimumIndexAtDepthComputation() {
        val min = TreeUtils.computeMinimumIndexAtDepth(k, totalOnDepth, depth)
        assertEquals(4, min)
    }

    @Test
    fun maximumIndexAtDepthComputation() {
        val max = TreeUtils.computeMaximumIndexAtDepth(totalOnDepth)
        assertEquals(12, max)
    }

    @Test
    fun findChildren() {
        val children = TreeUtils.findChildren(k, index)
        assertEquals(25..27, children)
    }

    @Test
    fun findNeighbour() {
        val minIndex = TreeUtils.computeMinimumIndexAtDepth(k, totalOnDepth, depth)
        val maxIndex = TreeUtils.computeMaximumIndexAtDepth(totalOnDepth)
        val neighbour = (12 + 1).takeIf { it <= maxIndex } ?: minIndex
        assertEquals(4, neighbour)
    }

    @Test
    fun outputTreeConnections() {
        val k = 2
        val depth = 2
        val maximumNodes = TreeUtils.computeTotalNodesOnDepth(k, depth)
        val tree = (0 until maximumNodes).toList()
        val stringBuilder = StringBuilder()
        val distances = mutableMapOf<Int, Double>()
        /*̣̣tree.reversed().forEach { index ->
            val currentDepth = TreeUtils.computeDepth(k, index)
            val totalNodes = TreeUtils.computeTotalNodesOnDepth(k, currentDepth)
            val minimumIndex = TreeUtils.computeMinimumIndexAtDepth(k, totalNodes, currentDepth)
            val maximumIndex = TreeUtils.computeMaximumIndexAtDepth(totalNodes)
            val neighbourIndex = (index + 1).takeIf { it <= maximumIndex } ?: minimumIndex
            if (currentDepth == depth) {
                val center = (minimumIndex + maximumIndex) / 2.0
                val distance = index - center
                distances[index] = distance
            } else {
                val children = TreeUtils.findChildren(k, index)
                val median = children.sumOf { distances[it]!! } / k
                val childrenConnections = children.joinToString("\n") { "$index -> $it" }
                distances[index] = median
                stringBuilder.append(childrenConnections + "\n")
            }
            val ourDistance = distances[index]!!
            val neighbourChildren = TreeUtils.findChildren(k, neighbourIndex).filter { it <= maximumNodes - 1 }
            val neighbourChildrenConnections = neighbourChildren.joinToString("\n") { "$index -> $it[color=\"red\"]" }

            stringBuilder.apply {
                append(neighbourChildrenConnections + "\n")
                append("$index -> $neighbourIndex [color=\"green\"]\n")
                append("$index [pos=\"$ourDistance,-$currentDepth!\"]\n")
            }
        }
         */
        // print(stringBuilder)

        print(TreeUtils.outputTree(k, tree))
        // Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(TreeUtils.outputTree(k, tree)), null)
    }
}