package data

import org.apache.commons.codec.digest.DigestUtils

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 14:11
 * using IntelliJ IDEA
 */

data class BlockVote(val blockHash: String, val voteType: VoteType, val vdfProof: String = "")

data class State(var currentEpoch: Int, var currentSlot: Int, var committeeIndex: Int, var currentDifficulty: Int)

data class ChainTask(val myTask: SlotDuty, val committee: List<String> = emptyList())

data class Block(val epoch: Int,
                 val slot: Int,
                 val difficulty: Int,
                 val timestamp: Long,
                 val committeeIndex: Int,
                 var vdfProof: String = "",
                 val precedentHash: String = "",
                 val validatorChanges: Map<String, Boolean> = emptyMap(),
                 val hash: String = DigestUtils.sha256Hex("$epoch$slot$difficulty$timestamp$committeeIndex$precedentHash"))
