package common

import abstraction.ProtocolTasks
import configuration.Configuration
import logging.Dashboard
import logging.Logger
import network.NetworkManager
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import utils.VDF
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.abs

class BlockChain(private var crypto: Crypto, private var vdf: VDF, private val configuration: Configuration) {

    lateinit var networkManager: NetworkManager
    // private var expectedBlock: ExpectedBlock? = null

    val chain = mutableListOf<BlockData>()
    var isValidator: Boolean = false
    var isSynced: Boolean = false

    val pendingInclusionRequests = mutableListOf<String>()
    val lastBlock: BlockData? get() = chain.lastOrNull()

    private var service = Executors.newSingleThreadScheduledExecutor()
    private var schedulingThread = Thread {}
    private var epoch = 0


    @Synchronized
    fun addBlock(blockData: BlockData): Boolean {
        val height = blockData.height
        val hash = blockData.hash

        isValidator = blockData.consensusNodes.contains(crypto.publicKey)
        Logger.debug("Are we validators? $isValidator")

        when {
            chain.isEmpty() && height == 0 -> {
                chain.add(blockData)
                Logger.chain("Added genesis block")
                return true
            }
            !isSynced -> {
                Logger.debug("We appear to not be synced, requesting synchronization...")
                networkManager.initiate(ProtocolTasks.requestBlocks, chain.size)
                return false
            }
            height == lastBlock!!.height + 1 -> { //present
                Logger.debug("Block we're attempting to add what appears to be a successor of our last block (height > lastHeight)...")
                if (blockData.previousBlockHash != lastBlock?.hash) {
                    Logger.error("Previous block hash doesn't match our last block hash... Running fork resolution by pop and sync...")
                    lastBlock?.apply { chain.remove(this) }
                    isSynced = false
                    networkManager.initiate(ProtocolTasks.requestBlocks, chain.size)
                    return false
                } else if (isProofValid(blockData)) {
                    Logger.debug("Proof for $hash appears to be valid and we're adding the block to the chain...")
                    chain.add(blockData)
                    Dashboard.newBlockAccepted(blockData,crypto)
                    Dashboard.newLottery(distance(blockData.vdfProof?:"",crypto.publicKey),crypto, blockData.height)

                    Logger.chain("Attempting to add a block [$height] | $hash...")
                    Logger.debug("Running ${Thread.activeCount()} threads...");

                    Logger.debug("----------------------- Current chain -----------------------")
                    printChain()
                    Logger.debug("----------------------- End of current chain -----------------------")
                    if (isValidator && isSynced) {
                        Logger.consensus("We seem to be validators. Running VDF for next height at ${height + 1}...")
                        vdf.runVDF(blockData.difficulty, hash, (height + 1))
                    } else Logger.consensus("We're not a validator node, skipping block creation")
                    return true
                } else Logger.consensus("Proof validation failed")
                return false
            }
            height > lastBlock!!.height +1 ->{ //block in the future. Block validation will fail, but we could have forked.
                Logger.consensus("Block $height is in the future, initiate pop and re-sync")
                Dashboard.possibleFork(blockData,crypto, chain.last())
                chain.remove(chain.last()) //pop last block
                isSynced=false
                networkManager.initiate(ProtocolTasks.requestBlocks,chain.size)
                return false
            }

            else -> return false.apply { Logger.debug("Block validation has failed...") }
        }
    }
    @Synchronized
    fun updateVdf(vdfProof: String, height: Int): Boolean {
        val lastBlock = lastBlock
        val lastHeight = lastBlock?.height ?: 1
        val lastProof = lastBlock?.vdfProof

        if (height <= lastHeight) {
            Logger.debug("Proof: ${DigestUtils.sha256Hex(vdfProof)} for block $height is old")
            return false
        }

        when (vdfProof) {
            lastProof -> Logger.trace("Proof already known for height $height...")
            else -> {
                Logger.trace("Verifying vdf proof for height $height with last block: ${lastBlock?.hash} ...")
                lastBlock?.apply {
                    val lastDifficulty = difficulty
                    val lastHash = hash
                    val lastConsensusNodes = consensusNodes
                    val lotteryResults = lotteryResults(vdfProof, lastConsensusNodes)

                    if (vdf.verifyProof(lastDifficulty, lastHash, vdfProof)) {
                        Logger.debug("Proof seems to be verified... ")

                        if (!isValidator) {
                            Logger.consensus("We're not in the validator set for block $height")
                            return false
                        }

                        val myTurn = lotteryResults.indexOf(crypto.publicKey)
                        val delta: Long = configuration.epochDuration
                        val deadline = myTurn * delta + System.currentTimeMillis()

                        schedulingThread.interrupt()
                        schedulingThread = Thread {
                            Logger.debug("Scheduling block creation in $delta...")
                            Logger.chain("Scheduling thread is running... [myTurn = $myTurn] vs [epoch = $epoch]")
                            while (System.currentTimeMillis() < deadline);

                            if (lastBlock.height == height) return@Thread


                            val newBlock: BlockData = BlockData.forgeNewBlock(chain.last(), vdfProof, crypto.publicKey, pendingInclusionRequests,distance(vdfProof,crypto.publicKey))
                            Logger.consensus("New block forged at height $height in $myTurn epoch with has ${newBlock.hash}")
                            if (addBlock(newBlock)) networkManager.initiate(ProtocolTasks.newBlock, newBlock)

                            Logger.error("Thread stop ${System.currentTimeMillis()}")

                        }
                        schedulingThread.start()
                        Logger.consensus("Scheduled block creation in $delta ms as $myTurn best lottery drawn")
                        return true
                    }
                }
            }
        }
        return false
    }

