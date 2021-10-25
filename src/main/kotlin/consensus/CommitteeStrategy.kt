package consensus

import data.chain.Block
import data.chain.Vote
import data.chain.VoteRequest
import data.chain.VoteType
import data.communication.Message
import data.communication.TransmissionType
import data.network.Endpoint
import manager.VerifiableDelayFunctionManager
import network.Network
import utils.Crypto
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Created by Mihael Valentin Berčič
 * on 25/10/2021 at 10:03
 * using IntelliJ IDEA
 */
class CommitteeStrategy(private val network: Network, private val crypto: Crypto, private val vdf: VerifiableDelayFunctionManager) {

    private val votingLock = ReentrantLock()
    private val votes = ConcurrentHashMap<String, MutableList<Vote>>()

    fun requestVotes(block: Block, committee: Array<String>) {
        val voteRequest = VoteRequest(block, network.ourNode)
        network.searchAndSend(Endpoint.VoteRequest, TransmissionType.Unicast, voteRequest, *committee)
    }

    fun voteRequested(message: Message<VoteRequest>) {
        val voteRequest = message.body
        val blockInQuestion = voteRequest.block
        val verified = vdf.verifyProof(blockInQuestion.difficulty, blockInQuestion.precedentHash, blockInQuestion.vdfProof)
        val voteType = if (verified) VoteType.FOR else VoteType.AGAINST
        val vote = Vote(blockInQuestion.hash, voteType)
        network.send(Endpoint.Vote, TransmissionType.Unicast, vote, voteRequest.producer)
    }

    fun voteReceived(message: Message<Vote>) {
        val vote = message.body
        if (vote.voteType != VoteType.FOR) return
        votingLock.lock()
        votes.computeIfAbsent(vote.blockHash) { mutableListOf() }.add(vote)
        votingLock.unlock()
    }

    fun getVotes(block: Block): Block {
        return votingLock.withLock {
            val votes = votes[block.hash]
            val count = votes?.count() ?: 0
            block.copy(votes = count)
        }
    }

}