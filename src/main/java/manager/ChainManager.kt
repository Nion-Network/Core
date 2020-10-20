package manager

import data.*
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
    private val votes = mutableMapOf<String, MutableList<VoteInformation>>()
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


    /**
     * Adds the specified block to the chain and calculates our task for the next slot.
     *
     * @param block
     */
    fun addBlock(block: Block) {

        currentState.apply {
            currentSlot = block.slot
            currentEpoch = block.epoch
        }

        applicationManager.updateValidatorSet(block)
        chain.add(block)

        val nextTask = calculateNextDuties(block)

        Logger.chain("Added block with [epoch][slot] => [${block.epoch}][${block.slot}] Next task: \u001B[32m${nextTask.myTask}")

        when (nextTask.myTask) {
            SlotDuty.PRODUCER -> {
                if (++currentState.currentSlot == configuration.slotCount) {
                    currentState.currentEpoch++
                    currentState.currentSlot = 0
                }
                val vdfProof = vdfManager.findProof(block.difficulty, block.hash, block.epoch)
                val newBlock = blockProducer.createBlock(block, vdfProof)
                val voteRequest = VoteRequest(newBlock, applicationManager.ourNode)
                val message = applicationManager.generateMessage(voteRequest)

                timeManager.runAfter(500) { nodeNetwork.broadcast("/voteRequest", message) }

                timeManager.runAfter(configuration.slotDuration * 2 / 3) {

                    val thisBlockVotes = votes[newBlock.hash]
                    val votesAmount = thisBlockVotes?.size ?: 0
                    val broadcastMessage = applicationManager.generateMessage(newBlock)

                    Logger.chain("Votes: $votesAmount")
                    newBlock.votes = votesAmount
                    // applicationManager.dashboardManager.newBlockProduced(newBlock)
                    nodeNetwork.broadcast("/block", broadcastMessage)
                    addBlock(newBlock)
                    newBlock.validatorChanges.forEach { (key, _) -> applicationManager.validatorSetChanges.remove(key) }

                }
            }
            SlotDuty.COMMITTEE, SlotDuty.VALIDATOR -> Unit
        }
    }

    /**
     * Request blocks from a random known node needed for synchronization.
     *
     */
    fun requestSync() {
        val from = currentState.currentEpoch * configuration.slotCount + currentState.currentSlot
        val message = applicationManager.generateMessage(from)
        Logger.trace("Requesting new blocks from $from")
        nodeNetwork.sendMessageToRandomNodes("/syncRequest", 1, message)
    }

    /**
     * After synchronization request has been received, we send back the blocks node has asked us for.
     *
     * @param context Web request context.
     */
    fun syncRequestReceived(context: Context) {
        val message = context.getMessage<Int>()
        val blocks = chain.drop(message.body)
        val responseBlocksMessageBody = applicationManager.generateMessage(blocks)
        knownNodes[message.publicKey]?.sendMessage("/syncReply", responseBlocksMessageBody)
    }

    /**
     * Received blocks for chain synchronization.
     *
     * @param context Web request context.
     */
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

        if (ourRole == SlotDuty.PRODUCER) committee.forEach(dhtManager::sendSearchQuery)
        return ChainTask(ourRole, committee)
    }

    @Synchronized
    fun voteReceived(context: Context) {
        val message = context.getMessage<BlockVote>()
        val blockVote = message.body
        votes.getOrPut(blockVote.blockHash) { mutableListOf() }.add(VoteInformation(message.publicKey))
    }

}