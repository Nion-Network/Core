package manager

import chain.BlockProducer
import data.*
import logging.Logger
import org.apache.commons.codec.digest.DigestUtils
import utils.runAfter
import java.lang.Integer.max
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random


/**
 * Created by Mihael Valentin Berčič
 * on 25/09/2020 at 16:58
 * using IntelliJ IDEA
 */
class ChainManager(private val networkManager: NetworkManager) {

    val isChainEmpty: Boolean get() = chain.isEmpty()

    private var isIncluded: Boolean = networkManager.isTrustedNode
    private val crypto = networkManager.crypto
    private val configuration = networkManager.configuration
    private val currentState = networkManager.currentState
    private val vdf = networkManager.vdf
    private val dht = networkManager.dht
    private val dockerManager = networkManager.docker
    private val dashboard = networkManager.dashboard
    private val informationManager = networkManager.informationManager
    private val knownNodes = networkManager.knownNodes

    private val votes = ConcurrentHashMap<String, MutableList<VoteInformation>>()
    private val chain = mutableListOf<Block>()

    val blockProducer = BlockProducer(crypto, configuration, currentState)
    val validatorManager = ValidatorManager(networkManager, this)

    private val committeeExecutor = Executors.newSingleThreadScheduledExecutor()
    private var scheduledCommitteeFuture: ScheduledFuture<*>? = null
    private var lastIndexRequest = -1

    /**
     * Adds the specified block to the chain and calculates our task for the next slot.
     *
     * @param block
     */

