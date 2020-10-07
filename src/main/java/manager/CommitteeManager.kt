package manager

import blockchain.BlockVote
import blockchain.VoteType
import io.javalin.http.Context
import logging.Logger
import messages.NewBlockMessageBody
import network.knownNodes
import utils.getMessage

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
        // println(context.body())
        val message = context.getMessage<NewBlockMessageBody>()
        val body = message.body
        val fromNode = knownNodes[message.publicKey]
        val block = body.block
        var vdfFound = false

        Logger.info("Vote request has been received...")
        fromNode?.also { senderNode ->
            timeManager.runAfter(5000) { if (!vdfFound) Logger.debug("This is a TODO in CommitteeManager.kt L#26 ($vdfFound => sending skip block)") }

            val proof = vdfManager.findProof(block.difficulty, block.hash, block.epoch)
            vdfFound = true
            Logger.debug("ProofFound set to $vdfFound")
            val blockVote = BlockVote(block.hash, VoteType.FOR, proof)
            val messageToSend = networkManager.nodeNetwork.createVoteMessage(blockVote)
            senderNode.sendMessage("/vote", messageToSend)
        }


    }

}