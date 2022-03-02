package chain.data

import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 14:11
 * using IntelliJ IDEA
 */
@Serializable
class Vote(val blockHash: ByteArray, val voteType: VoteType)
