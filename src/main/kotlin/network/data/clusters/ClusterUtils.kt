package network.data.clusters

/**
 * Created by mihael
 * on 05/03/2022 at 13:30
 * using IntelliJ IDEA
 */
object ClusterUtils {

    /** Generates clusters based on [k-mean clustering algorithm](https://en.wikipedia.org/wiki/K-means_clustering). */
    fun <T> computeClusters(perCluster: Int, iterations: Int, elements: Collection<T>, distanceComputation: (centroid: T, element: T) -> Int): Map<T, Cluster<T>> {
        if (elements.isEmpty()) return emptyMap()
        val clusterCount = elements.size / perCluster + 1
        val clusters = elements.take(clusterCount).map { Cluster(it) }
        var changeHappened = true
        val currentMapping = mutableMapOf<T, Cluster<T>>()

        for (iteration in 0..iterations) {
            // if (!changeHappened) break
            changeHappened = false
            clusters.forEach { cluster ->
                val currentCentroid = cluster.centroid
                val centerOfElements = cluster.elements.sortedBy { distanceComputation(currentCentroid, it) }
                val elementCount = centerOfElements.size
                val middle = elementCount / 2
                val newCentroid = centerOfElements.getOrNull(middle + elementCount % 2) ?: return@forEach
                if (currentCentroid != newCentroid) {
                    changeHappened = true
                    cluster.centroid = newCentroid
                }
            }
            elements.forEach {
                val closestCluster = clusters.minByOrNull { cluster -> distanceComputation(cluster.centroid, it) }
                if (closestCluster != null) {
                    val currentCluster = currentMapping[it]
                    currentCluster?.elements?.remove(it)
                    closestCluster.elements.add(it)
                    currentMapping[it] = closestCluster
                }
            }
        }
        return currentMapping
    }

}