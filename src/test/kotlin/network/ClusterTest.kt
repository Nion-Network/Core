package network

import network.data.clusters.Cluster
import network.data.clusters.ClusterUtils
import org.junit.jupiter.api.Test
import utils.asBitSet
import utils.asHex
import utils.sha256

/**
 * Created by mihael
 * on 05/03/2022 at 13:33
 * using IntelliJ IDEA
 */
class ClusterTest {

    @Test
    fun computeClusters() {
        val publicKeys = (0..100).map { "$it" }
        val clusters = ClusterUtils.computeClusters(10, 1, publicKeys) { centroid, element ->
            val elementBitSet = sha256(element).asHex.asBitSet
            val centroidBitset = sha256(centroid).asHex.asBitSet.apply { xor(elementBitSet) }
            centroidBitset.nextSetBit(0)
            // ToDo: Performance improvement.
        }
        publicKeys.forEach { element ->
            val cluster = clusters[element] ?: Cluster("null")
            val centroid = cluster.centroid
            val isRepresentative = element == centroid
            if (isRepresentative) println("$centroid -> PRODUCER")
            // println("$element -> $centroid")
        }

    }

}