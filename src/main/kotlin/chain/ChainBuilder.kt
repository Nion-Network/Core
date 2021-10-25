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
import data.network.Endpoint
import logging.Dashboard
import logging.Logger
import manager.VerifiableDelayFunctionManager
import network.Network
import utils.Crypto
import utils.runAfter
import java.lang.Long.max

/**
 * Created by Mihael Valentin Berčič
 * on 25/10/2021 at 09:25
 * using IntelliJ IDEA
 */
class ChainBuilder(
    private val network: Network,
    private val crypto: Crypto,
    private val configuration: Configuration,
    private val committeeStrategy: CommitteeStrategy,
    private val vdf: VerifiableDelayFunctionManager
) {

    private val chainHistory = ChainHistory(crypto, configuration, this, network.isTrustedNode)

    fun blockReceived(message: Message<Block>) {
        val newBlock = message.body
        chainHistory.addBlock(newBlock)
        if (!chainHistory.isInValidatorSet) requestInclusion(newBlock.slot)
    }

    fun produceBlock(previousBlock: Block, nextTask: ChainTask) {
        try {
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
                    Dashboard.vdfInformation("TIME TO BE A BLOCK PRODUCER.")
                    runAfter(firstDelay) {

                        runAfter(delayThird) {
                            committeeStrategy.requestVotes(newBlock, committeeNodes)

                            runAfter(delayThird) {
                                val blockToBroadcast = committeeStrategy.getVotes(newBlock)
                                network.send(Endpoint.NewBlock, TransmissionType.Broadcast, blockToBroadcast)
                                Dashboard.newBlockProduced(blockToBroadcast, network.knownNodes.size, chainHistory.getValidatorSize())
                                Dashboard.vdfInformation("Finished being a block producer!")
                            }
                        }
                    }
                }
                SlotDuty.COMMITTEE -> network.searchAndSend(Endpoint.NewBlock, TransmissionType.Unicast, previousBlock, nextTask.blockProducer)
                SlotDuty.VALIDATOR -> {}
            }
        } catch (e:Exception){
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
        network.send(Endpoint.InclusionRequest, TransmissionType.Broadcast, inclusionRequest)
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
        val blocksToSend = chainHistory.getBlocks(request.fromBlock)
        network.send(Endpoint.SyncReply, TransmissionType.Unicast, blocksToSend, request.node)
    }

    fun syncReplyReceived(message: Message<Array<Block>>) {
        val blocks = message.body
        chainHistory.addBlocks(blocks)
    }

}