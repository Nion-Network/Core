package chain.data

import docker.DockerStatistics
import docker.MigrationPlan
import kotlinx.serialization.Serializable
import utils.sha256
import java.math.BigInteger

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:46
 * using IntelliJ IDEA
 */
@Serializable
class Block(
    val slot: Long,
    val difficulty: Int,
    val blockProducer: String,
    val dockerStatistics: Set<DockerStatistics>,
    val vdfProof: String = "",
    val timestamp: Long,
    val precedentHash: ByteArray,
    val validatorChanges: Map<String, Boolean>,
    var votes: Int = 0,
    var committee: List<String> = listOf(),
    val votedMembers: MutableList<String> = mutableListOf(),
    val migrations: Map<String, MigrationPlan> = mutableMapOf(),
    val hash: ByteArray = sha256("$slot$blockProducer$difficulty$precedentHash$validatorChanges")
) {

    val seed by lazy {
        BigInteger(sha256(vdfProof)).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
    }
}