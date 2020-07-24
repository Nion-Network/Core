package common

import abstraction.ProtocolTasks
import configuration.Configuration
import logging.Logger
import network.NetworkManager
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import utils.VDF
import java.util.*
import kotlin.math.abs

class BlockChain(private var crypto: Crypto, private var vdf: VDF, private val configuration: Configuration) {

    lateinit var networkManager: NetworkManager
    // private var expectedBlock: ExpectedBlock? = null

    val chain = mutableListOf<BlockData>()
    var isValidator: Boolean = false
    var isSynced: Boolean = false

    val pendingInclusionRequests = mutableListOf<String>()
    val lastBlock: BlockData? get() = chain.lastOrNull()

    private var timer: Timer = Timer()


    fun addBlock(blockData: BlockData): Boolean {
        Logger.debug("WTF")
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
            height > lastBlock!!.height -> {
                Logger.debug("Block we're attempting to add appears to be a successor of our last block (height > lastHeight)...")
                if (isProofValid(blockData)) {
                    Logger.debug("Proof for $hash appears to be valid and we're adding the block to the chain...")
                    chain.add(blockData)

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
            else -> return false.apply { Logger.debug("Block validation has failed...") }
        }
    }

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
                    var epoch = 0

                    if (vdf.verifyProof(lastDifficulty, lastHash, vdfProof)) {
                        Logger.debug("Proof seems to be verified... ")

                        if (!isValidator) {
                            Logger.consensus("We're not in the validator set for block $height")
                            return false
                        }

                        val myTurn = lotteryResults.indexOf(crypto.publicKey)
                        val delta: Long = configuration.epochDuration

                        Logger.debug("Scheduling block creation in $delta...")
                        timer.cancel()
                        timer = Timer()
                        timer.scheduleAtFixedRate(object : TimerTask() {
                            override fun run() {
                                Logger.chain("Timer is running...")
                                // Logger.consensus("Moving epoch to: $epoch  with expected block producer as: ${DigestUtils.sha256Hex(expectedBlock!!.blockProducer)}")
                                if (epoch == myTurn) {
                                    Logger.consensus("New block forged at height $height in $myTurn epoch")
                                    val newBlock: BlockData = BlockData.forgeNewBlock(chain.last(), vdfProof, crypto.publicKey, pendingInclusionRequests).apply {
                                        Logger.debug("ADDBLOCK")
                                        addBlock(this)
                                        networkManager.initiate(ProtocolTasks.newBlock, this)
                                        Logger.debug("KARKOL")
                                    }
                                }
                                epoch++
                            }
                        }, 0, delta)
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

    /*
    private fun isSuccessorBlock(blockData: BlockData): Boolean = expectedBlock.apply { Logger.error("Expected block: $this") }?.let { expectedBlock ->
        val expectedHeight = expectedBlock.height
        val expectedBlockProducer = expectedBlock.blockProducer
        val blockProducer = blockData.blockProducer
        val blockHeight = blockData.height

        Logger.debug("Expected: ${expectedBlockProducer?.substring(0..60)}")
        Logger.debug("Received: ${blockProducer?.substring(0..60)}")
        Logger.debug(blockData.consensusNodes.map { it.substring(0..60) })

        if (expectedHeight != blockHeight) Logger.chain("Expecting block at height: $expectedHeight. Received $blockHeight")
        else when (expectedBlockProducer) {
            blockProducer -> {
                Logger.chain("Block validation passed")
                return true
            }
            null -> Logger.chain("Block came before the proof")
            else -> Logger.chain("Block ${blockProducer?.substring(0..60) ?: "Unknown"} is not the lottery winner for block $blockHeight")
        }
        return false
    } ?: false
    */

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
        } else Logger.error("Vdf proof is invalid for. Check BlockChain.kt @isProofValid...")
        return false

    }
}

private data class ExpectedBlock(val previousBlockHash: String, var vdfProof: String? = null, var blockProducer: String? = null, val height: Int)