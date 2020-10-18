package manager

import data.BlockVote
import data.VoteRequest
import data.VoteType
import io.javalin.http.Context
import utils.getMessage

/**
 * Created by Mihael Valentin Berčič
 * on 04/10/2020 at 17:17
 * using IntelliJ IDEA
 */
class CommitteeManager(private val applicationManager: ApplicationManager) {

    private val vdfManager by lazy { applicationManager.vdfManager }

    fun voteRequest(context: Context) {
        val message = context.getMessage<VoteRequest>()
        val voteRequest = message.body
        val block = voteRequest.block
        val producer = voteRequest.producer

        val blockVote = BlockVote(block.hash, applicationManager.crypto.sign(block.hash), VoteType.FOR)
        // applicationManager.dashboardManager.newVote(blockVote, DigestUtils.sha256Hex(applicationManager.crypto.publicKey))
        val messageToSend = applicationManager.generateMessage(blockVote)

        val isValidProof = vdfManager.verifyProof(block.difficulty, block.precedentHash, block.vdfProof)
        // if (isValidProof) // TODO

        producer.sendMessage("/vote", messageToSend)
    }

}