package data.chain

import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 14:11
 * using IntelliJ IDEA
 */
@Serializable
data class BlockVote(val blockHash: String, val signature: String, val voteType: VoteType)
