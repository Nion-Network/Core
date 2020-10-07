package manager

import blockchain.Block
import blockchain.BlockVote
import io.javalin.http.Context
import logging.Logger
import messages.NewBlockMessageBody
import messages.RequestBlocksMessageBody
import messages.ResponseBlocksMessageBody
import org.apache.commons.codec.digest.DigestUtils
import state.ChainTask
import utils.getMessage
import java.math.BigInteger
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 25/09/2020 at 16:58
 * using IntelliJ IDEA
 */
class ChainManager(private val applicationManager: ApplicationManager) {

    val lastBlock: Block? get() = chain.lastOrNull()

    val votes = mutableListOf<BlockVote>()
    val chain = mutableListOf<Block>()
    private val vdf by lazy { applicationManager.kotlinVDF }
    private val crypto by lazy { applicationManager.crypto }
    private val dht by lazy { applicationManager.dhtManager }
    private val timeManager by lazy { applicationManager.timeManager }
    private val nodeNetwork by lazy { applicationManager.networkManager.nodeNetwork }
    private val configuration by lazy { applicationManager.configuration }
    private val blockProducer by lazy { applicationManager.blockProducer }
    private val validatorManager by lazy { applicationManager.validatorManager }

    private var nextTask = ChainTask(Doodie.VALIDATOR)


    fun addBlock(block: Block) {
        chain.add(block)
        applicationManager.currentValidators.apply {
            block.validatorChanges.forEach { (publicKey, change) ->
                if (change) add(publicKey.apply { Logger.info("Adding one public key!") })
                else remove(publicKey.apply { Logger.info("Deleting one public key!") })
            }
        }
        calculateNextDuties(block.vdfProof)
        votes.clear()
        when (nextTask.myTask) {
            Doodie.PRODUCER -> {
                val newBlock = blockProducer.createBlock(block)
                val message = nodeNetwork.createNewBlockMessage(newBlock)
                timeManager.runAfter(1000) {
                    nodeNetwork.broadcast("/voteRequest", message)
                }
                timeManager.runAfter(configuration.slotDuration * 2 / 3) {
                    val votesAmount = votes.size
                    newBlock.vdfProof = votes[0].vdfProof
                    val broadcastMessage = nodeNetwork.createNewBlockMessage(newBlock)
                    Logger.debug("Broadcasting what we have collected... We got $votesAmount votes...")
                    nodeNetwork.broadcast("/block", broadcastMessage)
                    addBlock(newBlock)
                    applicationManager.validatorSetChanges.clear()
                }
            }
            Doodie.COMMITTEE -> Logger.debug("We're committee the next block...")
            Doodie.VALIDATOR -> Logger.debug("We're validator the next block...")
        }
    }

    fun runVDF(onBlock: Block) = vdf.findProof(onBlock.difficulty, onBlock.hash, onBlock.epoch)

    fun isVDFCorrect(proof: String) = chain.lastOrNull()?.let { lastBlock ->
        vdf.verifyProof(lastBlock.difficulty, lastBlock.hash, proof)
    } ?: false

    fun requestSync(fromHeight: Int) {
        Logger.info("Requesting new blocks from $fromHeight")
        val message = nodeNetwork.createRequestBlocksMessage(fromHeight)
        nodeNetwork.sendMessageToRandomNodes("/syncRequest", 1, message)
    }

    fun syncRequestReceived(context: Context) {
        val message = context.getMessage<RequestBlocksMessageBody>()
        val blockMessage = message.body
        Logger.debug("Received request for sync from epoch: ${blockMessage.epoch}")

        Logger.debug("Sending back a response with blocks to sync...")
        val blocks = chain.drop(blockMessage.epoch)
        val responseBlocksMessageBody = nodeNetwork.createResponseBlocksMessage(blocks)
        blockMessage.node.sendMessage("/syncReply", responseBlocksMessageBody)
    }

    fun syncReplyReceived(context: Context) {
        val message = context.getMessage<ResponseBlocksMessageBody>()
        val body = message.body
        val blocks = body.blocks

        Logger.info("We have ${blocks.size} blocks to sync...")
        blocks.forEach { block ->
            chain.add(block)
            applicationManager.currentState.ourSlot = block.slot
            applicationManager.currentState.currentEpoch = block.epoch
        }
        validatorManager.requestInclusion()
    }

    fun blockReceived(context: Context) {
        val message = context.getMessage<NewBlockMessageBody>()
        val body = message.body
        val newBlock = body.block
        Logger.chain("Block received...")
        if (newBlock.precedentHash == lastBlock?.hash ?: "") addBlock(newBlock)
    }

    private fun calculateNextDuties(proof: String) {
        Logger.error("Calculating duties with proof: $proof")
        val hex = DigestUtils.sha256Hex(proof)
        val seed = BigInteger(hex, 16).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
        val random = Random(seed)
        val ourKey = crypto.publicKey

        val validatorSetCopy = applicationManager.currentValidators.toMutableList().shuffled(random).toMutableList()
        val blockProducerNode = validatorSetCopy[0].apply { validatorSetCopy.remove(this) }
        val committee = validatorSetCopy.take(configuration.committeeSize)
        validatorSetCopy.removeAll(committee)

        val weProduce = blockProducerNode == ourKey
        val weCommittee = committee.contains(ourKey)

        Logger.info("Info for next block[${chain.size}] :\tWe produce: $weProduce\tWe committee: $weCommittee")
        val ourRole = when {
            weProduce -> Doodie.PRODUCER
            weCommittee -> Doodie.COMMITTEE
            else -> Doodie.VALIDATOR
        }

        if (ourRole == Doodie.PRODUCER) committee.forEach(dht::sendSearchQuery)

        nextTask = ChainTask(ourRole, committee)
    }

    fun voteReceived(context: Context) {
        val message = context.getMessage<BlockVote>()
        votes.add(message.body)
    }

}

enum class Doodie { PRODUCER, COMMITTEE, VALIDATOR }