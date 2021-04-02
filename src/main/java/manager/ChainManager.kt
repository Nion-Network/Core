package manager

import chain.BlockProducer
import data.*
import logging.Logger
import org.apache.commons.codec.digest.DigestUtils
import utils.runAfter
import java.util.concurrent.ConcurrentHashMap
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

    private var isIncluded: Boolean = false
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

    private var isSynced = false

    val blockProducer = BlockProducer(crypto, configuration, currentState)
    val validatorManager = ValidatorManager(networkManager, this)


    /**
     * Adds the specified block to the chain and calculates our task for the next slot.
     *
     * @param block
     */
    fun addBlock(block: Block, fromSync: Boolean = false) {

        val blockIndex = block.epoch * configuration.slotCount + block.slot
        val blockAtPosition = chain.getOrNull(blockIndex)
        val blockBefore = chain.getOrNull(blockIndex - 1)
        val previousHash = blockBefore?.hash ?: ""

        // Logger.info("Block at position: ${blockAtPosition == null} $blockIndex vs ${chain.lastIndex}")
        if (block.precedentHash != previousHash) {
            requestSync()
            return
        }
        if (blockAtPosition != null) {
            val hasMoreVotes = block.votes > blockAtPosition.votes
            val isLast = chain.lastIndex == blockIndex
            Logger.info("Is last? $isLast ... has more votes? $hasMoreVotes ... same hash: ${block.hash == blockAtPosition.hash}")

            when {
                block.hash == blockAtPosition.hash -> return
                hasMoreVotes -> {
                    chain.dropLast(chain.size - blockIndex)
                    if (!isLast) {
                        requestSync()
                        return
                    }
                }
            }
        }

        currentState.apply {
            block.validatorChanges.forEach { (publicKey, change) ->
                if (change) currentValidators.add(publicKey) else currentValidators.remove(publicKey)
                inclusionChanges.remove(publicKey)
                Logger.trace("${publicKey.subSequence(120, 140)} has been ${if (change) "added" else "removed"}")
            }
            slot = block.slot
            epoch = block.epoch
        }

        val myMigration = block.migrations[crypto.publicKey]
        if (!fromSync && myMigration != null) {
            val toSend = myMigration.containerName
            val receiverNodePublicKey = myMigration.toNode.apply { dht.searchFor(this) }
            val savedImage = dockerManager.saveImage(toSend)
            val receiver = knownNodes[myMigration.toNode] ?: throw Exception("Not able to find ${receiverNodePublicKey.take(16)}")

            Logger.info("We have to send container $toSend to ${receiver.ip}")
            val startOfMigration = System.currentTimeMillis();
            receiver.sendFile(EndPoint.RunMigratedImage, savedImage, toSend)
            val migrationDuration = System.currentTimeMillis() - startOfMigration;
            dashboard.newMigration(DigestUtils.sha256Hex(receiver.publicKey), DigestUtils.sha256Hex(crypto.publicKey), toSend, migrationDuration, currentState)
            savedImage.delete()
            dockerManager.ourContainers.remove(toSend)
        }

        chain.add(block)
        votes.remove(block.hash)
        if (!isIncluded && block.validatorChanges[crypto.publicKey] == true) isIncluded = true

        val nextTask = calculateNextTask(block, !fromSync)
        val textColor = when (nextTask.myTask) {
            SlotDuty.PRODUCER -> Logger.green
            SlotDuty.COMMITTEE -> Logger.blue
            SlotDuty.VALIDATOR -> Logger.white
        }

        // Logger.debug("Clearing statistics!")
        informationManager.latestNetworkStatistics.clear()

        Logger.chain("Added block with [epoch][slot][votes] => [${block.epoch}][${block.slot}][${Logger.green}${block.votes}${Logger.reset}] Next task: $textColor${nextTask.myTask}")
        dashboard.newRole(nextTask, DigestUtils.sha256Hex(crypto.publicKey), currentState);
        if (networkManager.isTrustedNode) dashboard.newBlockProduced(block)

        when (nextTask.myTask) {
            SlotDuty.PRODUCER -> {
                // if (networkManager.isTrustedNode && block.epoch >= 1) exitProcess(-1)
                if (fromSync) Logger.error("This is impossible to have happened. Check the whole implementation")

                val vdfProof = vdf.findProof(block.difficulty, block.hash)
                if (++currentState.slot == configuration.slotCount) {
                    currentState.epoch++
                    currentState.slot = 0
                }
                val newBlock = blockProducer.createBlock(block, vdfProof)
                val voteRequest = VoteRequest(newBlock, networkManager.ourNode)

                runAfter(500) {
                    val message = networkManager.generateMessage(voteRequest)
                    nextTask.committee.forEach { key -> networkManager.knownNodes[key]?.sendMessage(EndPoint.OnVoteRequest, message) }
                }

                runAfter(configuration.slotDuration * 2 / 3) {
                    Logger.debug("Running a block after (slotDuration * 2) / 3 ...")
                    val thisBlockVotes = votes[newBlock.hash]
                    val votesAmount = thisBlockVotes?.size ?: 0
                    val broadcastMessage = networkManager.generateMessage(newBlock)

                    val latestStatistics = informationManager.latestNetworkStatistics
                    Logger.info("We have ${latestStatistics.size} latest statistics!")
                    val mostUsedNode = latestStatistics.maxBy { it.totalCPU }
                    val leastUsedNode = latestStatistics.filter { it.publicKey != mostUsedNode?.publicKey }.minBy { it.totalCPU }

                    Logger.info("Most used node: $mostUsedNode")
                    Logger.info("Least used node: $leastUsedNode")

                    if (leastUsedNode != null && mostUsedNode != null) {
                        val leastConsumingApp = mostUsedNode.containers.minBy { it.cpuUsage }
                        Logger.debug("Least consuming app: $leastConsumingApp")
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
                    networkManager.broadcast(EndPoint.BlockReceived, broadcastMessage)
                    addBlock(newBlock)
                    newBlock.validatorChanges.forEach { (key, _) -> currentState.inclusionChanges.remove(key) }
                }
            }
            SlotDuty.COMMITTEE -> {
                if (!fromSync) {
                    informationManager.prepareForStatistics(nextTask.blockProducer, currentState.currentValidators, block)
                    val delay = configuration.slotDuration * 1.5
                    val nextIndex = block.epoch * configuration.slotCount + block.slot + 1
                    runAfter(delay.toLong()) {
                        val hasReceived = chain.getOrNull(nextIndex) != null
                        if (hasReceived) return@runAfter
                        Logger.error("Block has not been received! Creating skip block!")

                        if (++currentState.slot == configuration.slotCount) {
                            currentState.epoch++
                            currentState.slot = 0
                        }
                        currentState.inclusionChanges[nextTask.blockProducer] = false
                        val skipBlock = blockProducer.createSkipBlock(block)
                        val message = networkManager.generateMessage(skipBlock)
                        networkManager.broadcast(EndPoint.BlockReceived, message)
                        addBlock(skipBlock)
                    }
                }
            }
            SlotDuty.VALIDATOR -> if (!fromSync) informationManager.prepareForStatistics(nextTask.blockProducer, currentState.currentValidators, block)
        }
    }

    /**
     * Request blocks from a random known node needed for synchronization.
     *
     */
    private fun requestSync() {
        isSynced = false
        val from = currentState.epoch * configuration.slotCount + currentState.slot
        val message = networkManager.generateMessage(from)
        Logger.trace("Requesting new blocks from $from")
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
        knownNodes[message.publicKey]?.sendMessage(EndPoint.SyncReply, responseBlocksMessageBody)
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
        isSynced = true
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
        val lastBlock = chain.lastOrNull() ?: return true
        return lastBlock.epoch == inclusionRequest.currentEpoch && lastBlock.slot == inclusionRequest.currentSlot
    }
}