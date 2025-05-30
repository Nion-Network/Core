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
 * Holds information about a block in the blockchain.
 *
 * This class contains metadata and data associated with a blockchain block,
 * such as the block hash, previous block hash, timestamp, nonce, transactions,
 * and any other relevant consensus or application-specific details.
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

    /**
     * Lazy computation of the seed that is later used in the block computation.
     */
    val seed by lazy {
        BigInteger(sha256(vdfProof)).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
    }
}