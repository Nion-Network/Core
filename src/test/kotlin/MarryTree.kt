import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utils.TreeUtils

/**
 * Created by mihael
 * on 31/12/2021 at 18:13
 * using IntelliJ IDEA
 */
class MarryTree {

    private val k = 9
    private val index = 72
    private val ourDepth = TreeUtils.computeDepth(k, index)
    private val totalOnDepth = TreeUtils.computeTotalNodesOnDepth(k, ourDepth)

    @Test
    fun depthComputation() {
        assertEquals(2, ourDepth)
    }

    @Test
    fun maxAtDepthComputation() {
        assertEquals(91, totalOnDepth)
    }

    @Test
    fun minimumIndexAtDepthComputation() {
        val min = TreeUtils.computeMinimumIndexAtDepth(k, totalOnDepth, ourDepth)
        assertEquals(10, min)
    }

    @Test
    fun maximumIndexAtDepthComputation() {
        val max = TreeUtils.computeMaximumIndexAtDepth(totalOnDepth)
        assertEquals(90, max)
    }
}