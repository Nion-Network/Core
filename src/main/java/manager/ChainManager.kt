package manager

import chain.BlockProducer
import communication.TransmissionType
import data.*
import logging.Logger
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import utils.runAfter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
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
    private val vdf: VDFManager,
    private val dht: DHTManager,
    private val docker: DockerManager,
    private val dashboard: DashboardManager,
    private val informationManager: InformationManager,
    private val blockProducer: BlockProducer
) {
    val isChainEmpty: Boolean get() = chain.isEmpty()


    private val minValidatorsCount = configuration.validatorsCount

    private var isIncluded = networkManager.isTrustedNode
    private val votes = ConcurrentHashMap<String, MutableList<VoteInformation>>()
    private val chain = mutableListOf<Block>()


    private val committeeExecutor = Executors.newSingleThreadScheduledExecutor()
    private var scheduledCommitteeFuture: ScheduledFuture<*>? = null

    /**
     * Adds the specified block to the chain and calculates our task for the next slot.
     *
     * @param block
     */

    private fun addBlock(block: Block, isFromSync: Boolean = false) {
        val blockSlot = block.slot
        val currentSlot = chain.lastOrNull()?.slot ?: 0

        Logger.info("New block came [$blockSlot][$currentSlot] from ${block.blockProducer}")

        if (blockSlot != currentSlot + 1 && !isFromSync) {
            requestSync()
            return
        }


        block.validatorChanges.forEach(blockProducer::validatorChange)

        scheduledCommitteeFuture?.cancel(true)
        chain.add(block)
        votes.remove(block.hash)
        block.validatorChanges.apply {
            val key = crypto.publicKey
            if (this[key] == true) isIncluded = true
            if (this[key] == false) isIncluded = false
        }
        if (isFromSync) return

        if (!isIncluded) requestInclusion()

        val nextTask = calculateNextTask(block)
        if (nextTask.myTask == SlotDuty.PRODUCER || nextTask.myTask == SlotDuty.COMMITTEE) dashboard.newRole(nextTask, DigestUtils.sha256Hex(crypto.publicKey), blockSlot + 1)

        if (networkManager.isTrustedNode) dashboard.newBlockProduced(block, networkManager.knownNodes.size, blockProducer.currentValidators.size)
        Logger.chain("Added block [${block.slot}][${Logger.green}${block.votes}]${Logger.reset} Next task: ${Logger.red}${nextTask.myTask}${Logger.reset}")
        Logger.trace("Next producer is: ${DigestUtils.sha256Hex(nextTask.blockProducer)}")

        if (nextTask.myTask == SlotDuty.PRODUCER) {
            val vdfProof = vdf.findProof(block.difficulty, block.hash)
            val newBlock = blockProducer.createBlock(block, vdfProof, blockSlot + 1)
            val voteRequest = VoteRequest(newBlock, networkManager.ourNode)

            runAfter(configuration.slotDuration * 1 / 3) {
                val message = networkManager.generateMessage(voteRequest)
                networkManager.apply {
                    Logger.trace("Requesting votes!")
                    val committeeNodes = nextTask.committee.mapNotNull { knownNodes[it] }.toTypedArray()
                    sendUDP(EndPoint.OnVoteRequest, message, TransmissionType.Unicast, *committeeNodes)
                }
            }

            runAfter(configuration.slotDuration * 2 / 3) {
                val votesAmount = votes[newBlock.hash]?.size ?: 0
                newBlock.votes = votesAmount

                val broadcastMessage = networkManager.generateMessage(newBlock)
                networkManager.apply {
                    val committeeNodes = nextTask.committee.mapNotNull { knownNodes[it] }.toTypedArray()
                    sendUDP(EndPoint.BlockReceived, broadcastMessage, TransmissionType.Broadcast, *committeeNodes)
                    sendUDP(EndPoint.BlockReceived, broadcastMessage, TransmissionType.Broadcast)
                }
            }
        }
    }

    /*
    private fun addBlock(block: Block, isFromSync: Boolean = false) {

        val blockIndex = block.epoch * configuration.slotCount + block.slot
        val blockAtPosition = chain.getOrNull(blockIndex)
        val blockBefore = chain.getOrNull(blockIndex - 1)
        val previousHash = blockBefore?.hash ?: ""

        // Logger.info("Block at position: ${blockAtPosition == null} $blockIndex vs ${chain.lastIndex}")

        Logger.error("1")
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

        Logger.error("2")
        if (block.precedentHash != previousHash) {
            val currentIndex = block.epoch * configuration.slotCount + block.slot
            if (currentIndex - lastIndexRequest <= 3) return
            lastIndexRequest = currentIndex
            requestSync()
            return
        }

        Logger.error("3")
        currentState.apply {
            block.validatorChanges.forEach { (publicKey, change) ->
                if (change) currentValidators.add(publicKey) else currentValidators.remove(publicKey)
                inclusionChanges.remove(publicKey)
                Logger.info("${publicKey.subSequence(120, 140)} has been ${if (change) "added" else "removed"}")
            }
            slot = block.slot
            epoch = block.epoch
        }
        Logger.error("4")
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

        Logger.error("5")
        if (isFromSync) {
            Logger.chain("Added block [${block.epoch}][${block.slot}][${Logger.green}${block.votes}${Logger.reset}]")
            return
        }

        Logger.error("6")
        val nextTask = calculateNextTask(block, !isFromSync)
        val textColor = when (nextTask.myTask) {
            SlotDuty.PRODUCER -> Logger.green
            SlotDuty.COMMITTEE -> Logger.blue
            SlotDuty.VALIDATOR -> Logger.white
        }
        Logger.error("7")

        // Logger.debug("Clearing statistics!")
        informationManager.latestNetworkStatistics.clear()

        Logger.chain("Added block [${block.epoch}][${block.slot}][${Logger.green}${block.votes}${Logger.reset}] Next task: $textColor${nextTask.myTask}${Logger.reset}")
        if (nextTask.myTask == SlotDuty.PRODUCER || nextTask.myTask == SlotDuty.COMMITTEE) dashboard.newRole(nextTask, DigestUtils.sha256Hex(crypto.publicKey), currentState);
        if (networkManager.isTrustedNode) dashboard.newBlockProduced(currentState, block, knownNodes.size)


        Logger.error("8")
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
                    dashboard.scheduledTimer(nextTask.myTask, currentState)
                    val message = networkManager.generateMessage(voteRequest)
                    networkManager.apply {
                        nextTask.committee.forEach { key -> sendMessage(knownNodes[key], EndPoint.OnVoteRequest, message) }
                    }
                }

                runAfter(configuration.slotDuration * 2 / 3) {
                    dashboard.scheduledTimer(nextTask.myTask, currentState)
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
                /*
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

                 */
            }
            SlotDuty.VALIDATOR -> if (!isFromSync) informationManager.prepareForStatistics(nextTask.blockProducer, currentState.currentValidators, block)
        }
    }
    */

    /**
     * Request blocks from a random known node needed for synchronization.
     *
     */
    private fun requestSync() {
        networkManager.clearMessageQueue()
        val from = chain.lastOrNull()?.slot ?: 0
        val message = networkManager.generateMessage(from)
        Logger.info("Requesting new blocks from $from")
        val trusted = Node("", configuration.trustedNodeIP, configuration.trustedNodePort)
        networkManager.sendUDP(EndPoint.SyncRequest, message, TransmissionType.Unicast, trusted)
    }

    /**
     * After synchronization request has been received, we send back the blocks node has asked us for.
     *
     * @param body Web request body.
     */
    fun syncRequestReceived(message: Message<Int>) {
        val node = networkManager.knownNodes[message.publicKey] ?: return
        val blocks = chain.drop(message.body).take(50)
        val responseBlocksMessageBody = networkManager.generateMessage(blocks)
        networkManager.sendUDP(EndPoint.SyncReply, responseBlocksMessageBody, TransmissionType.Unicast, node)
        Logger.debug("Sent back ${blocks.size} blocks!")
    }

    /**
     * Received blocks for chain synchronization.
     *
     * @param context Web request context.
     */
    fun syncReplyReceived(message: Message<Array<Block>>) {
        val blocks = message.body
        Logger.info("We have ${blocks.size} blocks to sync...")
        blocks.forEach { block -> addBlock(block, true) }
        Logger.info("Syncing finished...")
    }

    fun blockReceived(message: Message<Block>) {
        val newBlock = message.body
        addBlock(newBlock)
    }

    private fun calculateNextTask(block: Block): ChainTask {
        val seed = block.getRandomSeed
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

    fun voteReceived(message: Message<BlockVote>) {
        val blockVote = message.body
        Logger.trace("Vote received!")
        votes.getOrPut(blockVote.blockHash) { mutableListOf() }.add(VoteInformation(message.publicKey))
    }

    private fun canBeIncluded(inclusionRequest: InclusionRequest): Boolean {
        if (!isIncluded) return false
        val lastBlock = chain.lastOrNull() ?: return isIncluded
        return lastBlock.slot == inclusionRequest.currentSlot
    }

    private fun revertChanges(block: Block) {
        blockProducer.currentValidators.apply {
            block.validatorChanges.forEach { (publicKey, change) ->
                if (change) remove(publicKey)
                else add(publicKey)
            }
        }
    }

    fun inclusionRequest(message: Message<InclusionRequest>) {
        val publicKey = message.publicKey
        val inclusionRequest = message.body
        val canBeIncluded = canBeIncluded(inclusionRequest)
        Logger.error("Inclusion request received: ${inclusionRequest.currentSlot} and can be included: ${Logger.yellow} $canBeIncluded${Logger.reset}")
        if (!canBeIncluded) return

        blockProducer.inclusionChanges[publicKey] = true

        val currentValidatorsSize = blockProducer.currentValidators.size
        val newValidators = blockProducer.inclusionChanges.filter { it.value }.count()

        val isEnoughIncluded = currentValidatorsSize + newValidators >= minValidatorsCount + 1
        val isChainEmpty = isChainEmpty
        if (networkManager.isTrustedNode && isChainEmpty && isEnoughIncluded) {
            val vdfProof = vdf.findProof(configuration.initialDifficulty, "FFFF")
            val block = blockProducer.genesisBlock(vdfProof)
            Logger.debug("Broadcasting genesis block...")
            networkManager.knownNodes.forEach { Logger.info("Sending genesis block to: ${it.value.ip}") }
            networkManager.sendUDP(EndPoint.BlockReceived, networkManager.generateMessage(block), TransmissionType.Broadcast)
        }
    }

    fun requestInclusion(askTrusted: Boolean = false) {
        networkManager.apply {
            val slot = chain.lastOrNull()?.slot ?: 0
            val inclusionRequest = InclusionRequest(slot, crypto.publicKey)
            Logger.debug("Requesting inclusion with slot ${inclusionRequest.currentSlot}...")
            val message = generateMessage(inclusionRequest)
            val node = if (askTrusted) knownNodes.values.firstOrNull { it.ip == configuration.trustedNodeIP && it.port == configuration.trustedNodePort }
            else knownNodes.values.random()
            (node ?: knownNodes.values.random()).apply {
                sendUDP(EndPoint.Include, message, TransmissionType.Unicast, this)
                networkManager.dashboard.requestedInclusion(crypto.publicKey, this.publicKey, slot)
            }
        }
    }

}