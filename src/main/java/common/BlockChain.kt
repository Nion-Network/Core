package common

import abstraction.ProtocolTasks
import configuration.Configuration
import logging.Logger
import logging.Logger.chain
import logging.Logger.consensus
import logging.Logger.debug
import network.NetworkManager
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import utils.VDF
import java.util.*
import java.util.function.ToIntFunction
import kotlin.concurrent.schedule

class BlockChain(private var crypto: Crypto, private var vdf: VDF, private val configuration: Configuration) {
    private var pendingInclusionRequests: Set<String> = mutableSetOf<String>()
    var chain: MutableList<BlockData> = mutableListOf<BlockData>()
    private lateinit var networkManager: NetworkManager
    private var pending_inclusion_requests: MutableList<String> = mutableListOf<String>()
    val lastBlock: BlockData? get() = chain.lastOrNull()
    private var validator: Boolean = false;
    private var timer: Timer = Timer();
    private var synced: Boolean = false
    private var winner: Pair<String, Int> = Pair("", 0) //winners public key and block height

    /*
        @Synchronized
        fun addBlock(blockData: BlockData) {
            if (chain.isEmpty()) {
                if(blockData.height == 0) {
                    chain.add(blockData)
                    chain("Genesis block added")
                }else{
                    chain("Node fell out of sync 1.")
                    networkManager.initiate(ProtocolTasks.requestBlocks, 0)
                }
            } else if ((chain.lastOrNull()?.height ?:0) + 1 == blockData.height) { //is successor block
                if (chain.last().hash.equals(blockData.previous_hash)) { //is on the right chain
                    if (updateVdf(blockData.vdf_proof!! , blockData.height)) { //is proof valid
                        if (blockData.blockProducer.equals(lotteryResults(blockData.vdf_proof?: "", blockData.consensusNodes as MutableList<String>).get(0))) {
                            chain.add(blockData)
                            if(!validator && blockData.consensusNodes.contains(crypto.publicKey)){
                                Logger.consensus("Accepted to the validator set")
                                validator=!validator;
                            }
                            if(validator) vdf.runVDF(blockData.difficulty, blockData.hash, blockData.height + 1)
                            chain("Block ${blockData.hash} added at height ${blockData.height}")
                        } else {
                            chain("Block ${blockData.hash} at height ${blockData.height} is not the lottery winner!")
                        }
                    } else {
                        chain("Block ${blockData.hash} added at height ${blockData.height} invalid VDF proof")
                    }
                } else {
                    chain("Hash miss-match at block ${blockData.height} ")
                }
            } else if((chain.lastOrNull()?.hash).equals(blockData.hash)) {
                Logger.trace("Block ${blockData.height} : ${blockData.hash} already received")
            }else{
                Logger.debug("Not succesor ${chain.lastOrNull()?.height?:0}  : ${blockData.height}")
                if (chain.lastOrNull()?.height?:0 > blockData.height) { //we're out of syncF
                    chain("Node fell out of sync 2.")
                    networkManager.initiate(ProtocolTasks.requestBlocks, 0)
                } else if (chain.lastOrNull()?.height?:0 > blockData.height){
                    Logger.debug("Old block?  ${blockData.height} : ${blockData.hash}")
                }else{
                    Logger.chain("Hash miss-match on candidate block: ${blockData.hash}")
                }
            }
        }
        */
    @Synchronized
    fun addBlock(blockData: BlockData) : Boolean{
        if (chain.isEmpty() && blockData.height == 0) {//genesis blocks does not require verification
            chain.add(blockData)
            winner = Pair(blockData.blockProducer ?: crypto.publicKey, blockData.height + 1)
            chain("Added genesis block")
        } else if (!synced) {
            networkManager.initiate(ProtocolTasks.requestBlocks, chain.size)
        } //sync chain
        else if (chain.last().hash.equals(blockData.previous_hash)) { //is on the right chain
            if (winner.first.equals(blockData.blockProducer) && winner.second == blockData.height) { //expected winner
                if (vdf.verifyProof(chain.last().difficulty, chain.last().hash, blockData.vdf_proof)) { //is proof valid
                    chain.add(blockData)
                    this.validator = blockData.consensusNodes.contains(crypto.publicKey)
                    printChain()
                    chain("Block ${blockData.hash} added at height ${blockData.height}")
                    if(validator) {
                        vdf.runVDF(blockData.difficulty, blockData.hash, blockData.height + 1)
                        consensus("We're validators. Running VDF for ${blockData.height + 1}")
                    }
                    return true;
                } else {
                    chain("Block ${blockData.hash} added at height ${blockData.height} invalid VDF proof")
                }
            } else {
                debug("Not expected block ${chain.last().hash ?: ""} height: ${chain.lastOrNull()?.height ?: 0}  : ${blockData.height} | block producer ${DigestUtils.sha256Hex(blockData.blockProducer)}")
            }
        } else {
            chain("Hash miss-match at block ${blockData.height} ")
        }
        return false;
    }

