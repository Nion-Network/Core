package chain

import Configuration
import chain.data.*
import docker.DockerProxy
import kotlinx.serialization.ExperimentalSerializationApi
import logging.Dashboard
import logging.Logger
import network.data.Endpoint
import network.data.clusters.ClusterUtils
import network.data.messages.InclusionRequest
import network.data.messages.Message
import network.data.messages.SyncRequest
import network.rpc.Topic
import utils.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 06/11/2021 at 13:50
 * using IntelliJ IDEA
 */
@ExperimentalSerializationApi
abstract class ChainBuilder(configuration: Configuration) : DockerProxy(configuration) {

    private val verifiableDelay = VerifiableDelay()
    private val networkContainerState = NetworkContainerState()
    private val chain = Chain(verifiableDelay, configuration.initialDifficulty, configuration.committeeSize)
    private var sentGenesis = AtomicBoolean(false)
    private val isSyncing = AtomicBoolean(false)
    private val votes = ConcurrentHashMap<String, MutableList<Vote>>()
    private val voteLock = ReentrantLock(true)

    /** Compute next task, report statistics and execute your role on arrival of the new block. */
    fun blockReceived(message: Message) {
        if (isSyncing.get()) {
            Logger.debug("Ignoring new block because we're in the process of syncing.")
            return
        }
        val block = message.decodeAs<Block>()
        val blockAdded = chain.addBlocks(block)
        val blockRandom = Random(block.seed)

        votes.entries.removeIf { (key, _) -> key == block.hash.asHex }
        if (blockAdded) {

            if(isTrustedNode) Logger.chain("Added block\t[${block.slot}].")
            sendToSubscribed(Topic.Block, block)

            if (block.slot <= 2) validatorSet.inclusionChanges(block)
            val nextTask = validatorSet.computeNextTask(block, configuration.committeeSize)

            val activeValidators = validatorSet.activeValidators.shuffled(blockRandom)
            val clusters = ClusterUtils.computeClusters(configuration.nodesPerCluster, configuration.maxIterations, activeValidators) { centroid, element ->
                val elementBitSet = sha256(element).asHex.asBitSet
                val centroidBitset = sha256(centroid).asHex.asBitSet.apply { xor(elementBitSet) }
                centroidBitset.nextSetBit(0)
                // ToDo: Performance improvement.
            }
            if (validatorSet.isInValidatorSet) sendDockerStatistics(block, nextTask.blockProducer, clusters)
            val ourMigrationPlan = block.migrations[localNode.publicKey]


            if (block.slot > 2) validatorSet.inclusionChanges(block)
            if (!validatorSet.isInValidatorSet) requestInclusion(nextTask.blockProducer)
            if (ourMigrationPlan != null) migrateContainer(ourMigrationPlan, block)

            validatorSet.clearScheduledChanges()
            if (isTrustedNode) query(nextTask.blockProducer) {
                Logger.info("Migration plan for ${block.slot} = ${ourMigrationPlan?.copy(from = "", to = "")}")
                Dashboard.newBlockProduced(block, totalKnownNodes, validatorSet.validatorCount, "${it.ip}:${it.kademliaPort}")
            }


            block.migrations.values.forEach(networkContainerState::migrationChange)
            /* ToDo: Every % k == 0 do
                1. Create snapshots of running containers using CRIU
                2. Store snapshots as files / folders
                3. Store snapshot name / identifier
                4. Submit snapshot identifier to cluster representative
                5. Cluster representative aggregates proofs and sends back to the block producer
                6. Block producer includes snapshot information in block
             */

            when (nextTask.myTask) {
                SlotDuty.Producer -> {
                    Dashboard.logCluster(block, nextTask, clusters)
                    Logger.chain("Producing block ${block.slot + 1}...")
                    val computationStart = System.currentTimeMillis()
                    val proof = verifiableDelay.computeProof(block.difficulty, block.hash)
                    val computationDuration = System.currentTimeMillis() - computationStart
                    Logger.chain("$computationDuration ... ${max(0, configuration.slotDuration - computationDuration)}")
                    val delayThird = configuration.slotDuration * 1 / 3
                    val startDelay = delayThird - computationDuration
                    val committeeMembers = nextTask.committee
                    committeeMembers.forEach { query(it) }
                    runAfter(max(0, startDelay)) {
                        Logger.chain("Running producing of the block after time...")

                        val latestStatistics = getNetworkStatistics(block.slot).apply {
                            Logger.info("Total length: $size")
                            Logger.info("Distinct: ${distinctBy { it.publicKey }.size}")
                            Logger.debug("Total containers: ${sumOf { it.containers.size }}")
                            Dashboard.reportStatistics(this, block.slot)
                        }

                        val previouslyMigratedContainers = chain.getLastBlocks(block.slot - 10)
                            .flatMap { it.migrations.values }
                            .map { it.container }
                        val migrations = computeMigrations(previouslyMigratedContainers, latestStatistics)

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
                        send(Endpoint.VoteRequest, voteRequest, *committeeMembers.toTypedArray())
                        runAfter(delayThird * 2) {
                            val allVotes = votes[newBlock.hash.asHex]?.count() ?: -1
                            Logger.chain("Broadcasting out block ${newBlock.slot}.")
                            newBlock.votes = allVotes
                            send(Endpoint.NewBlock, newBlock)
                        }
                    }
                }

                SlotDuty.Committee -> {}
                SlotDuty.Quorum -> {

                }

                SlotDuty.Validator -> {}
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

    /** Attempts inclusion every [Configuration.slotDuration] milliseconds. */
    fun attemptInclusion() {
        if (validatorSet.isInValidatorSet) return
        requestInclusion()
        runAfter(Random.nextLong(configuration.slotDuration, 3 * configuration.slotDuration), ::attemptInclusion)
    }

    /** Requests inclusion into the validator set. */
    private fun requestInclusion(nextProducer: String? = null) {
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        val inclusionRequest = InclusionRequest(ourSlot, localNode.publicKey)
        val isValidatorSetEmpty = validatorSet.activeValidators.isEmpty()
        when {
            isValidatorSetEmpty -> {
                val trusted = pickRandomNodes(totalKnownNodes).firstOrNull { it.ip == configuration.trustedNodeIP && it.kademliaPort == configuration.trustedNodePort }
                if (trusted != null) send(Endpoint.InclusionRequest, inclusionRequest, trusted.publicKey)
            }

            nextProducer == null -> send(Endpoint.InclusionRequest, inclusionRequest)
            else -> send(Endpoint.InclusionRequest, inclusionRequest, nextProducer)
        }
        Logger.chain("Requesting inclusion with $ourSlot.")
    }

    /** If the node can be included in the validator set (synchronization status check) add it to future inclusion changes.*/
    fun inclusionRequested(message: Message) {
        if (!validatorSet.isInValidatorSet) return
        val inclusionRequest = message.decodeAs<InclusionRequest>()
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        if (ourSlot == inclusionRequest.currentSlot) {
            validatorSet.scheduleChange(inclusionRequest.publicKey, true)
            // send(message)
        }
        val canStartTheChain = isTrustedNode && lastBlock == null
        Logger.debug("Received inclusion request! Can start the chain: $canStartTheChain")

        if (canStartTheChain) {
            val scheduledChanges = validatorSet.getScheduledChanges().count { it.value }
            val isEnoughToStart = scheduledChanges > configuration.committeeSize
            Logger.debug("There is enough to start: $isEnoughToStart")
            if (isEnoughToStart && !sentGenesis.getAndSet(true)) {
                Logger.debug("Computing proof for genesis block.")
                val proof = verifiableDelay.computeProof(configuration.initialDifficulty, "FFFF".encodeToByteArray())
                val genesisBlock = Block(1, configuration.initialDifficulty, localNode.publicKey, emptySet(), proof, System.currentTimeMillis(), byteArrayOf(), validatorSet.getScheduledChanges())
                send(Endpoint.NewBlock, genesisBlock)
                Logger.chain("Broadcasting genesis block to $scheduledChanges nodes!")
            }
        }
    }

    /** Verify the block produced and send back your verdict. */
    fun voteRequested(message: Message) {
        val request = message.decodeAs<VoteRequest>()
        val block = request.block
        val vote = Vote(block.hash, VoteType.FOR)
        send(Endpoint.Vote, vote, request.publicKey)
    }

    /** Stores received vote in [votes]. */
    fun voteReceived(message: Message) {
        val vote = message.decodeAs<Vote>()
        voteLock.tryWithLock {
            val votes = votes.computeIfAbsent(vote.blockHash.asHex) { mutableListOf() }
            votes.add(vote)
        }
    }
}