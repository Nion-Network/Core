package chain

import data.Configuration
import data.chain.*
import data.communication.InclusionRequest
import data.communication.Message
import data.communication.SyncRequest
import data.communication.TransmissionType
import data.network.Endpoint
import docker.DockerMigrationStrategy
import logging.Dashboard
import logging.Logger
import manager.DistributedHashTable
import manager.InformationManager
import manager.NetworkManager
import manager.VerifiableDelayFunctionManager
import utils.Crypto
import utils.runAfter
import java.lang.Long.max
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import kotlin.random.Random


/**
 * Created by Mihael Valentin Berčič
 * on 25/09/2020 at 16:58
 * using IntelliJ IDEA
 */
class ChainManager(
    private val networkManager: NetworkManager,
    private val crypto: Crypto,
    private val configuration: Configuration,
    private val vdf: VerifiableDelayFunctionManager,
    private val dht: DistributedHashTable,
    private val docker: DockerMigrationStrategy,
    private val informationManager: InformationManager,
    private val blockProducer: BlockProducer
) {

    val isChainEmpty: Boolean get() = chain.isEmpty()

    private val blockQueue = LinkedBlockingQueue<NewBlock>()
    private val votes = ConcurrentHashMap<String, MutableList<VoteInformation>>()
    private val chain = mutableListOf<Block>()
    private val committeeExecutor = Executors.newSingleThreadScheduledExecutor()
    private var scheduledCommitteeFuture: ScheduledFuture<*>? = null


    init {
        Thread {
            while (true) {
                val blockToAdd = blockQueue.take()
                addBlock(blockToAdd.block, blockToAdd.isFromSync)
            }
        }.start()
    }


    /** Adds the specified block to the chain. Calculates our task for the next slot and acts accordingly.*/
    private fun addBlock(block: Block, isFromSync: Boolean = false) {
        val blockSlot = block.slot
        val currentSlot = chain.lastOrNull()?.slot ?: 0

        // Logger.info("New block came [$blockSlot][$currentSlot] from ${block.blockProducer}")
        if (blockSlot <= currentSlot) {
            Logger.error("Ignoring old block...")
            return
        }

        if (blockSlot > currentSlot + 1 && !isFromSync) {
            requestSync()
            blockQueue.clear()
            Dashboard.requestedInclusion("SYNCREQUEST", 0)
            return
        }

        block.validatorChanges.forEach(blockProducer::validatorChange)
        scheduledCommitteeFuture?.cancel(true)
        chain.add(block)
        votes.remove(block.hash)
        informationManager.latestNetworkStatistics.removeIf { it.slot != block.slot }

        Logger.chain("Added block [${block.slot}][${Logger.green}${block.votes}]${Logger.reset}")
        if (isFromSync) return

        block.migrations[crypto.publicKey]?.apply { docker.migrateContainer(this, block) }

        val nextTask = calculateNextTask(block)
        if (!blockProducer.isIncluded) requestInclusion(block)

        if (networkManager.isTrustedNode) Dashboard.newBlockProduced(block, networkManager.knownNodes.size, blockProducer.currentValidators.size)
        Logger.info("Next task: ${Logger.red}${nextTask.myTask}${Logger.reset}")


        // informationManager.prepareForStatistics(nextTask, blockProducer.currentValidators, block)
        when (nextTask.myTask) {
            SlotDuty.PRODUCER -> {
                val vdfStart = System.currentTimeMillis()
                val vdfProof = vdf.findProof(block.difficulty, block.hash)
                val vdfComputationTime = System.currentTimeMillis() - vdfStart
                val newBlock = blockProducer.createBlock(block, vdfProof, blockSlot + 1)
                val voteRequest = VoteRequest(newBlock, networkManager.ourNode)

                val delayThird = configuration.slotDuration / 3
                val firstDelay = max(0, delayThird - vdfComputationTime)
                val secondDelay = max(delayThird, delayThird * 2 - vdfComputationTime)

                val committeeNodes = nextTask.committee.toTypedArray()
                runAfter(firstDelay) {
                    networkManager.searchAndSend(Endpoint.VoteRequest, TransmissionType.Unicast, voteRequest, *committeeNodes)
                }

                runAfter(secondDelay) {
                    val votesAmount = votes[newBlock.hash]?.size ?: 0
                    newBlock.votes = votesAmount
                    networkManager.searchAndSend(Endpoint.NewBlock, TransmissionType.Broadcast, newBlock, *committeeNodes)
                    // sendUDP(Endpoint.NewBlock, newBlock, TransmissionType.Broadcast)
                    // dashboard.reportStatistics(latestStatistics.toList(), blockSlot)
                }
            }
            SlotDuty.COMMITTEE -> networkManager.searchAndSend(Endpoint.NewBlock, TransmissionType.Broadcast, block, nextTask.blockProducer)
            SlotDuty.VALIDATOR -> TODO()
        }
    }

    /** Request blocks from a random known node needed for synchronization. */
    fun requestSync() {
        networkManager.clearMessageQueue()
        val from = chain.lastOrNull()?.slot ?: 0
        val syncRequest = SyncRequest(networkManager.ourNode, from)
        Logger.info("Requesting new blocks from $from")
        networkManager.send(Endpoint.SyncRequest, syncRequest, TransmissionType.Unicast, 1)
    }

    /** After synchronization request has been received, we send back blocks node has asked us for. */
    fun syncRequestReceived(message: Message<SyncRequest>) {
        val syncRequest = message.body
        val requestingNode = syncRequest.node
        networkManager.knownNodes.computeIfAbsent(requestingNode.publicKey) { requestingNode }

        val blocks = chain.drop(syncRequest.fromBlock.toInt()).take(1000) // TODO change after retrieving blocks from database.
        if (blocks.isEmpty()) return

        networkManager.send(Endpoint.SyncReply, TransmissionType.Unicast, blocks, requestingNode)
        Logger.debug("Sent back ${blocks.size} blocks!")
    }

    /** After receiving of blocks, we put them to queue for them to be added to the chain. */
    fun syncReplyReceived(message: Message<Array<Block>>) {
        val blocks = message.body
        Logger.info("We have ${blocks.size} blocks to sync...")
        blockQueue.clear()
        blockQueue.addAll(blocks.map { NewBlock(true, it) })
        Logger.info("Syncing finished...")
    }

    /** On single block received, we add it to the chain queue. */
    fun blockReceived(message: Message<Block>) {
        val newBlock = message.body
        blockQueue.offer(NewBlock(false, newBlock))
    }

    /** When a vote for the current block is received, we add it to the votes map. */
    fun voteReceived(message: Message<BlockVote>) {
        val blockVote = message.body
        val voteInformation = VoteInformation(message.publicKey)
        Logger.trace("Vote received!")
        votes.computeIfAbsent(blockVote.blockHash) { mutableListOf() }.add(voteInformation)
    }

    /** Computes the task for the next block creation using current block information. */
    private fun calculateNextTask(block: Block): ChainTask {
        val seed = block.seed
        val random = Random(seed)
        val ourKey = crypto.publicKey

        val validatorSetCopy = blockProducer.currentValidators.shuffled(random).toMutableList()
        val blockProducerNode = validatorSetCopy[0].apply { validatorSetCopy.remove(this) }
        val committee = validatorSetCopy.take(configuration.committeeSize)

        val ourRole = when {
            blockProducerNode == ourKey -> SlotDuty.PRODUCER
            committee.contains(ourKey) -> SlotDuty.COMMITTEE
            else -> SlotDuty.VALIDATOR
        }

        if (ourRole == SlotDuty.PRODUCER) committee.forEach(dht::searchFor)
        return ChainTask(ourRole, blockProducerNode, committee)
    }

    /** Returns boolean value whether a node can be included in the validator set or not. */
    private fun canBeIncluded(inclusionRequest: InclusionRequest): Boolean {
        if (!blockProducer.isIncluded) return false
        val lastBlock = chain.lastOrNull() ?: return blockProducer.isIncluded
        return lastBlock.slot == inclusionRequest.currentSlot
    }

    /** Reverts changes of the specified block made to our state. */
    private fun revertChanges(block: Block) {
        blockProducer.currentValidators.apply {
            block.validatorChanges.forEach { (publicKey, change) ->
                if (change) remove(publicKey)
                else add(publicKey)
            }
        }
    }

    /** When an inclusion request is received and the node is synced, the public key is added to the future inclusion changes.*/
    fun inclusionRequest(message: Message<InclusionRequest>) {
        val publicKey = message.publicKey
        val inclusionRequest = message.body
        val canBeIncluded = canBeIncluded(inclusionRequest)
        Logger.trace("Inclusion request received[${inclusionRequest.currentSlot}] and can be included: ${Logger.green} $canBeIncluded${Logger.reset}")
        if (!canBeIncluded) return

        blockProducer.inclusionChanges[publicKey] = true

        val currentValidatorsSize = blockProducer.currentValidators.size
        val newValidators = blockProducer.inclusionChanges.filter { it.value }.count()

        val isEnoughIncluded = currentValidatorsSize + newValidators >= configuration.committeeSize + 1
        val isChainEmpty = isChainEmpty
        if (networkManager.isTrustedNode && isChainEmpty && isEnoughIncluded) {
            val vdfProof = vdf.findProof(configuration.initialDifficulty, "FFFF")
            val block = blockProducer.genesisBlock(vdfProof)
            Logger.debug("Broadcasting genesis block...")
            with(networkManager) {
                knownNodes.forEach { Logger.info("Sending genesis block to: ${it.value.ip}") }
                send(Endpoint.NewBlock, TransmissionType.Broadcast, block)
            }
        }
    }

    /** Requests inclusion by sending a broadcast message to [n][Configuration.broadcastSpreadPercentage] of random known nodes. */
    fun requestInclusion(block: Block? = null) {
        val slot = block?.slot ?: 0
        val inclusionRequest = InclusionRequest(slot, crypto.publicKey)
        Dashboard.requestedInclusion(networkManager.ourNode.ip, slot)
        Logger.debug("Requesting inclusion with slot ${inclusionRequest.currentSlot}...")
        networkManager.send(Endpoint.InclusionRequest, TransmissionType.Broadcast, inclusionRequest)
    }

}