    @Synchronized
    fun updateVdf(vdfProof: String, height: Int): Boolean {
        val isInside = chain.size > height
        if (!isInside && synced) {
            val proofValid = chain.last().consensusNodes.contains(crypto.publicKey) && vdf.verifyProof(chain.last().difficulty, chain.last().hash, vdfProof)
            if (proofValid) {
                consensus("Proof is valid")
                val lotteryResults = lotteryResults(vdfProof, chain.last().consensusNodes)
                val myTurn = lotteryResults.indexOf(crypto.publicKey)
                var delta: Long = myTurn * 5000L;
                Logger.info("Scheduling block creation for $myTurn epoch in $delta ms")
                winner = Pair(lotteryResults.first(), height);
                chain("Setting winner ${DigestUtils.sha256Hex(winner.first)} for height: ${winner.second + 1}")
                if(validator) {
                    timer.cancel()
                    timer = Timer()
                    timer.schedule(delta) {
                        Logger.consensus("New block forged at height $height in $myTurn epoch")
                        var newBlock: BlockData = BlockData.forgeNewBlock(chain.last(), vdfProof, crypto.publicKey, getPending_inclusion_requests())
                        if(addBlock(newBlock)) {
                            networkManager.initiate(ProtocolTasks.newBlock, newBlock)
                        }
                    }
                }
                return true;
            } else {
                debug("Proof: ${DigestUtils.sha256Hex(vdfProof)} for block $height is invalid")
            }
        } else {
            debug("Proof: ${DigestUtils.sha256Hex(vdfProof)} for block $height is old")
        }
        return false;
    }

    fun lotteryResults(vdf_proof: String, candidates: List<String>): List<String> {
        candidates.sortedWith(Comparator.comparingInt(ToIntFunction { S: String -> distance(vdf_proof, S) }))
        if (candidates.size > 0) {
            //chain("First ticket number: " + distance(vdf_proof, candidates[0]))
            //chain("Last ticket number: " + distance(vdf_proof, candidates[candidates.size - 1]))
        }
        return candidates
    }

    fun distance(proof: String, node_id: String): Int {
        var node_id = node_id
        node_id = DigestUtils.sha256Hex(node_id)
        val seed = java.lang.Long.parseUnsignedLong(proof.substring(0, 16), 16)
        val random = Random(seed)
        val draw = random.nextDouble()
        val front = Math.abs(java.lang.Long.parseUnsignedLong(node_id.substring(0, 16), 16) / Long.MAX_VALUE.toDouble())
        return (Math.abs(front - draw) * 1000000).toInt()
    }

    fun syncChain(blocks: List<BlockData>) {
        if (blocks.isEmpty()) {
            setSynced(true)
        }
        if (chain.isEmpty()) {
            chain.addAll(blocks)
        } else {
            for (b in blocks) {
                //TODO: verify proofs and rebuild state by state
                if (b.height > chain.size && chain.last().hash.equals(b.previous_hash)) {
                    chain("Chain size: " + chain.size + " Adding block " + b.height + " : " + b.hash)
                    chain.add(b)
                }
            }
        }
        setSynced(true)
    }

    fun printChain() {
        chain.forEach { Logger.chain("${it.height} : ${it.hash} | ${DigestUtils.sha256Hex(it.blockProducer)}") }
    }

    fun injectDependency(networkManager: NetworkManager?) {
        this.networkManager = networkManager!!
    }

    fun addInclusionRequest(publicKey: String?) {
        this.pending_inclusion_requests.add(publicKey ?: "")
    }

    fun getPending_inclusion_requests(): MutableList<String> {
        return pending_inclusion_requests
    }

    fun setSynced(synced: Boolean) {
        this.synced = synced;
        debug("Chain synced $synced")
    }
    fun setValidator(isValidator: Boolean){
        this.validator = isValidator
    }
}
