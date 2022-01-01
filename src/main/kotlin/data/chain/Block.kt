package data.chain

import data.docker.DockerStatistics
import data.docker.MigrationPlan
import kotlinx.serialization.Serializable
import utils.Utils
import utils.Utils.Companion.asHex
import java.math.BigInteger

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:46
 * using IntelliJ IDEA
 */
@Serializable
data class Block(
    val slot: Long,
    val difficulty: Int,
    val blockProducer: String,
    val dockerStatistics: List<DockerStatistics>,
    val vdfProof: String = "",
    val timestamp: Long,
    val precedentHash: String,
    val validatorChanges: Map<String, Boolean>,
    val votes: Int = 0,
    val migrations: Map<String, MigrationPlan> = mutableMapOf(),
    val hash: String = Utils.sha256("$slot$difficulty$precedentHash").asHex
) {

    val seed by lazy {
        BigInteger(Utils.sha256(vdfProof).asHex, 16).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
    }
}