    fun syncChain(blocks: List<BlockData>) {
        Logger.debug("Syncing chain with ${blocks.size} blocks...")
        if (chain.isEmpty()) chain.addAll(blocks).apply { Logger.debug("We've added all blocks to the chain...") }
        else {
            Logger.info("Syncing chain with a not yet done feature. This is a TODO! BlockChain.kt @syncChain...")
            blocks.forEach { block ->
                lastBlock?.apply {
                    if (vdf.verifyProof(difficulty, hash, block.vdfProof)) chain.add(block)
                }
            }
            /*
            for (b in blocks) {
                //TODO: verify proofs and rebuild state by state
                if (b.height > chain.size && chain.last().hash.equals(b.previous_hash)) {
                    Logger.chain("Chain size: " + chain.size + " Adding block " + b.height + " : " + b.hash)
                    chain.add(b)
                }
            }
             */
        }
        isSynced = true
    }

    private fun printChain() = chain.forEach { Logger.chain("Height: ${it.height} | Hash: ${it.hash} | Producer: ${DigestUtils.sha256Hex(it.blockProducer)}") }

    private fun lotteryResults(vdfProof: String, candidates: List<String>): List<String> = candidates.sortedWith(Comparator.comparingInt { S: String -> distance(vdfProof, S) })

    private fun distance(proof: String, node_id: String): Int {
        val nodeId = DigestUtils.sha256Hex(node_id)
        val seed = java.lang.Long.parseUnsignedLong(proof.substring(0, 16), 16)
        val random = Random(seed)
        val draw = random.nextDouble()
        val front = abs(java.lang.Long.parseUnsignedLong(nodeId.substring(0, 16), 16) / Long.MAX_VALUE.toDouble())
        return (abs(front - draw) * 100000).toInt()
    }

    private fun isProofValid(blockData: BlockData): Boolean {
        val lastBlock = lastBlock
        Logger.trace("Checking if proof is valid with previous block hash: ${lastBlock?.hash}")

        if (lastBlock == null) return false

        Logger.debug("Last block is not null, therefor we can verify the proof...")
        val lastDifficulty = lastBlock.difficulty
        val lastHash = lastBlock.hash
        val newProof = blockData.vdfProof
        val newBlockProducer = blockData.blockProducer

        val isValidVdf = vdf.verifyProof(lastDifficulty, lastHash, newProof)
        if (isValidVdf) {
            Logger.consensus("Vdf proof for ${blockData.hash} seems to be valid...")
            return true
        } else Logger.error("Vdf proof is invalid for ${blockData.hash} Check BlockChain.kt @isProofValid...")
        return false

    }
}

private data class ExpectedBlock(val previousBlockHash: String, var vdfProof: String? = null, var blockProducer: String? = null, val height: Int)