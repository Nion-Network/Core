package blockchain

import org.apache.commons.codec.digest.DigestUtils
import org.influxdb.annotation.Column
import org.influxdb.annotation.Measurement

@Measurement(name = "block")
data class Block(@Column(name = "epoch")val epoch: Int,
                 @Column(name = "slot")val slot: Int,
                 @Column(name = "difficulty")val difficulty: Int,
                 @Column(name = "timestamp")val timestamp: Long,
                 @Column(name = "committeeIndex")val committeeIndex: Int,
                 var vdfProof: String = "",
                 @Column(name = "previousHash", tag = true)val precedentHash: String = "",
                 val validatorChanges: Map<String, Boolean> = emptyMap(),
                 @Column(name = "hash", tag = true)val hash: String = DigestUtils.sha256Hex("$epoch$slot$difficulty$timestamp$committeeIndex$precedentHash"))

data class BlockVote(val blockHash: String, val voteType: VoteType, val vdfProof: String = "")


enum class VoteType { FOR, AGAINST, SKIP }