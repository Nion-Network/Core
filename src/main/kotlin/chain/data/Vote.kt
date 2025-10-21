package chain.data

import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 14:11
 * using IntelliJ IDEA
 *
 * Holds information about the vote that has been aggregated through the network.
 * @publicKey The public key of the node that has given the vote.
 */
@Serializable
class Vote(val blockHash: ByteArray, val voteType: VoteType, val publicKey: String)
