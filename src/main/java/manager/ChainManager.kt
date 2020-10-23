package manager

import data.*
import logging.Logger
import network.knownNodes
import org.apache.commons.codec.digest.DigestUtils
import utils.networkHistory
import utils.toMessage
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

        val textColor = when (nextTask.myTask) {
            SlotDuty.PRODUCER -> Logger.green
            SlotDuty.COMMITTEE -> Logger.blue
            SlotDuty.VALIDATOR -> Logger.white
        }

        Logger.chain("Added block with [epoch][slot][votes] => [${block.epoch}][${block.slot}][${block.votes}] Next task: $textColor${nextTask.myTask}")

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

                timeManager.runAfter(500) {
                    nextTask.committee.forEach { key -> knownNodes[key]?.sendMessage("/voteRequest", message) }
                }

                timeManager.runAfter(configuration.slotDuration * 2 / 3) {

                    val thisBlockVotes = votes[newBlock.hash]
                    val votesAmount = thisBlockVotes?.size ?: 0
                    val broadcastMessage = applicationManager.generateMessage(newBlock)

                    newBlock.votes = votesAmount
                    applicationManager.dashboardManager.newBlockProduced(newBlock)
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
        val from = currentState.currentEpoch * configuration.slotCount + currentState.currentSlot + 1
        val message = applicationManager.generateMessage(from)
        Logger.trace("Requesting new blocks from $from")
        nodeNetwork.sendMessageToRandomNodes("/syncRequest", 1, message)
    }

    /**
     * After synchronization request has been received, we send back the blocks node has asked us for.
     *
     * @param body Web request body.
     */
    fun syncRequestReceived(body: String) {
        val message = body.toMessage<Int>()
        val blocks = chain.drop(message.body)
        val responseBlocksMessageBody = applicationManager.generateMessage(blocks)
        knownNodes[message.publicKey]?.sendMessage("/syncReply", responseBlocksMessageBody)
    }

    /**
     * Received blocks for chain synchronization.
     *
     * @param context Web request context.
     */
    fun syncReplyReceived(body: String) {
        val message = body.toMessage<Array<Block>>()
        val blocks = message.body
        Logger.info("We have ${blocks.size} blocks to sync...")
        blocks.forEach { block ->
            addBlock(block)
            currentState.currentSlot = block.slot
            currentState.currentEpoch = block.epoch
        }
        Logger.info("Syncing finished...")
        if (blocks.isEmpty()) validatorManager.requestInclusion()
    }

    fun blockReceived(body: String) {
        val message = body.toMessage<Block>() // TODO fucking make this shit prettier
        val newBlock = message.body

        nodeNetwork.broadcast("/block", message)

        if (newBlock.validatorChanges[crypto.publicKey] == true) applicationManager.isIncluded = true
        if (newBlock.precedentHash == lastBlock?.hash ?: "") {
            addBlock(newBlock)
            if (!applicationManager.isIncluded) validatorManager.requestInclusion()
        } else {
            Logger.error("\t${newBlock.precedentHash}\n\t ${lastBlock?.hash}\n\t ${newBlock.hash}")
            Logger.error("For block: ${newBlock.epoch} | ${newBlock.slot}")
            if (lastBlock?.hash != newBlock.hash) requestSync()
        }
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

    fun voteReceived(body: String) {
        val message = body.toMessage<BlockVote>()
        val blockVote = message.body
        votes.getOrPut(blockVote.blockHash) { mutableListOf() }.add(VoteInformation(message.publicKey))
    }

}