package chain

import Configuration
import chain.data.*
import docker.DockerProxy
import logging.Dashboard
import logging.Logger
import network.data.Endpoint
import network.data.communication.InclusionRequest
import network.data.communication.Message
import network.data.communication.SyncRequest
import utils.asHex
import utils.launchCoroutine
import utils.runAfter
import utils.tryWithLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max

/**
 * Created by Mihael Valentin Berčič
 * on 06/11/2021 at 13:50
 * using IntelliJ IDEA
 */
abstract class ChainBuilder(configuration: Configuration) : DockerProxy(configuration) {

    private val verifiableDelay = VerifiableDelay()
    private val chain = Chain(verifiableDelay, configuration.initialDifficulty, configuration.committeeSize)
    private var sentGenesis = AtomicBoolean(false)
    private val isSyncing = AtomicBoolean(false)

    fun blockReceived(message: Message) {
        if (isSyncing.get()) {
            Logger.debug("Ignoring new block because we're in the process of syncing.")
            return
        }
        val block = message.decodeAs<Block>()
        val blockAdded = chain.addBlocks(block)
        if (blockAdded) {
            if (block.slot <= 2) validatorSet.inclusionChanges(block)
            val nextTask = validatorSet.computeNextTask(block, configuration.committeeSize)
            val clusters = validatorSet.generateClusters(nextTask.blockProducer, configuration, block)
            val ourMigrationPlan = block.migrations[localNode.publicKey]

            if (block.slot > 2) validatorSet.inclusionChanges(block)
            if (!validatorSet.isInValidatorSet) requestInclusion(nextTask.blockProducer)

            if (ourMigrationPlan != null) migrateContainer(ourMigrationPlan, block)

            sendDockerStatistics(block, nextTask.blockProducer, clusters)
            validatorSet.clearScheduledChanges()
            if (isTrustedNode) query(nextTask.blockProducer) {
                Dashboard.newBlockProduced(block, totalKnownNodes, validatorSet.validatorCount, "${it.ip}:${it.kademliaPort}")
            }
            when (nextTask.myTask) {
                SlotDuty.PRODUCER -> {
                    Dashboard.logCluster(block, nextTask, clusters)
                    Logger.chain("Producing block ${block.slot + 1}...")
                    val computationStart = System.currentTimeMillis()
                    val proof = verifiableDelay.computeProof(block.difficulty, block.hash)
                    val computationDuration = System.currentTimeMillis() - computationStart
                    Logger.chain("$computationDuration ... ${max(0, configuration.slotDuration - computationDuration)}")
                    val delayThird = configuration.slotDuration * 1 / 3
                    val startDelay = delayThird - computationDuration

                    runAfter(max(0, startDelay)) {
                        Logger.chain("Running producing of the block after time...")
                        val latestStatistics = getNetworkStatistics(block.slot).apply {
                            Logger.info("Total length: $size")
                            Logger.info("Distinct: ${distinctBy { it.publicKey }.size}")
                            Logger.debug("Total containers: ${sumOf { it.containers.size }}")
                            Dashboard.reportStatistics(this, block.slot)
                        }

                        val migrations = computeMigrations(latestStatistics)

                        val newBlock = Block(
                            block.slot + 1,
                            difficulty = configuration.initialDifficulty,
                            timestamp = System.currentTimeMillis(),
                            dockerStatistics = latestStatistics,
                            vdfProof = proof,
                            blockProducer = localNode.publicKey,
                            validatorChanges = validatorSet.getScheduledChanges(),
                            precedentHash = block.hash,
                            migrations = migrations
                        )
                        val voteRequest = VoteRequest(newBlock, localNode.publicKey)
                        val committeeMembers = nextTask.committee
                        send(Endpoint.VoteRequest, voteRequest, *committeeMembers.toTypedArray())
                        runAfter(delayThird * 2) {
                            val allVotes = votes[newBlock.hash.asHex]?.count() ?: -1
                            Logger.chain("Broadcasting out block ${newBlock.slot}.")
                            newBlock.votes = allVotes
                            send(Endpoint.NewBlock, newBlock)
                        }
                    }
                }
                SlotDuty.COMMITTEE -> {}
                SlotDuty.VALIDATOR -> {}
            }
        } else {
            val lastBlock = chain.getLastBlock()
            val lastSlot = lastBlock?.slot ?: 0
            val slotDifference = block.slot - lastSlot
            Logger.info("Slot difference: $slotDifference")
            if (slotDifference > 1) {
                Logger.error("Block ${block.slot} has been rejected. Our last slot is $lastSlot.")
                requestSynchronization()
            }
        }
    }

