package manager

import data.Block
import data.BlockVote
import data.ChainTask
import data.SlotDuty
import io.javalin.http.Context
import logging.Logger
import network.knownNodes
import org.apache.commons.codec.digest.DigestUtils
import utils.getMessage
import java.math.BigInteger
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 25/09/2020 at 16:58
 * using IntelliJ IDEA
 */
class ChainManager(private val applicationManager: ApplicationManager) {

    val isChainEmpty: Boolean get() = chain.isEmpty()

    private val lastBlock: Block? get() = chain.lastOrNull()
    private val votes = mutableListOf<BlockVote>()
    private val chain = mutableListOf<Block>()

    private val dhtManager by lazy { applicationManager.dhtManager }
    private val currentState by lazy { applicationManager.currentState }
    private val vdfManager by lazy { applicationManager.vdfManager }
    private val crypto by lazy { applicationManager.crypto }
    private val timeManager by lazy { applicationManager.timeManager }
    private val nodeNetwork by lazy { applicationManager.networkManager.nodeNetwork }
    private val configuration by lazy { applicationManager.configuration }
    private val blockProducer by lazy { applicationManager.blockProducer }
    private val validatorManager by lazy { applicationManager.validatorManager }


    fun addBlock(block: Block) {
        currentState.apply {
            currentSlot = block.slot
            currentEpoch = block.epoch
        }

        applicationManager.updateValidatorSet(block)
        chain.add(block)
        votes.clear()
        Logger.chain("Added block with [epoch][slot] => [${block.epoch}][${block.slot}] ")

        val nextTask = calculateNextDuties(block)

        when (nextTask.myTask) {
            SlotDuty.PRODUCER -> {
                if (++currentState.currentSlot == configuration.slotCount) {
                    currentState.currentEpoch++
                    currentState.currentSlot = 0
                    Logger.debug("Moved to next epoch!")
                }
                val newBlock = blockProducer.createBlock(block)
                val message = applicationManager.generateMessage(newBlock)

                timeManager.runAfter(1000) { nodeNetwork.broadcast("/voteRequest", message) }

                timeManager.runAfter(configuration.slotDuration * 2 / 3) {
                    newBlock.vdfProof = votes[0].vdfProof
                    val votesAmount = votes.size
                    val broadcastMessage = applicationManager.generateMessage(newBlock)

                    Logger.debug("We got $votesAmount votes and we're broadcasting...")
                    newBlock.votes = votesAmount
                    applicationManager.dasboardManager.newBlockProduced(newBlock)
                    nodeNetwork.broadcast("/block", broadcastMessage)
                    addBlock(newBlock)
                    newBlock.validatorChanges.forEach { (key, _) -> applicationManager.validatorSetChanges.remove(key) }
                }
            }
            SlotDuty.COMMITTEE, SlotDuty.VALIDATOR -> Unit
        }
    }

    fun requestSync() {
        val from = currentState.currentEpoch * configuration.slotCount + currentState.currentSlot
        val message = applicationManager.generateMessage(from)
        Logger.trace("Requesting new blocks from $from")
        nodeNetwork.sendMessageToRandomNodes("/syncRequest", 1, message)
    }

    fun syncRequestReceived(context: Context) {
        val message = context.getMessage<Int>()
        val blocks = chain.drop(message.body)
        val responseBlocksMessageBody = applicationManager.generateMessage(blocks)
        knownNodes[message.publicKey]?.sendMessage("/syncReply", responseBlocksMessageBody)
    }

    fun syncReplyReceived(context: Context) {
        val message = context.getMessage<Array<Block>>()
        val blocks = message.body
        Logger.info("We have ${blocks.size} blocks to sync...")
        blocks.forEach { block ->
            addBlock(block)
            currentState.currentSlot = block.slot
            currentState.currentEpoch = block.epoch
        }
        Logger.info("Syncing finished...")
        validatorManager.requestInclusion()
    }

    fun blockReceived(context: Context) {
        val message = context.getMessage<Block>()
        val newBlock = message.body

        nodeNetwork.broadcast("/block", message)

        if (newBlock.precedentHash == lastBlock?.hash ?: "") {
            addBlock(newBlock)
            // if (!applicationManager.isIncluded) validatorManager.requestInclusion()
        } else requestSync()
    }

    private fun calculateNextDuties(block: Block): ChainTask {
        val proof = block.vdfProof
        val hex = DigestUtils.sha256Hex(proof)
        val seed = BigInteger(hex, 16).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
        val random = Random(seed)
        val ourKey = crypto.publicKey

        val validatorSetCopy = applicationManager.currentValidators.toMutableList().shuffled(random).toMutableList()
        val blockProducerNode = validatorSetCopy[0].apply { validatorSetCopy.remove(this) }
        val committee = validatorSetCopy.take(configuration.committeeSize)

        // Logger.error("Block producer is: ${blockProducerNode.drop(30).take(15)}")
        val ourRole = when {
            blockProducerNode == ourKey -> SlotDuty.PRODUCER
            committee.contains(ourKey) -> SlotDuty.COMMITTEE
            else -> SlotDuty.VALIDATOR
        }

        Logger.debug("Next task: $ourRole")

        if (ourRole == SlotDuty.PRODUCER) committee.forEach(dhtManager::sendSearchQuery)
        return ChainTask(ourRole, committee)
    }

    fun voteReceived(context: Context) {
        val message = context.getMessage<BlockVote>()
        votes.add(message.body)
    }

    fun isVDFCorrect(proof: String) = chain.lastOrNull()?.let { vdfManager.verifyProof(it.difficulty, it.hash, proof) }
            ?: false
}