    private fun addBlock(block: Block, isFromSync: Boolean = false) {

        val blockIndex = block.epoch * configuration.slotCount + block.slot
        val blockAtPosition = chain.getOrNull(blockIndex)
        val blockBefore = chain.getOrNull(blockIndex - 1)
        val previousHash = blockBefore?.hash ?: ""

        // Logger.info("Block at position: ${blockAtPosition == null} $blockIndex vs ${chain.lastIndex}")

        if (blockAtPosition != null) {
            val hasMoreVotes = block.votes > blockAtPosition.votes
            val isLast = chain.lastIndex == blockIndex
            Logger.info(
                "[${block.epoch}][${block.slot}] | [${chain.lastIndex} vs $blockIndex] Is last? $isLast ... has more votes? [${block.votes} vs " +
                        "${blockAtPosition.votes}] $hasMoreVotes ... same hash: ${block.hash == blockAtPosition.hash}"
            )

            if (hasMoreVotes) {
                val amountToTake = chain.size - blockIndex
                val lastBlocks = chain.takeLast(amountToTake)
                lastBlocks.forEach(this::revertChanges)
                chain.removeAll(lastBlocks)
            } else return
        }

        if (block.precedentHash != previousHash) {
            val currentIndex = block.epoch * configuration.slotCount + block.slot
            if (currentIndex - lastIndexRequest <= 3) return
            lastIndexRequest = currentIndex
            requestSync()
            return
        }

        currentState.apply {
            block.validatorChanges.forEach { (publicKey, change) ->
                if (change) currentValidators.add(publicKey) else currentValidators.remove(publicKey)
                inclusionChanges.remove(publicKey)
                Logger.info("${publicKey.subSequence(120, 140)} has been ${if (change) "added" else "removed"}")
            }
            slot = block.slot
            epoch = block.epoch
        }

        val myMigration = block.migrations[crypto.publicKey]
        if (!isFromSync && myMigration != null) {
            val toSend = myMigration.containerName
            val receiverNodePublicKey = myMigration.toNode.apply { dht.searchFor(this) }
            val savedImage = dockerManager.saveImage(toSend)
            val receiver = knownNodes[myMigration.toNode] ?: throw Exception("Not able to find ${receiverNodePublicKey.take(16)}")

            Logger.info("We have to send container $toSend to ${receiver.ip}")
            val startOfMigration = System.currentTimeMillis();
            // TODO send file ... receiver.sendFile(EndPoint.RunMigratedImage, savedImage, toSend)
            val migrationDuration = System.currentTimeMillis() - startOfMigration;
            dashboard.newMigration(DigestUtils.sha256Hex(receiver.publicKey), DigestUtils.sha256Hex(crypto.publicKey), toSend, migrationDuration, currentState)
            savedImage.delete()
            dockerManager.ourContainers.remove(toSend)
        }

        scheduledCommitteeFuture?.cancel(true)
        chain.add(block)
        votes.remove(block.hash)
        block.validatorChanges.apply {
            val key = crypto.publicKey
            if (this[key] == true) isIncluded = true
            if (this[key] == false) isIncluded = false
        }

        if (isFromSync) {
            Logger.chain("Added block [${block.epoch}][${block.slot}][${Logger.green}${block.votes}${Logger.reset}]")
            return
        }

        val nextTask = calculateNextTask(block, !isFromSync)
        val textColor = when (nextTask.myTask) {
            SlotDuty.PRODUCER -> Logger.green
            SlotDuty.COMMITTEE -> Logger.blue
            SlotDuty.VALIDATOR -> Logger.white
        }

        // Logger.debug("Clearing statistics!")
        informationManager.latestNetworkStatistics.clear()

        Logger.chain("Added block [${block.epoch}][${block.slot}][${Logger.green}${block.votes}${Logger.reset}] Next task: $textColor${nextTask.myTask}${Logger.reset}")
        dashboard.newRole(nextTask, DigestUtils.sha256Hex(crypto.publicKey), currentState);
        if (networkManager.isTrustedNode) dashboard.newBlockProduced(currentState, block, knownNodes.size)


        when (nextTask.myTask) {
            SlotDuty.PRODUCER -> {
                val vdfProof = vdf.findProof(block.difficulty, block.hash)
                if (++currentState.slot == configuration.slotCount) {
                    currentState.epoch++
                    currentState.slot = 0
                }
                val newBlock = blockProducer.createBlock(block, vdfProof)
                val voteRequest = VoteRequest(newBlock, networkManager.ourNode)

                runAfter(configuration.slotDuration * 1 / 3) {
                    val message = networkManager.generateMessage(voteRequest)
                    networkManager.apply {
                        nextTask.committee.forEach { key -> sendMessage(knownNodes[key], EndPoint.OnVoteRequest, message) }
                    }
                }

                runAfter(configuration.slotDuration * 2 / 3) {
                    val votesAmount = votes[newBlock.hash]?.size ?: 0
                    val latestStatistics = informationManager.latestNetworkStatistics
                    Logger.info("\t\tWe have ${latestStatistics.size} latest statistics!")
                    val mostUsedNode = latestStatistics.maxBy { it.totalCPU }
                    val leastUsedNode = latestStatistics.filter { it.publicKey != mostUsedNode?.publicKey }.minBy { it.totalCPU }

                    Logger.info("\t\tMost used node: $mostUsedNode")
                    Logger.info("\t\tLeast used node: $leastUsedNode")

                    if (leastUsedNode != null && mostUsedNode != null) {
                        val leastConsumingApp = mostUsedNode.containers.minBy { it.cpuUsage }
                        Logger.debug("\t\tLeast consuming app: $leastConsumingApp")
                        if (leastConsumingApp != null) {
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
                                val newMigration = Migration(mostUsedNode.publicKey, leastUsedNode.publicKey, leastConsumingApp.name)
                                newBlock.migrations[mostUsedNode.publicKey] = newMigration
                            }
                        }
                    }
                    dashboard.reportStatistics(latestStatistics, currentState)
                    newBlock.votes = votesAmount

                    val broadcastMessage = networkManager.generateMessage(newBlock)
                    networkManager.apply {
                        nextTask.committee.forEach { key -> sendMessage(knownNodes[key], EndPoint.BlockReceived, broadcastMessage) }
                    }
                    networkManager.broadcast(EndPoint.BlockReceived, broadcastMessage)
                    // addBlock(newBlock)
                    newBlock.validatorChanges.forEach { (key, _) -> currentState.inclusionChanges.remove(key) }
                }
            }
            SlotDuty.COMMITTEE -> {
                informationManager.prepareForStatistics(nextTask.blockProducer, currentState.currentValidators, block)
                val delay = configuration.slotDuration * 1.5
                val nextIndex = block.epoch * configuration.slotCount + block.slot + 1
                scheduledCommitteeFuture = committeeExecutor.schedule({
                    val hasReceived = chain.getOrNull(nextIndex) != null
                    // if (hasReceived) return@runAfter
                    Logger.error("Block has not been received! Creating skip block!")

                    if (++currentState.slot == configuration.slotCount) {
                        currentState.epoch++
                        currentState.slot = 0
                    }
                    currentState.inclusionChanges[nextTask.blockProducer] = false
                    val skipBlock = blockProducer.createSkipBlock(block)
                    val message = networkManager.generateMessage(skipBlock)
                    networkManager.broadcast(EndPoint.BlockReceived, message)
                    // addBlock(skipBlock)
                }, delay.toLong(), TimeUnit.MILLISECONDS)

            }
            SlotDuty.VALIDATOR -> if (!isFromSync) informationManager.prepareForStatistics(nextTask.blockProducer, currentState.currentValidators, block)
        }
    }

    /**
     * Request blocks from a random known node needed for synchronization.
     *
     */
    private fun requestSync() {
        networkManager.clearMessageQueue()
        val from = max(0, currentState.epoch * configuration.slotCount + currentState.slot)
        val message = networkManager.generateMessage(from)
        Logger.info("Requesting new blocks from $from")
        networkManager.sendMessageToRandomNodes(EndPoint.SyncRequest, 1, message)
    }

    /**
     * After synchronization request has been received, we send back the blocks node has asked us for.
     *
     * @param body Web request body.
     */
    fun syncRequestReceived(message: Message<Int>) {
        val blocks = chain.drop(message.body)
        val responseBlocksMessageBody = networkManager.generateMessage(blocks)
        val node = knownNodes[message.publicKey] ?: return
        networkManager.sendMessage(node, EndPoint.SyncReply, responseBlocksMessageBody)
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
            currentState.slot = block.slot
            currentState.epoch = block.epoch
        }
        Logger.info("Syncing finished...")
    }

    fun blockReceived(message: Message<Block>) {
        val newBlock = message.body
        networkManager.broadcast(EndPoint.BlockReceived, message)
        addBlock(newBlock)
    }

    private fun calculateNextTask(block: Block, askForInclusion: Boolean = true): ChainTask {
        val seed = block.getRandomSeed
        val random = Random(seed)
        val ourKey = crypto.publicKey

        val validatorSetCopy = currentState.currentValidators.shuffled(random).toMutableList()
        val blockProducerNode = validatorSetCopy[0].apply { validatorSetCopy.remove(this) }
        val committee = validatorSetCopy.take(configuration.committeeSize)

        if (askForInclusion && !isIncluded) validatorManager.requestInclusion(blockProducerNode)

        val ourRole = when {
            blockProducerNode == ourKey -> SlotDuty.PRODUCER
            committee.contains(ourKey) -> SlotDuty.COMMITTEE
            else -> SlotDuty.VALIDATOR
        }

        if (ourRole == SlotDuty.PRODUCER) committee.forEach(dht::searchFor)
        return ChainTask(ourRole, blockProducerNode, committee)
    }

    fun voteReceived(message: Message<BlockVote>) {
        val blockVote = message.body
        votes.getOrPut(blockVote.blockHash) { mutableListOf() }.add(VoteInformation(message.publicKey))
    }

    fun canBeIncluded(inclusionRequest: InclusionRequest): Boolean {
        if (!isIncluded) return false
        val lastBlock = chain.lastOrNull() ?: return isIncluded
        return lastBlock.epoch == inclusionRequest.currentEpoch && lastBlock.slot == inclusionRequest.currentSlot
    }

    private fun revertChanges(block: Block) {
        currentState.currentValidators.apply {
            block.validatorChanges.forEach { (publicKey, change) ->
                if (change) remove(publicKey)
                else add(publicKey)
            }
        }
    }
}