    /** If the node can be included in the validator set (synchronization status check) add it to future inclusion changes.*/
    fun inclusionRequested(message: Message) {
        if (!validatorSet.isInValidatorSet) return
        val inclusionRequest = message.decodeAs<InclusionRequest>()
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        if (ourSlot == inclusionRequest.currentSlot) validatorSet.scheduleChange(inclusionRequest.publicKey, true)
        Logger.debug("Received inclusion request! ")

        if (isTrustedNode && lastBlock == null) {
            val scheduledChanges = validatorSet.getScheduledChanges().count { it.value }
            val isEnoughToStart = scheduledChanges > configuration.committeeSize
            if (isEnoughToStart && !sentGenesis.getAndSet(true)) {
                val proof = verifiableDelay.computeProof(configuration.initialDifficulty, "FFFF".encodeToByteArray())
                val genesisBlock = Block(1, configuration.initialDifficulty, localNode.publicKey, emptyList(), proof, System.currentTimeMillis(), byteArrayOf(), validatorSet.getScheduledChanges())
                send(Endpoint.NewBlock, genesisBlock)
                Logger.chain("Broadcasting genesis block to $scheduledChanges nodes!")
            }
        }
    }

    fun attemptInclusion() {
        if (validatorSet.isInValidatorSet) return
        requestInclusion()
        runAfter(configuration.slotDuration, ::attemptInclusion)
    }

    /** Respond with blocks between the slot and the end of the chain. */
    fun synchronizationRequested(message: Message) {
        val syncRequest = message.decodeAs<SyncRequest>()
        Logger.info("Sync request received from ${syncRequest.fromSlot} slot.")
        val blocksToSendBack = chain.getLastBlocks(syncRequest.fromSlot)
        val requestingNode = syncRequest.node
        Logger.info("Sending back sync reply with blocks: ${blocksToSendBack.firstOrNull()?.slot} -> ${blocksToSendBack.lastOrNull()?.slot}")
        if (blocksToSendBack.isNotEmpty()) send(Endpoint.SyncReply, blocksToSendBack, requestingNode.publicKey)
    }

    /** Attempt to add blocks received from the random node. */
    fun synchronizationReply(message: Message) {
        if (isSyncing.getAndSet(true)) {
            Logger.debug("Ignoring sync reply because we're in the process of syncing.")
            return
        }
        launchCoroutine {
            val blocks = message.decodeAs<Array<Block>>()
            Logger.chain("Received back ${blocks.size} ready for synchronization. ${blocks.firstOrNull()?.slot}\t→\t${blocks.lastOrNull()?.slot} ")
            val synchronizationSuccess = chain.addBlocks(*blocks)
            if (synchronizationSuccess) validatorSet.inclusionChanges(*blocks)
            Logger.chain("Synchronization has been successful $synchronizationSuccess.")
            isSyncing.set(false)
        }
    }

    /** Requests synchronization from any random known node. */
    private fun requestSynchronization() {
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        val syncRequest = SyncRequest(localNode, ourSlot)
        val randomValidator = validatorSet.activeValidators.randomOrNull()
        if (randomValidator != null) send(Endpoint.SyncRequest, syncRequest, randomValidator)
        else send(Endpoint.SyncRequest, syncRequest, 1)
        Logger.chain("Requesting synchronization from $ourSlot.")
        // TODO add to Dashboard.
    }

    /** Requests inclusion into the validator set. */
    private fun requestInclusion(nextProducer: String? = null) {
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        val inclusionRequest = InclusionRequest(ourSlot, localNode.publicKey)
        val isValidatorSetEmpty = validatorSet.activeValidators.isEmpty()
        if (nextProducer == null) send(Endpoint.InclusionRequest, inclusionRequest)
        else send(Endpoint.InclusionRequest, inclusionRequest, nextProducer)
        Logger.chain("Requesting inclusion with $ourSlot.")
    }

    private val votes = ConcurrentHashMap<String, MutableList<Vote>>()
    private val voteLock = ReentrantLock(true)

    fun voteRequested(message: Message) {
        val request = message.decodeAs<VoteRequest>()
        val block = request.block
        val vote = Vote(block.hash, VoteType.FOR)
        send(Endpoint.Vote, vote, request.publicKey)
    }


    fun voteReceived(message: Message) {
        val vote = message.decodeAs<Vote>()
        voteLock.tryWithLock {
            val votes = votes.computeIfAbsent(vote.blockHash.asHex) { mutableListOf() }
            votes.add(vote)
        }
    }
}
