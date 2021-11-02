package chain

import consensus.CommitteeStrategy
import data.Configuration
import data.chain.Block
import data.chain.ChainTask
import data.chain.SlotDuty
import data.communication.InclusionRequest
import data.communication.Message
import data.communication.SyncRequest
import data.communication.TransmissionType
import data.docker.MigrationPlan
import data.network.Endpoint
import docker.DockerMigrationStrategy
import logging.Dashboard
import logging.Logger
import manager.InformationManager
import manager.VerifiableDelayFunctionManager
import network.DistributedHashTable
import network.Network
import utils.Crypto
import utils.runAfter
import java.lang.Long.max
import kotlin.math.abs

/**
 * Created by Mihael Valentin Berčič
 * on 25/10/2021 at 09:25
 * using IntelliJ IDEA
 */
class ChainBuilder(
    private val informationManager: InformationManager,
    private val dockerMigrationStrategy: DockerMigrationStrategy,
    private val network: Network,
    private val dht: DistributedHashTable,
    private val crypto: Crypto,
    private val configuration: Configuration,
    private val committeeStrategy: CommitteeStrategy,
    private val vdf: VerifiableDelayFunctionManager
) {

    private val chainHistory = ChainHistory(dht, crypto, configuration, this, network.isTrustedNode)

    fun blockReceived(message: Message<Block>) {
        val newBlock = message.body
        chainHistory.addBlock(newBlock)
        if (!chainHistory.isInValidatorSet) requestInclusion(newBlock.slot)
    }

    fun executeTask(previousBlock: Block, nextTask: ChainTask) {
        try {
            previousBlock.migrations[crypto.publicKey]?.apply {
                dockerMigrationStrategy.migrateContainer(this, previousBlock)
            }
            informationManager.prepareForStatistics(nextTask, chainHistory.getValidators(), previousBlock)
            when (nextTask.myTask) {
                SlotDuty.PRODUCER -> {
                    val vdfStart = System.currentTimeMillis()
                    val vdfProof = vdf.findProof(previousBlock.difficulty, previousBlock.hash)
                    val vdfComputationTime = System.currentTimeMillis() - vdfStart
                    val newBlock = Block(
                        previousBlock.slot + 1,
                        difficulty = configuration.initialDifficulty,
                        timestamp = System.currentTimeMillis(),
                        vdfProof = vdfProof,
                        blockProducer = crypto.publicKey,
                        validatorChanges = chainHistory.getInclusionChanges(),
                        precedentHash = previousBlock.hash
                    )
                    val delayThird = configuration.slotDuration / 3
                    val firstDelay = max(0, delayThird - vdfComputationTime)
                    val committeeNodes = nextTask.committee.toTypedArray()
                    committeeNodes.forEach(network.dht::searchFor)
                    runAfter(firstDelay + delayThird + delayThird) {
                        val latestStatistics = setOf(*informationManager.getLatestStatistics(previousBlock.slot).toTypedArray())
                            .filter { it.slot == previousBlock.slot }
                            .sortedByDescending { it.timestamp }
                            .distinctBy { it.publicKey }
                        Dashboard.reportStatistics(latestStatistics, newBlock.slot)

                        val mostUsedNode = latestStatistics.maxByOrNull { it.totalCPU }
                        val leastUsedNode = latestStatistics.filter { it != mostUsedNode }.minByOrNull { it.totalCPU }
                        Logger.debug("$mostUsedNode $leastUsedNode ${leastUsedNode == mostUsedNode}")

                        if (leastUsedNode != null && mostUsedNode != null && leastUsedNode != mostUsedNode) {
                            val lastBlocks = chainHistory.getLastBlocks(20)
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
                                    newBlock.migrations[mostUsedNode.publicKey] = migration
                                    Logger.debug(migration)
                                }
                            }
                        }
                        // committeeStrategy.requestVotes(newBlock, committeeNodes)
                        val blockToBroadcast = committeeStrategy.getVotes(newBlock)
                        val task = chainHistory.calculateNextTask(blockToBroadcast, configuration.committeeSize)
                        network.searchAndSend(Endpoint.NewBlock, TransmissionType.Broadcast, blockToBroadcast, task.blockProducer, *task.committee.toTypedArray())
                        Dashboard.newBlockProduced(blockToBroadcast, network.knownNodes.size, chainHistory.getValidatorSize())
                    }
                }
                SlotDuty.COMMITTEE -> network.searchAndSend(Endpoint.NewBlock, TransmissionType.Unicast, previousBlock, nextTask.blockProducer)
                SlotDuty.VALIDATOR -> {}
            }
        } catch (e: Exception) {
            Dashboard.reportException(e)
        }
    }

    fun produceGenesisBlock() {
        val vdfProof = vdf.findProof(configuration.initialDifficulty, "FFFF")
        val block = Block(
            slot = 1,
            difficulty = configuration.initialDifficulty,
            timestamp = System.currentTimeMillis(),
            vdfProof = vdfProof,
            blockProducer = crypto.publicKey,
            validatorChanges = chainHistory.getInclusionChanges()
        )
        Logger.info("Broadcasting genesis block...")
        network.send(Endpoint.NewBlock, TransmissionType.Broadcast, block)
        Dashboard.newBlockProduced(block, network.knownNodes.size, chainHistory.getValidatorSize())
    }

    /** Requests inclusion by sending a broadcast message to [n][Configuration.broadcastSpreadPercentage] of random known nodes. */
    fun requestInclusion(slot: Long = 0) {
        val inclusionRequest = InclusionRequest(slot, crypto.publicKey)
        Dashboard.requestedInclusion(network.ourNode.ip, slot)
        Logger.debug("Requesting inclusion with slot ${inclusionRequest.currentSlot}...")
        network.send(Endpoint.InclusionRequest, TransmissionType.Unicast, inclusionRequest)
    }

    fun inclusionRequested(message: Message<InclusionRequest>) {
        val inclusionRequest = message.body
        chainHistory.inclusionRequested(inclusionRequest)
    }

    fun requestSync() {
        val slot = chainHistory.getLastBlock()?.slot ?: 0
        val request = SyncRequest(network.ourNode, slot)
        network.send(Endpoint.SyncRequest, TransmissionType.Unicast, request, 1)
    }

    fun syncRequested(message: Message<SyncRequest>) {
        val request = message.body
        val blocksToSend = chainHistory.getBlocks(request.fromBlock).take(500)
        network.send(Endpoint.SyncReply, TransmissionType.Unicast, blocksToSend, request.node)
    }

    fun syncReplyReceived(message: Message<Array<Block>>) {
        val blocks = message.body
        chainHistory.addBlocks(blocks)
    }

}