package manager

import data.Block
import data.BlockVote
import data.VoteType
import io.javalin.http.Context
import logging.Logger
import network.knownNodes
import org.apache.commons.codec.digest.DigestUtils
import utils.getMessage

/**
 * Created by Mihael Valentin Berčič
 * on 04/10/2020 at 17:17
 * using IntelliJ IDEA
 */
class CommitteeManager(private val applicationManager: ApplicationManager) {

    private val vdfManager by lazy { applicationManager.vdfManager }
    private val timeManager by lazy { applicationManager.timeManager }

    fun voteRequest(context: Context) {
        val message = context.getMessage<Block>()
        val publicKey = message.publicKey
        val fromNode = knownNodes[publicKey]

        // Logger.info("Vote request has been received...")
        if (fromNode == null) applicationManager.dhtManager.sendSearchQuery(publicKey)

        val block = message.body
        var vdfFound = false

        timeManager.runAfter(5000) { if (!vdfFound) Logger.debug("This is a TODO in CommitteeManager.kt L#26 ($vdfFound => sending skip block)") }

        val proof = vdfManager.findProof(block.difficulty, block.hash, block.epoch)
        vdfFound = true
        val blockVote = BlockVote(block.hash, proof, applicationManager.crypto.sign(block.hash), VoteType.FOR)
        applicationManager.dashboardManager.newVote(blockVote, DigestUtils.sha256Hex(applicationManager.crypto.publicKey))
        val messageToSend = applicationManager.generateMessage(blockVote)

        knownNodes[publicKey]?.sendMessage("/vote", messageToSend)

    }

}