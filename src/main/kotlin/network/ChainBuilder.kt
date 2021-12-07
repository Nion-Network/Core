package network

import Nion
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
import kotlin.math.abs
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
    private val chain = Chain(verifiableDelay, configuration.initialDifficulty, configuration.committeeSize)

    @MessageEndpoint(Endpoint.NewBlock)
    fun blockReceived(message: Message) {
        val block = message.decodeAs<Block>()
        if (chain.addBlocks(block)) {
            validatorSet.inclusionChanges(block)
            if (!validatorSet.isInValidatorSet) requestInclusion()
            val nextTask = validatorSet.computeNextTask(block, configuration.committeeSize)
            val clusters = validatorSet.generateClusters(nextTask.blockProducer, configuration, block)

            if (nion.isTrustedNode) { // Debugging only.
                nion.queryFor(nextTask.blockProducer) {
                    Dashboard.newBlockProduced(block, nion.knownNodeCount, validatorSet.validatorCount, it.ip)
                }
            }

            nion.apply {
                sendDockerStatistics(block, nextTask.blockProducer, clusters)
                val ourMigration = block.migrations[localNode.publicKey] ?: return@apply
                migrateContainer(ourMigration, block)
            }

            when (nextTask.myTask) {
                SlotDuty.PRODUCER -> {
                    Dashboard.logCluster(block, nextTask, clusters)
                    Logger.chain("Producing action ${block.slot + 1}...")
                    val computationStart = System.currentTimeMillis()
                    val proof = verifiableDelay.computeProof(block.difficulty, block.hash)
                    val committeeMembers = nextTask.committee.toTypedArray().apply {
                        nion.queryFor(*this)
                    }
                    val computationDuration = System.currentTimeMillis() - computationStart
                    Logger.info("$computationDuration ... ${max(0, configuration.slotDuration - computationDuration)}")
                    runAfter(max(0, configuration.slotDuration - computationDuration)) {
                        val latestStatistics = nion.getNetworkStatistics(block.slot).apply {
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
                            blockProducer = nion.localNode.publicKey,
                            validatorChanges = validatorSet.getScheduledChanges(),
                            precedentHash = block.hash,
                            migrations = futureMigrations
                        )
                        Logger.chain("Broadcasting out action ${newBlock.slot}.")
                        nion.send(Endpoint.NewBlock, TransmissionType.Broadcast, newBlock, *committeeMembers)
                    }
                }
                SlotDuty.COMMITTEE -> {
                    nion.send(Endpoint.NewBlock, TransmissionType.Unicast, block, nextTask.blockProducer)
                    runAfter(configuration.slotDuration * 3) {
                        val skipVDF = verifiableDelay.computeProof(block.difficulty, block.hash)
                        val skipBlock = Block(
                            block.slot + 1,
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
                            Dashboard.reportException(Exception("Sending out skip action [${chain.getLastBlock()?.slot}] vs [${block.slot}]!"))
                            nion.send(Endpoint.NewBlock, TransmissionType.Broadcast, skipBlock)
                        }
                    }
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
                val genesisBlock = Block(1, configuration.initialDifficulty, nion.localNode.publicKey, emptyList(), proof, System.currentTimeMillis(), "", validatorSet.getScheduledChanges())
                nion.send(Endpoint.NewBlock, TransmissionType.Broadcast, genesisBlock)
                Logger.chain("Broadcasting genesis action to $scheduledChanges nodes!")
            }
        }
    }

    /** Respond with blocks between the slot and the end of the chain. */
    @MessageEndpoint(Endpoint.SyncRequest)
    fun synchronizationRequested(message: Message) {
        val syncRequest = message.decodeAs<SyncRequest>()
        val blocksToSendBack = chain.getLastBlocks(syncRequest.fromSlot)
        val requestingNode = syncRequest.node
        nion.addNewNodes(requestingNode)
        nion.send(Endpoint.SyncReply, TransmissionType.Unicast, blocksToSendBack, requestingNode.publicKey)
    }

    /** Attempt to add blocks received from the random node. */
    @MessageEndpoint(Endpoint.SyncReply)
    fun synchronizationReply(message: Message) {
        val blocks = message.decodeAs<Array<Block>>()
        Logger.info("Received back ${blocks.size} ready for synchronization. ${blocks.firstOrNull()?.slot}\t\uD83D\uDE02\t${blocks.lastOrNull()?.slot} ")
        val synchronizationSuccess = chain.addBlocks(*blocks)
        if (synchronizationSuccess) {
            validatorSet.inclusionChanges(*blocks)
        }
        Logger.chain("Synchronization has been successful $synchronizationSuccess.")
    }

    /** Requests synchronization from any random known node. */
    private fun requestSynchronization() {
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        val syncRequest = SyncRequest(nion.localNode, ourSlot)
        val randomNode = nion.pickRandomNodes(1).map { it.publicKey }
        nion.send(Endpoint.SyncRequest, TransmissionType.Unicast, syncRequest, *randomNode.toTypedArray())
        Logger.error("Requesting synchronization from $ourSlot.")
    }

    /** Requests inclusion into the validator set. */
    fun requestInclusion() {
        val lastBlock = chain.getLastBlock()
        val ourSlot = lastBlock?.slot ?: 0
        val inclusionRequest = InclusionRequest(ourSlot, nion.localNode.publicKey)
        nion.send(Endpoint.InclusionRequest, TransmissionType.Unicast, inclusionRequest)
    }
}
