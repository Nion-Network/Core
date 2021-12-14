package network

import data.Configuration
import data.chain.Block
import data.chain.SlotDuty
import data.communication.InclusionRequest
import data.communication.Message
import data.communication.SyncRequest
import data.communication.TransmissionType
import data.docker.MigrationPlan
import data.network.Endpoint
import logging.Dashboard
import logging.Logger
import utils.runAfter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Created by Mihael Valentin Berčič
 * on 06/11/2021 at 13:50
 * using IntelliJ IDEA
 */
abstract class ChainBuilder(configuration: Configuration) : DockerProxy(configuration) {

    private val validatorSet = ValidatorSet(localNode, isTrustedNode)
    private val verifiableDelay = VerifiableDelay()
    private val chain = Chain(verifiableDelay, configuration.initialDifficulty, configuration.committeeSize)
    private var sentGenesis = AtomicBoolean(false)

    fun blockReceived(message: Message) {
        val block = message.decodeAs<Block>()
        if (chain.addBlocks(block)) {
            validatorSet.inclusionChanges(block)
            if (!validatorSet.isInValidatorSet) requestInclusion()
            val nextTask = validatorSet.computeNextTask(block, configuration.committeeSize)
            val clusters = validatorSet.generateClusters(nextTask.blockProducer, configuration, block)

            if (isTrustedNode) {
                query(nextTask.blockProducer) {
                    Dashboard.newBlockProduced(block, totalKnownNodes, validatorSet.validatorCount, it.ip)
                }
            }


            // sendDockerStatistics(block, nextTask.blockProducer, clusters)
            block.migrations[localNode.publicKey]?.apply {
                migrateContainer(this, block)
            }

            when (nextTask.myTask) {
                SlotDuty.PRODUCER -> {
                    Dashboard.logCluster(block, nextTask, clusters)
                    Logger.chain("Producing block ${block.slot + 1}...")
                    val computationStart = System.currentTimeMillis()
                    val proof = verifiableDelay.computeProof(block.difficulty, block.hash)
                    val committeeMembers = nextTask.committee.toTypedArray()
                    val computationDuration = System.currentTimeMillis() - computationStart
                    Logger.info("$computationDuration ... ${max(0, configuration.slotDuration - computationDuration)}")
                    runAfter(max(0, configuration.slotDuration - computationDuration)) {
                        val latestStatistics = getNetworkStatistics(block.slot).apply {
                            Logger.info("Total length: $size")
                            Logger.info("Any duplicates? ${distinctBy { it.publicKey }.size}")
                            Logger.debug("Total containers: ${sumOf { it.containers.size }}")
                            Dashboard.reportStatistics(this, block.slot)
                        }

                        val futureMigrations = mutableMapOf<String, MigrationPlan>()
                        val mostUsedNode = latestStatistics.maxByOrNull { it.totalCPU }
                        val leastUsedNode = latestStatistics.filter { it != mostUsedNode }.minByOrNull { it.totalCPU }
                        Logger.debug("$mostUsedNode $leastUsedNode ${leastUsedNode == mostUsedNode}")

                        if (leastUsedNode != null && mostUsedNode != null && leastUsedNode != mostUsedNode) {
                            val lastBlocks = chain.getLastBlocks(block.slot - 20)
                            val lastMigrations = lastBlocks.map { it.migrations.values }.flatten()
                            val leastConsumingApp = mostUsedNode.containers
                                .filter { app -> lastMigrations.none { it.container == app.id } }
                                .filter { it.cpuUsage > 5 }
                                .minByOrNull { it.cpuUsage }

                            if (leastConsumingApp != null) {
                                val mostUsage = mostUsedNode.totalCPU
                                val leastUsage = leastUsedNode.totalCPU
                                val appUsage = leastConsumingApp.cpuUsage
                                val beforeMigration = abs(mostUsage - leastUsage)
                                val afterMigration = abs((mostUsage - appUsage) - (leastUsage + appUsage))
                                val difference = abs(beforeMigration - afterMigration)

                                Dashboard.reportException(Exception("Difference: $difference App: $appUsage"))
                                if (difference >= 5) {
                                    val migration = MigrationPlan(mostUsedNode.publicKey, leastUsedNode.publicKey, leastConsumingApp.id)
                                    futureMigrations[mostUsedNode.publicKey] = migration
                                    Logger.debug(migration)
                                }
                            }
                        }
                        val newBlock = Block(
                            block.slot + 1,
                            difficulty = configuration.initialDifficulty,
                            timestamp = System.currentTimeMillis(),
                            dockerStatistics = latestStatistics,
                            vdfProof = proof,
                            blockProducer = localNode.publicKey,
                            validatorChanges = validatorSet.getScheduledChanges(),
                            precedentHash = block.hash,
                            migrations = futureMigrations
                        )
                        Logger.chain("Broadcasting out block ${newBlock.slot}.")
                        send(Endpoint.NewBlock, TransmissionType.Broadcast, newBlock, *committeeMembers)
                    }
                }
                SlotDuty.COMMITTEE -> {
                    send(Endpoint.NewBlock, TransmissionType.Unicast, block, nextTask.blockProducer)
                    /*runAfter(configuration.slotDuration * 3) {
                        val skipVDF = verifiableDelay.computeProof(block.difficulty, block.hash)
                        val skipBlock = Block(
                            block.slot,
                            block.difficulty,
                            "SKIPBLOCK",
                            emptyList(),
                            skipVDF,
                            System.currentTimeMillis(),
                            block.hash,
                            mapOf(nextTask.blockProducer to false),
                            votes = 69
                        )
                        if (chain.getLastBlock()?.slot == block.slot) {
                            Dashboard.reportException(Exception("Sending out skip block [${chain.getLastBlock()?.slot}] vs [${block.slot}]!"))
                            send(Endpoint.NewBlock, TransmissionType.Broadcast, skipBlock)
                        }
                    }*/
                }
                SlotDuty.VALIDATOR -> {}
            }
        } else {
            val lastBlock = chain.getLastBlock()
            val lastSlot = lastBlock?.slot ?: 0
            val slotDifference = block.slot - lastSlot
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
        if (isTrustedNode && chain.getLastBlock() == null) {
            Logger.debug("Received inclusion request! ")
            val scheduledChanges = validatorSet.getScheduledChanges().count { it.value }
            val isEnoughToStart = scheduledChanges > configuration.committeeSize
            if (isEnoughToStart && !sentGenesis.get()) {
                val proof = verifiableDelay.computeProof(configuration.initialDifficulty, "FFFF")
                val genesisBlock = Block(1, configuration.initialDifficulty, localNode.publicKey, emptyList(), proof, System.currentTimeMillis(), "", validatorSet.getScheduledChanges())
                send(Endpoint.NewBlock, TransmissionType.Broadcast, genesisBlock)
                sentGenesis.set(true)
                Logger.chain("Broadcasting genesis block to $scheduledChanges nodes!")
            }
        }
    }

    fun attemptInclusion() {
        if (validatorSet.isInValidatorSet) return
        runAfter(configuration.slotDuration, this::requestInclusion)
    }

    /** Respond with blocks between the slot and the end of the chain. */
    fun synchronizationRequested(message: Message) {
        val syncRequest = message.decodeAs<SyncRequest>()
        val blocksToSendBack = chain.getLastBlocks(syncRequest.fromSlot)
        val requestingNode = syncRequest.node
        send(Endpoint.SyncReply, TransmissionType.Unicast, blocksToSendBack, requestingNode.publicKey)
    }

    /** Attempt to add blocks received from the random node. */
    fun synchronizationReply(message: Message) {
        val blocks = message.decodeAs<Array<Block>>()
        Logger.info("Received back ${blocks.size} ready for synchronization. ${blocks.firstOrNull()?.slot}\t→\t${blocks.lastOrNull()?.slot} ")
        val synchronizationSuccess = chain.addBlocks(*blocks)
        if (synchronizationSuccess) validatorSet.inclusionChanges(*blocks)
        Logger.chain("Synchronization has been successful $synchronizationSuccess.")
    }

    /** Requests synchronization from any random known node. */
    private fun requestSynchronization() {
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        val syncRequest = SyncRequest(localNode, ourSlot)
        send(Endpoint.SyncRequest, TransmissionType.Unicast, syncRequest, 1)
        Logger.error("Requesting synchronization from $ourSlot.")
    }

    /** Requests inclusion into the validator set. */
    internal fun requestInclusion() {
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        val inclusionRequest = InclusionRequest(ourSlot, localNode.publicKey)
        send(Endpoint.InclusionRequest, TransmissionType.Unicast, inclusionRequest)
        Logger.info("Requesting inclusion with $ourSlot.")
    }
}
