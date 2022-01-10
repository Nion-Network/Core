import docker.DockerContainer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utils.CircularList
import utils.TreeUtils

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
        val container = DockerContainer("", 10, CircularList(5), CircularList(5))
        val x = ProtoBuf.encodeToByteArray(container)
        val y = ProtoBuf.decodeFromByteArray<DockerContainer>(x)

        assertEquals(y, container)
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
}