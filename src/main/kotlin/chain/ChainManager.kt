package chain

import communication.InclusionRequest
import communication.Message
import communication.SyncRequest
import communication.TransmissionType
import data.*
import logging.Dashboard
import logging.Logger
import manager.*
import utils.Crypto
import utils.runAfter
import java.lang.Long.max
import java.util.concurrent.*
import kotlin.math.abs
import kotlin.math.roundToInt
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
    private val docker: DockerManager,
    private val dashboard: Dashboard,
    private val informationManager: InformationManager,
    private val blockProducer: BlockProducer
) {

    val isChainEmpty: Boolean get() = chain.isEmpty()

    private val blockQueue = LinkedBlockingQueue<BlockToAdd>()
    private val votes = ConcurrentHashMap<String, MutableList<VoteInformation>>()
    private val chain = mutableListOf<Block>()
    private val committeeExecutor = Executors.newSingleThreadScheduledExecutor()
    private var scheduledCommitteeFuture: ScheduledFuture<*>? = null

    data class BlockToAdd(val isFromSync: Boolean, val block: Block)

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
            dashboard.requestedInclusion("SYNCREQUEST", 0)
            return
        }

        block.validatorChanges.forEach(blockProducer::validatorChange)
        scheduledCommitteeFuture?.cancel(true)
        chain.add(block)
        votes.remove(block.hash)
        informationManager.latestNetworkStatistics.clear()
        docker.apply {
            val outdated = latestStatistics.filter { (_, stats) -> System.currentTimeMillis() - stats.updated >= 1000 }
            outdated.keys.forEach { latestStatistics.remove(it) }
        }
        Logger.chain("Added block [${block.slot}][${Logger.green}${block.votes}]${Logger.reset}")
        if (isFromSync) return

        block.migrations[crypto.publicKey]?.apply { docker.migrateContainer(this, block) }

        val nextTask = calculateNextTask(block)
        if (!blockProducer.isIncluded) requestInclusion(block)

        if (networkManager.isTrustedNode) dashboard.newBlockProduced(block, networkManager.knownNodes.size, blockProducer.currentValidators.size)
        Logger.info("Next task: ${Logger.red}${nextTask.myTask}${Logger.reset}")

        try {
            if (nextTask.myTask == SlotDuty.PRODUCER) {
                val vdfStart = System.currentTimeMillis()
                val vdfProof = vdf.findProof(block.difficulty, block.hash, dashboard)
                val vdfComputationTime = System.currentTimeMillis() - vdfStart
                val newBlock = blockProducer.createBlock(block, vdfProof, blockSlot + 1)
                val voteRequest = VoteRequest(newBlock, networkManager.ourNode)

                val delayThird = configuration.slotDuration / 3
                val firstDelay = max(0, delayThird - vdfComputationTime)
                val secondDelay = max(delayThird, delayThird * 2 - vdfComputationTime)
                runAfter(firstDelay) {
                    networkManager.apply {
                        Logger.trace("Requesting votes!")
                        val committeeNodes = nextTask.committee.mapNotNull { knownNodes[it] }.toTypedArray()
                        sendUDP(Endpoint.VoteRequest, voteRequest, TransmissionType.Unicast, *committeeNodes)
                    }
                }

                runAfter(secondDelay) {
                    val votesAmount = votes[newBlock.hash]?.size ?: 0
                    newBlock.votes = votesAmount
                    networkManager.apply {
                        val latestStatistics = informationManager.latestNetworkStatistics
                        Logger.info("\t\tWe have ${latestStatistics.size} latest statistics!")
                        val mostUsedNode = latestStatistics.maxByOrNull { it.totalCPU }
                        val leastUsedNode = latestStatistics.filter { it.publicKey != mostUsedNode?.publicKey }.minByOrNull { it.totalCPU }

                        Logger.info("\t\tMost used node: $mostUsedNode")
                        Logger.info("\t\tLeast used node: $leastUsedNode")

                        if (leastUsedNode != null && mostUsedNode != null) {
                            val leastConsumingApp = mostUsedNode.containers.values.minByOrNull { it.cpuUsage }
                            val lastBlocks = chain.takeLast(10)
                            val lastMigrations = lastBlocks.map { it.migrations.values }.flatten()
                            Logger.debug("\t\tLeast consuming app: $leastConsumingApp")

                            // Note: Extremely naive and useless efficiency algorithm. Proper configurable migration planning coming later.
                            if (leastConsumingApp != null && lastMigrations.none { it.container == leastConsumingApp.id }) {
                                val senderBefore = mostUsedNode.totalCPU
                                val receiverBefore = leastUsedNode.totalCPU
                                val cpuChange = leastConsumingApp.cpuUsage.roundToInt()

                                val senderAfter = senderBefore - cpuChange
                                val receiverAfter = receiverBefore + cpuChange

                                val differenceBefore = abs(senderBefore - receiverBefore)
                                val differenceAfter = abs(senderAfter - receiverAfter)
                                val migrationDifference = abs(differenceBefore - differenceAfter)

                                // TODO add to configuration
                                val minimumDifference = 5
                                Logger.debug("Percentage difference of before and after: $migrationDifference %")
                                if (migrationDifference >= minimumDifference) {
                                    val newMigrationPlan = MigrationPlan(mostUsedNode.publicKey, leastUsedNode.publicKey, leastConsumingApp.id)
                                    newBlock.migrations[mostUsedNode.publicKey] = newMigrationPlan
                                }
                            }
                        }

                        val committeeNodes = nextTask.committee.mapNotNull { knownNodes[it] }.toTypedArray()
                        sendUDP(Endpoint.NewBlock, newBlock, TransmissionType.Broadcast, *committeeNodes)
                        // sendUDP(Endpoint.NewBlock, newBlock, TransmissionType.Broadcast)
                        dashboard.reportStatistics(latestStatistics.toList(), blockSlot)
                    }
                }
            } else if (nextTask.myTask == SlotDuty.COMMITTEE) {
                val nextProducer = nextTask.blockProducer
                dht.searchFor(nextProducer) {
                    networkManager.sendUDP(Endpoint.NewBlock, block, TransmissionType.Unicast, it)
                }

                scheduledCommitteeFuture = committeeExecutor.schedule({
                    networkManager.apply {
                        val skipBlock = blockProducer.createSkipBlock(block)
                        val committeeNodes = nextTask.committee.mapNotNull { knownNodes[it] }.toTypedArray()
                        // sendUDP(Endpoint.NewBlock, skipBlock, TransmissionType.Broadcast, *committeeNodes)
                        // sendUDP(Endpoint.NewBlock, broadcastMessage, TransmissionType.Broadcast)
                    }
                }, configuration.slotDuration * 2, TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            dashboard.reportException(e)
        }
        informationManager.prepareForStatistics(nextTask, blockProducer.currentValidators, block)
    }

    /** Request blocks from a random known node needed for synchronization. */
    fun requestSync() {
        networkManager.clearMessageQueue()
        val from = chain.lastOrNull()?.slot ?: 0
        val syncRequest = SyncRequest(networkManager.ourNode, from)
        Logger.info("Requesting new blocks from $from")
        networkManager.sendUDP(Endpoint.SyncRequest, syncRequest, TransmissionType.Unicast, 1)
    }

    /** After synchronization request has been received, we send back blocks node has asked us for. */
    fun syncRequestReceived(message: Message<SyncRequest>) {
        val syncRequest = message.body
        val requestingNode = syncRequest.node
        networkManager.knownNodes.computeIfAbsent(requestingNode.publicKey) { requestingNode }

        val blocks = chain.drop(syncRequest.fromBlock.toInt()).take(1000) // TODO change after retrieving blocks from database.
        if (blocks.isEmpty()) return

        networkManager.sendUDP(Endpoint.SyncReply, blocks, TransmissionType.Unicast, requestingNode)
        Logger.debug("Sent back ${blocks.size} blocks!")
    }

    /** After receiving of blocks, we put them to queue for them to be added to the chain. */
    fun syncReplyReceived(message: Message<Array<Block>>) {
        val blocks = message.body
        Logger.info("We have ${blocks.size} blocks to sync...")
        blockQueue.clear()
        blockQueue.addAll(blocks.map { BlockToAdd(true, it) })
        Logger.info("Syncing finished...")
    }

    /** On single block received, we add it to the chain queue. */
    fun blockReceived(message: Message<Block>) {
        val newBlock = message.body
        blockQueue.offer(BlockToAdd(false, newBlock))
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
            val vdfProof = vdf.findProof(configuration.initialDifficulty, "FFFF", dashboard)
            val block = blockProducer.genesisBlock(vdfProof)
            Logger.debug("Broadcasting genesis block...")
            networkManager.knownNodes.forEach { Logger.info("Sending genesis block to: ${it.value.ip}") }
            networkManager.sendUDP(Endpoint.NewBlock, block, TransmissionType.Broadcast)
        }
    }

    /** Requests inclusion by sending a broadcast message to [n][Configuration.broadcastSpreadPercentage] of random known nodes. */
    fun requestInclusion(block: Block? = null) {
        val slot = block?.slot ?: 0
        val inclusionRequest = InclusionRequest(slot, crypto.publicKey)
        dashboard.requestedInclusion(networkManager.ourNode.ip, slot)
        Logger.debug("Requesting inclusion with slot ${inclusionRequest.currentSlot}...")
        networkManager.sendUDP(Endpoint.InclusionRequest, inclusionRequest, TransmissionType.Broadcast)
    }

}
