package manager

import blockchain.Block
import blockchain.BlockVote
import blockchain.VoteType
import io.javalin.http.Context
import logging.Logger
import utils.getMessage
import utils.senderNode

/**
 * Created by Mihael Valentin Berčič
 * on 04/10/2020 at 17:17
 * using IntelliJ IDEA
 */
class CommitteeManager(applicationManager: ApplicationManager) {

    private val vdfManager by lazy { applicationManager.kotlinVDF }
    private val timeManager by lazy { applicationManager.timeManager }
    private val networkManager by lazy { applicationManager.networkManager }

    fun voteRequest(context: Context) {
        val message = context.getMessage<Block>()
        val fromNode = context.senderNode
        val block = message.body
        var vdfFound = false

        fromNode?.also { senderNode ->
            timeManager.runAfter(1000) { if (!vdfFound) Logger.debug("This is a TODO in CommitteeManager.kt L#26 (sending skip block)") }

            val proof = vdfManager.findProof(block.difficulty, block.hash, block.epoch).also { vdfFound = true }
            val blockVote = BlockVote(block.hash, VoteType.FOR, proof)
            val messageToSend = networkManager.nodeNetwork.createVoteMessage(blockVote)
            senderNode.sendMessage("/vote", messageToSend)
        }


    }

}