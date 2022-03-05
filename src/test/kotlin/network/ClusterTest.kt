package network

import network.data.clusters.ClusterUtils
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Created by mihael
 * on 05/03/2022 at 13:33
 * using IntelliJ IDEA
 */
class ClusterTest {

    @Test
    fun computeClusters() {
        val clusters = ClusterUtils.computeClusters(10, 1, (0..100).toList()) { centroid, element ->
            abs(element - centroid)
        }
        clusters.forEach { (element, cluster) ->
            val centroid = cluster.centroid
            val isRepresentative = element == centroid
            if (isRepresentative) println("$centroid -> PRODUCER")
            println("$element -> $centroid")
        }

    }

}