/*
                val latestStatistics = informationManager.latestNetworkStatistics
                Logger.info(latestStatistics)

                val mostUsedNode = latestStatistics.maxByOrNull { it.totalCPU }
                val leastUsedNode = latestStatistics.minByOrNull { it.totalCPU }

                Logger.debug("$mostUsedNode $leastUsedNode ${leastUsedNode == mostUsedNode}")

                if (leastUsedNode != null && mostUsedNode != null && leastUsedNode != mostUsedNode) {
                    val leastConsumingApp = mostUsedNode.containers.minByOrNull { it.cpuUsage }
                    val lastBlocks = chain.takeLast(20)
                    val lastMigrations = lastBlocks.map { it.migrations.values }.flatten()
                    Logger.trace("\t\tLeast consuming app: $leastConsumingApp")

                    // Note: Extremely naive and useless efficiency algorithm. Proper configurable migration planning coming later.
                    Logger.trace("App: ${leastConsumingApp == null}. Least: ${lastMigrations.none { it.container == leastConsumingApp?.id }}")

                    if (leastConsumingApp != null && lastMigrations.none { it.container == leastConsumingApp.id }) {
                        val migration = MigrationPlan(mostUsedNode.publicKey, leastUsedNode.publicKey, leastConsumingApp.id)
                        newBlock.migrations[mostUsedNode.publicKey] = migration
                        Logger.debug(migration)
                    }
                }
                */