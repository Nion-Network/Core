package network.data.clusters

import logging.Logger

/**
 * Created by mihael
 * on 05/03/2022 at 13:30
 * using IntelliJ IDEA
 */
object ClusterUtils {

    data class Cluster<T>(var centroid: T, val elements: MutableList<T> = mutableListOf())

    fun <T> computeClusters(perCluster: Int, iterations: Int, elements: Collection<T>, distanceComputation: (centroid: T, element: T) -> Int): List<Cluster<T>> {
        if (elements.isEmpty()) return emptyList()
        val clusterCount = elements.size / perCluster + 1
        val clusters = elements.shuffled().take(clusterCount).map { Cluster(it) }
        var changeHappened = true
        val currentMapping = mutableMapOf<T, Cluster<T>>()

        for (iteration in 0..iterations) {
            if (!changeHappened) break
            changeHappened = false
            elements.forEach {
                val closestCluster = clusters.minByOrNull { cluster -> distanceComputation(cluster.centroid, it) }
                if (closestCluster != null) {
                    val currentCluster = currentMapping[it]
                    currentCluster?.elements?.remove(it)
                    closestCluster.elements.add(it)
                    currentMapping[it] = closestCluster
                } else Logger.error("Closest centroid does not exist!")
            }
            clusters.forEach { cluster ->
                val currentCentroid = cluster.centroid
                val centerOfElements = cluster.elements.sortedBy { distanceComputation(currentCentroid, it) }
                val elementCount = centerOfElements.size
                val middle = elementCount / 2
                val newCentroid = centerOfElements[middle + elementCount % 2]
                if (currentCentroid != newCentroid) {
                    changeHappened = true
                    cluster.centroid = newCentroid
                }
            }
        }
        return clusters
    }

}