package blockchain

import org.apache.commons.codec.digest.DigestUtils

data class Block(val epoch: Int,
                 val slot: Int,
                 val difficulty: Int,
                 val timestamp: Long,
                 val committeeIndex: Int,
                 var vdfProof: String = "",
                 val precedentHash: String = "",
                 val validatorChanges: Map<String, Boolean> = emptyMap(),
                 val hash: String = DigestUtils.sha256Hex("$epoch$slot$difficulty$timestamp$committeeIndex$precedentHash"))

data class BlockVote(val blockHash: String, val voteType: VoteType, val vdfProof: String = "")


enum class VoteType { FOR, AGAINST, SKIP }