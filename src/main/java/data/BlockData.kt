package data

import org.apache.commons.codec.digest.DigestUtils
import java.math.BigInteger

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 14:11
 * using IntelliJ IDEA
 */
data class BlockVote(val blockHash: String, val signature: String, val voteType: VoteType)

data class VoteRequest(val block: Block, val producer: Node)

data class VoteInformation(val from: String, val timestamp: Long = System.currentTimeMillis())

data class State(
    var epoch: Int,
    var slot: Int,
    var committeeIndex: Int,
    var currentDifficulty: Int
)

data class ChainTask(val myTask: SlotDuty, val blockProducer: String, val committee: List<String> = emptyList())


data class Block(
    val slot: Int,
    val difficulty: Int,
    val timestamp: Long,
    val committeeIndex: Int,
    val blockProducer: String,
    val precedentHash: String = "",
    val hash: String = DigestUtils.sha256Hex("$slot$difficulty$timestamp$committeeIndex$precedentHash"),
    var votes: Int = 0,
    val validatorChanges: Map<String, Boolean> = emptyMap(),
    val migrations: MutableMap<String, Migration> = mutableMapOf(),
    var vdfProof: String = ""
) {

    val getRandomSeed get(): Long = BigInteger(DigestUtils.sha256Hex(vdfProof), 16).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
}
