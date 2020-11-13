package manager

import chain.BlockProducer
import data.*
import logging.Logger
import org.apache.commons.codec.digest.DigestUtils
import utils.runAfter
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 25/09/2020 at 16:58
 * using IntelliJ IDEA
 */
class ChainManager(private val networkManager: NetworkManager) {

    val isChainEmpty: Boolean get() = chain.isEmpty()

    private var isIncluded: Boolean = false
    private val crypto = networkManager.crypto
    private val configuration = networkManager.configuration
    private val currentState = networkManager.currentState
    private val vdf = networkManager.vdf
    private val dht = networkManager.dht
    private val dashboard = networkManager.dashboard
    private val knownNodes = networkManager.knownNodes

    private val lastBlock: Block? get() = chain.lastOrNull()
    private val votes = ConcurrentHashMap<String, MutableList<VoteInformation>>()
    private val chain = mutableListOf<Block>()

    val blockProducer = BlockProducer(crypto, configuration, currentState)
    val validatorManager = ValidatorManager(networkManager, this)


    /**
     * Adds the specified block to the chain and calculates our task for the next slot.
     *
     * @param block
     */
    fun addBlock(block: Block, fromSync: Boolean = false) {
        votes.remove(block.hash)
        currentState.apply {
            currentSlot = block.slot
            currentEpoch = block.epoch
        }

        if (!isIncluded && block.validatorChanges[crypto.publicKey] == true) isIncluded = true
        updateValidatorSet(block)
        chain.add(block)

        val nextTask = calculateNextDuties(block, !fromSync)

        val textColor = when (nextTask.myTask) {
            SlotDuty.PRODUCER -> Logger.green
            SlotDuty.COMMITTEE -> Logger.blue
            SlotDuty.VALIDATOR -> Logger.white
        }

        Logger.chain("Added block with [epoch][slot][votes] => [${block.epoch}][${block.slot}][${Logger.green}${block.votes}${Logger.reset}] Next task: $textColor${nextTask.myTask}")
        dashboard.newRole(nextTask, DigestUtils.sha256Hex(crypto.publicKey), currentState);
        when (nextTask.myTask) {
            SlotDuty.PRODUCER -> {
                val vdfProof = vdf.findProof(block.difficulty, block.hash)
                if (++currentState.currentSlot == configuration.slotCount) {
                    currentState.currentEpoch++
                    currentState.currentSlot = 0
                }
                val newBlock = blockProducer.createBlock(block, vdfProof)
                val voteRequest = VoteRequest(newBlock, networkManager.ourNode)

                runAfter(500) {
                    val message = networkManager.generateMessage(voteRequest)
                    nextTask.committee.forEach { key -> networkManager.knownNodes[key]?.sendMessage("/voteRequest", message) }
                }

                runAfter(configuration.slotDuration * 2 / 3) {

                    val thisBlockVotes = votes[newBlock.hash]
                    val votesAmount = thisBlockVotes?.size ?: 0
                    val broadcastMessage = networkManager.generateMessage(newBlock)

                    newBlock.votes = votesAmount
                    dashboard.newBlockProduced(newBlock)
                    networkManager.broadcast("/block", broadcastMessage)
                    addBlock(newBlock)
                    newBlock.validatorChanges.forEach { (key, _) -> currentState.inclusionChanges.remove(key) }
                }
            }
            SlotDuty.COMMITTEE, SlotDuty.VALIDATOR -> Unit
        }
    }

    /**
     * Request blocks from a random known node needed for synchronization.
     *
     */
    private fun requestSync() {
        val from = currentState.currentEpoch * configuration.slotCount + currentState.currentSlot
        val message = networkManager.generateMessage(from)
        Logger.trace("Requesting new blocks from $from")
        networkManager.sendMessageToRandomNodes("/syncRequest", 1, message)
    }

    /**
     * After synchronization request has been received, we send back the blocks node has asked us for.
     *
     * @param body Web request body.
     */
    fun syncRequestReceived(message: Message<Int>) {
        val blocks = chain.drop(message.body)
        val responseBlocksMessageBody = networkManager.generateMessage(blocks)
        knownNodes[message.publicKey]?.sendMessage("/syncReply", responseBlocksMessageBody)
    }

    /**
     * Received blocks for chain synchronization.
     *
     * @param context Web request context.
     */
    fun syncReplyReceived(message: Message<Array<Block>>) {
        val blocks = message.body
        Logger.info("We have ${blocks.size} blocks to sync...")
        blocks.forEach { block ->
            addBlock(block, true)
            currentState.currentSlot = block.slot
            currentState.currentEpoch = block.epoch
        }
        Logger.info("Syncing finished...")
    }

    fun blockReceived(message: Message<Block>) {
        val newBlock = message.body

        // Logger.info("Broadcasting ${newBlock.hash} [ECHO]")
        networkManager.broadcast("/block", message)

        if (newBlock.precedentHash == lastBlock?.hash ?: "") {
            addBlock(newBlock)
        } else {
            if (lastBlock != null) Logger.error("\n[${newBlock.epoch}][${newBlock.slot}]\nPrecedent: ${newBlock.precedentHash}\nLast: ${lastBlock?.hash}\nNew: ${newBlock.hash}")
            if (lastBlock?.hash != newBlock.hash) requestSync()
        }
    }

    private fun calculateNextDuties(block: Block, askForInclusion: Boolean = true): ChainTask {
        val proof = block.vdfProof
        val hex = DigestUtils.sha256Hex(proof)
        val seed = BigInteger(hex, 16).remainder(Long.MAX_VALUE.toBigInteger()).toLong()
        val random = Random(seed)
        val ourKey = crypto.publicKey

        val validatorSetCopy = currentState.currentValidators.toMutableList().shuffled(random).toMutableList()
        val blockProducerNode = validatorSetCopy[0].apply { validatorSetCopy.remove(this) }
        val committee = validatorSetCopy.take(configuration.committeeSize)

        if (askForInclusion && !isIncluded) validatorManager.requestInclusion(blockProducerNode)

        val ourRole = when {
            blockProducerNode == ourKey -> SlotDuty.PRODUCER
            committee.contains(ourKey) -> SlotDuty.COMMITTEE
            else -> SlotDuty.VALIDATOR
        }

        if (ourRole == SlotDuty.PRODUCER) committee.forEach(dht::searchFor)
        return ChainTask(ourRole, committee)
    }

    fun voteReceived(message: Message<BlockVote>) {
        val blockVote = message.body
        votes.getOrPut(blockVote.blockHash) { mutableListOf() }.add(VoteInformation(message.publicKey))
    }

    fun updateValidatorSet(block: Block) = block.validatorChanges.forEach { (publicKey, change) ->
        currentState.currentValidators.apply { if (change) add(publicKey) else remove(publicKey) }
    }

}