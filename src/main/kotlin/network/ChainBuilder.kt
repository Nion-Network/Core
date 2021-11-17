package network

import Nion
import data.chain.Block
import data.chain.SlotDuty
import data.communication.InclusionRequest
import data.communication.Message
import data.communication.SyncRequest
import data.communication.TransmissionType
import data.network.Endpoint
import logging.Dashboard
import logging.Logger
import utils.runAfter
import kotlin.math.max

/**
 * Created by Mihael Valentin Berčič
 * on 06/11/2021 at 13:50
 * using IntelliJ IDEA
 */
class ChainBuilder(private val nion: Nion) {

    private val configuration = nion.configuration
    private val validatorSet = ValidatorSet(nion.localNode, nion.isTrustedNode)
    private val verifiableDelay = VerifiableDelay()
    private val chain = Chain(verifiableDelay, configuration.initialDifficulty)

    @MessageEndpoint(Endpoint.NewBlock)
    fun blockReceived(message: Message) {
        val block = message.decodeAs<Block>()
        Logger.chain("Block ${block.slot} has been received and is being processed.")
        if (chain.addBlocks(block)) {
            Logger.chain("Added block ${block.slot} with ${block.votes}/${configuration.committeeSize} votes.")
            block.validatorChanges.forEach { (key, wasAdded) ->
                validatorSet.inclusionChange(key, wasAdded)
            }
            if (nion.isTrustedNode) Dashboard.newBlockProduced(block, nion.knownNodeCount, validatorSet.validatorCount)
            if (!validatorSet.isInValidatorSet) requestInclusion()
            val nextTask = validatorSet.computeNextTask(block, configuration.committeeSize)
            Logger.debug("Task for ${block.slot} is ${nextTask.myTask}")
            when (nextTask.myTask) {
                SlotDuty.PRODUCER -> {
                    val computationStart = System.currentTimeMillis()
                    val proof = verifiableDelay.computeProof(block.difficulty, block.hash)
                    val computationDuration = System.currentTimeMillis() - computationStart
                    val committeeMembers = nextTask.committee.toTypedArray()
                    nion.queryFor(*committeeMembers)
                    runAfter(max(0, configuration.slotDuration - computationDuration)) {
                        val newBlock = Block(
                            block.slot + 1,
                            difficulty = configuration.initialDifficulty,
                            timestamp = System.currentTimeMillis(),
                            vdfProof = proof,
                            blockProducer = nion.localNode.publicKey,
                            validatorChanges = validatorSet.getScheduledChanges(),
                            precedentHash = block.hash
                        )
                        nion.send(Endpoint.NewBlock, TransmissionType.Broadcast, newBlock, *committeeMembers)
                    }
                }
                SlotDuty.COMMITTEE -> {
                    nion.send(Endpoint.NewBlock, TransmissionType.Unicast, block, nextTask.blockProducer)
                }
                SlotDuty.VALIDATOR -> {}
            }
        } else {
            val lastBlock = chain.getLastBlock()
            val lastSlot = lastBlock?.slot ?: 0
            Logger.error("Block ${block.slot} has been rejected. Our last slot is $lastSlot.")
            val slotDifference = block.slot - lastSlot
            if (slotDifference > 1) requestSynchronization()
        }
    }

    @MessageEndpoint(Endpoint.InclusionRequest)
    fun inclusionRequested(message: Message) {
        val inclusionRequest = message.decodeAs<InclusionRequest>()
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        if (ourSlot == inclusionRequest.currentSlot) validatorSet.scheduleChange(inclusionRequest.publicKey, true)
        if (nion.isTrustedNode && chain.getLastBlock() == null) {
            val scheduledChanges = validatorSet.getScheduledChanges().count { it.value }
            val isEnoughToStart = scheduledChanges > configuration.committeeSize
            if (isEnoughToStart) {
                val proof = verifiableDelay.computeProof(configuration.initialDifficulty, "FFFF")
                val genesisBlock = Block(1, configuration.initialDifficulty, nion.localNode.publicKey, proof, System.currentTimeMillis(), "", validatorSet.getScheduledChanges())
                nion.send(Endpoint.NewBlock, TransmissionType.Broadcast, genesisBlock)
                Logger.chain("Broadcasting genesis block to $scheduledChanges nodes!")
            }
        }
    }

    @MessageEndpoint(Endpoint.SyncRequest)
    fun synchronizationRequested(message: Message) {
        val syncRequest = message.decodeAs<SyncRequest>()
        val blocksToSendBack = chain.getLastBlocks(syncRequest.fromSlot)
        val requestingNode = syncRequest.node
        nion.addNewNodes(requestingNode)
        nion.send(Endpoint.SyncReply, TransmissionType.Unicast, blocksToSendBack, requestingNode.publicKey)
    }

    @MessageEndpoint(Endpoint.SyncReply)
    fun synchronizationReply(message: Message) {
        val blocks = message.decodeAs<Array<Block>>()
        val synchronizationSuccess = chain.addBlocks(*blocks)
        if (synchronizationSuccess) {
            blocks.forEach { block ->
                block.validatorChanges.forEach { (key, wasAdded) ->
                    validatorSet.inclusionChange(key, wasAdded)
                }
            }
        }
        Logger.chain("Synchronization has been successful $synchronizationSuccess.")
    }

    private fun requestSynchronization() {
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        val syncRequest = SyncRequest(nion.localNode, ourSlot)
        val randomNode = nion.pickRandomNodes(1).map { it.publicKey }
        nion.send(Endpoint.SyncRequest, TransmissionType.Unicast, syncRequest, *randomNode.toTypedArray())
        Logger.info("Requesting synchronization from $ourSlot.")
    }

    fun requestInclusion() {
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        val inclusionRequest = InclusionRequest(ourSlot, nion.localNode.publicKey)
        nion.send(Endpoint.InclusionRequest, TransmissionType.Unicast, inclusionRequest)
    }
}
