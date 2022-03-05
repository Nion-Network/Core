package network.data.clusters

/**
 * Created by mihael
 * on 05/03/2022 at 16:56
 * using IntelliJ IDEA
 */
data class Cluster<T>(var centroid: T, val elements: MutableList<T> = mutableListOf())