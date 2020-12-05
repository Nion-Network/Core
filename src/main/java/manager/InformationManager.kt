package manager

import data.Block
import utils.Crypto
import utils.levenshteinDistance
import kotlin.math.abs
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 05/12/2020 at 10:57
 * using IntelliJ IDEA
 */
class InformationManager(private val crypto: Crypto, private val isTrusted: Boolean, private val dashboard: DashboardManager) {

    fun generateClusters(k: Int, maxIterations: Int, validators: Collection<String>, lastBlock: Block): String {
        val random = Random(lastBlock.getRandomSeed)
        var centroids = validators.shuffled(random).take(k)
        val clusters = mutableMapOf<String, MutableMap<String, Int>>()
        var lastState = clusters
        lateinit var myRepresentative: String
        for (iteration in 0 until maxIterations) {
            validators.forEach { validator ->
                val centroid = centroids.minBy { it levenshteinDistance validator }.orEmpty() // Not sure if we should handle this differently...
                clusters.computeIfAbsent(centroid) { mutableMapOf() }[validator] = centroid levenshteinDistance validator
                if (isTrusted) dashboard.logCluster(lastBlock.epoch, validator, centroid)

                if (validator == crypto.publicKey) myRepresentative = centroid
            }
            if (lastState == clusters) break
            lastState = clusters
            clusters.clear()
            centroids = clusters.mapNotNull { (_, connected) ->
                val averageDistance = connected.values.average()
                connected.minBy { (_, distance) -> abs(averageDistance - distance) }
            }.map { it.key }
        }
        if (centroids.contains(crypto.publicKey)) myRepresentative = crypto.publicKey
        return myRepresentative
    }

}