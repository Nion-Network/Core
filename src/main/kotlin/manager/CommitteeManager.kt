package manager

import communication.Message
import communication.TransmissionType
import data.BlockVote
import data.Endpoint
import data.VoteRequest
import data.VoteType
import logging.Dashboard
import logging.Logger
import utils.Crypto
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256

/**
 * Created by Mihael Valentin Berčič
 * on 04/10/2020 at 17:17
 * using IntelliJ IDEA
 *
 * Vote requests are being handled by this class.
 */
class CommitteeManager(
    private val networkManager: NetworkManager,
    private val crypto: Crypto,
    private val vdfManager: VerifiableDelayFunctionManager,
    private val dashboard: Dashboard
) {

    /** On vote request received, the block is verified and if correct, a positive vote is sent back. */
    fun voteRequest(message: Message<VoteRequest>) {
        val voteRequest = message.body
        val block = voteRequest.block
        val producer = voteRequest.producer

        val blockVote = BlockVote(block.hash, crypto.sign(block.hash.encodeToByteArray()).toString(), VoteType.FOR)

        dashboard.newVote(blockVote, sha256(crypto.publicKey).asHex)

        val isValidProof = vdfManager.verifyProof(block.difficulty, block.precedentHash, block.vdfProof)
        if (!isValidProof) Logger.error(block)
        else networkManager.sendUDP(Endpoint.VoteReceived, blockVote, TransmissionType.Unicast, producer)
    }

}