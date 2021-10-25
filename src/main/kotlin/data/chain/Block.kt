package data.chain

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
    val timestamp: Long,
    val blockProducer: String,
    val precedentHash: String = "",
    val hash: String = Utils.sha256("$slot$difficulty$timestamp$precedentHash").asHex,
    var votes: Int = 0,
    val validatorChanges: Map<String, Boolean> = emptyMap(),
    val migrations: MutableMap<String, MigrationPlan> = mutableMapOf(),
    var vdfProof: String = ""
) {

    val seed by lazy {
        BigInteger(Utils.sha256(vdfProof).asHex, 16).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
    }
}