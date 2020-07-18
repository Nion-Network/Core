package common

import abstraction.ProtocolTasks
import configuration.Configuration
import logging.Logger
import network.NetworkManager
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import utils.VDF
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.abs

class BlockChain(private var crypto: Crypto, private var vdf: VDF, private val configuration: Configuration) {

    lateinit var networkManager: NetworkManager
    private var expectedBlock: ExpectedBlock? = null

    val chain = mutableListOf<BlockData>()
    var validator: Boolean = false
    var synced: Boolean = false

    val pendingInclusionRequests = mutableListOf<String>()
    val lastBlock: BlockData? get() = chain.lastOrNull()

    private var timer: Timer = Timer()


    fun addBlock(blockData: BlockData): Boolean {
        val height = blockData.height
        val hash = blockData.hash

        Logger.chain("Adding block $height...")
        Logger.debug("Running " + Thread.activeCount() + " threads...");
        printChain()

        when {
            chain.isEmpty() && height == 0 -> {
                chain.add(blockData)
                expectedBlock = ExpectedBlock(hash, height = 1)
                Logger.chain("Added genesis block")
                return true
            }
            !synced -> {
                Logger.debug("Requesting synchronization of the chain...")
                networkManager.initiate(ProtocolTasks.requestBlocks, chain.size)
            }
            isSuccessorBlock(blockData) -> {
                if (isProofValid(blockData)) { //is proof valid
                    chain.add(blockData)
                    expectedBlock = ExpectedBlock(hash, height = blockData.height + 1)
                    Logger.chain("Block $hash added at height $height")
                    if (validator) {
                        vdf.runVDF(blockData.difficulty, hash, (height + 1))
                        Logger.consensus("We're validators. Running VDF for ${(blockData.height + 1)}")
                    } else Logger.consensus("We're not a validator node, skipping block creation")
                    return true;
                } else Logger.consensus("Proof validation failed")
            }
            else -> Logger.chain("Block validation failed")
        }

        validator = blockData.consensusNodes.contains(crypto.publicKey)
        Logger.debug("Is validator $validator")
        return false;
    }

    fun updateVdf(vdfProof: String, height: Int): Boolean {
        expectedBlock?.also { block ->
            val blockHeight = block.height
            val blockProof = block.vdfProof

            if (height == blockHeight) {
                when (blockProof) {
                    null -> {
                        chain.lastOrNull()?.also { lastBlock ->
                            val lastDifficulty = lastBlock.difficulty
                            val lastHash = lastBlock.hash
                            val lastConsensusNodes = lastBlock.consensusNodes
                            val lotteryResults = lotteryResults(vdfProof, lastConsensusNodes)

                            if (vdf.verifyProof(lastDifficulty, lastHash, vdfProof)) {
                                Logger.chain("Proof is valid")
                                this.expectedBlock?.blockProducer = lotteryResults.firstOrNull()
                                this.expectedBlock?.vdfProof = vdfProof

                                if (validator) {
                                    val myTurn = lotteryResults.indexOf(crypto.publicKey)
                                    val delta: Long = myTurn * 5000L

                                    Logger.debug("Scheduling block creation in $delta")
                                    timer.cancel()
                                    timer = Timer()
                                    timer.schedule(delta) {
                                        Logger.consensus("New block forged at height $height in $myTurn epoch")
                                        val newBlock: BlockData = BlockData.forgeNewBlock(chain.last(), vdfProof, crypto.publicKey, pendingInclusionRequests)
                                        if (addBlock(newBlock)) networkManager.initiate(ProtocolTasks.newBlock, newBlock)
                                    }
                                    Logger.consensus("Scheduled block creation in $delta ms as $myTurn best lottery drawn")
                                } else Logger.consensus("We're not in the validator set for block $height")

                            }

                        }
                        return true;
                    }
                    vdfProof -> Logger.chain("Proof already known")
                    else -> Logger.chain("Proof different then ours, something wrong!")
                }
            } else Logger.debug("Proof: ${DigestUtils.sha256Hex(vdfProof)} for block $height is old")
        }
        return false;
    }

