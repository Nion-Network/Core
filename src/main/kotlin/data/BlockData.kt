package data

import kotlinx.serialization.Serializable
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256
import java.math.BigInteger

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 14:11
 * using IntelliJ IDEA
 */
@Serializable
data class BlockVote(val blockHash: String, val signature: String, val voteType: VoteType)

@Serializable
data class VoteRequest(val block: Block, val producer: Node)

@Serializable
data class VoteInformation(val from: String, val timestamp: Long = System.currentTimeMillis())

@Serializable
data class ChainTask(val myTask: SlotDuty, val blockProducer: String, val committee: List<String> = emptyList())

@Serializable
data class Block(
    val slot: Int,
    val difficulty: Int,
    val timestamp: Long,
    val committeeIndex: Int,
    val blockProducer: String,
    val precedentHash: String = "",
    val hash: String = sha256("$slot$difficulty$timestamp$committeeIndex$precedentHash").asHex,
    var votes: Int = 0,
    val validatorChanges: Map<String, Boolean> = emptyMap(),
    val migrations: MutableMap<String, Migration> = mutableMapOf(),
    var vdfProof: String = ""
) {

    val seed get(): Long = BigInteger(sha256(vdfProof).asHex, 16).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
}
