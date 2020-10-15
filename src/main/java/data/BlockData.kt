package data

import org.apache.commons.codec.digest.DigestUtils
import org.influxdb.annotation.Column
import org.influxdb.annotation.Measurement

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 14:11
 * using IntelliJ IDEA
 */

data class BlockVote(val blockHash: String, val voteType: VoteType, val vdfProof: String = "")

data class State(var currentEpoch: Int, var currentSlot: Int, var committeeIndex: Int, var currentDifficulty: Int)

data class ChainTask(val myTask: SlotDuty, val committee: List<String> = emptyList())


@Measurement(name = "block")
data class Block(@Column(name = "epoch")val epoch: Int,
                 @Column(name = "slot")val slot: Int,
                 @Column(name = "difficulty")val difficulty: Int,
                 @Column(name = "timestamp")val timestamp: Long,
                 @Column(name = "committeeIndex")val committeeIndex: Int,
                 @Column(name = "blockProducer") val blockProducer: String,
                 @Column(name = "previousHash", tag = true)val precedentHash: String = "",
                 @Column(name = "hash", tag = true)val hash: String = DigestUtils.sha256Hex("$epoch$slot$difficulty$timestamp$committeeIndex$precedentHash"),
                 @Column(name = "votes") var votes: Int,
                 val validatorChanges: Map<String, Boolean> = emptyMap(),
                 var vdfProof: String = "")