    fun syncChain(blocks: List<BlockData>) {
        if (blocks.isEmpty()) synced = true
        if (chain.isEmpty()) {
            chain.addAll(blocks)
        } else {
            for (b in blocks) {
                //TODO: verify proofs and rebuild state by state
                if (b.height > chain.size && chain.last().hash.equals(b.previous_hash)) {
                    Logger.chain("Chain size: " + chain.size + " Adding block " + b.height + " : " + b.hash)
                    chain.add(b)
                }
            }
        }
        synced = true
    }

    private fun printChain() = chain.forEach { Logger.chain("${it.height} : ${it.hash} | ${DigestUtils.sha256Hex(it.blockProducer)}") }

    private fun lotteryResults(vdfProof: String, candidates: List<String>): List<String> {

        // TODO povej mihatu kva je to...
        //  candidates.filterNotNull().sortedWith(Comparator.comparingInt({ S: String -> distance(vdf_proof, S) }))
        if (candidates.size > 0) {
            //chain("First ticket number: " + distance(vdf_proof, candidates[0]))
            //chain("Last ticket number: " + distance(vdf_proof, candidates[candidates.size - 1]))
        }
        return candidates.sortedWith(Comparator.comparingInt({ S: String -> distance(vdfProof, S) }))

    }

    private fun distance(proof: String, node_id: String): Int {
        val nodeId = DigestUtils.sha256Hex(node_id)
        val seed = java.lang.Long.parseUnsignedLong(proof.substring(0, 16), 16)
        val random = Random(seed)
        val draw = random.nextDouble()
        val front = abs(java.lang.Long.parseUnsignedLong(nodeId.substring(0, 16), 16) / Long.MAX_VALUE.toDouble())
        return (abs(front - draw) * 100000).toInt()
    }

    private fun isSuccessorBlock(blockData: BlockData): Boolean = expectedBlock?.let { expectedBlock ->
        val expectedHeight = expectedBlock.height
        val expectedBlockProducer = expectedBlock.blockProducer
        val blockProducer = blockData.blockProducer
        val blockHeight = blockData.height

        if (expectedHeight != blockHeight) Logger.chain("Expecting block at height: $expectedHeight. Received $blockHeight")
        else when (expectedBlockProducer) {
            blockProducer -> {
                Logger.chain("Block validation passed")
                return true
            }
            null -> Logger.chain("Block came before the proof")
            else -> Logger.chain("Block ${blockData.blockProducer?.substring(7) ?: "Unknown"} is not the lottery winner for block $blockHeight")
        }
        return false
    } ?: false

    private fun isProofValid(blockData: BlockData): Boolean = chain.lastOrNull()?.let { lastBlock ->
        val lastDifficulty = lastBlock.difficulty
        val lastHash = lastBlock.hash
        val newProof = blockData.vdfProof
        val expectedVdfProof = this.expectedBlock?.vdfProof
        val newBlockProducer = blockData.blockProducer

        when {
            expectedVdfProof != null && expectedVdfProof == newProof -> Logger.chain("Received block's proof is as expected")
            vdf.verifyProof(lastDifficulty, lastHash, newProof) -> {
                Logger.chain("Block came before the proof but is valid")
                /*
                val blockProducer = lotteryResults(newProof ?: "", lastBlock.consensusNodes).firstOrNull()

                expectedBlock?.run {
                    this.vdfProof = newProof
                    this.blockProducer = if (blockProducer == newBlockProducer) newBlockProducer else blockProducer
                }
                 */
            }
            else -> {
                Logger.debug("vdf is invalid?")
                return false
            }
        }
        return true
    } ?: false

}

private data class ExpectedBlock(val previousBlockHash: String, var vdfProof: String? = null, var blockProducer: String? = null, val height: Int)