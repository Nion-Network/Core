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
        val clusters = ClusterUtils.computeClusters(10, 5, (0..100).toList()) { centroid, element ->
            abs(element - centroid)
        }
        clusters.forEach { (centroid, elements) ->
            elements.forEach {
                println("$it -> $centroid")
            }
            println("$centroid -> PRODUCER")
        }

    }

}