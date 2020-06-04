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
import kotlin.math.abs

class BlockChain(private var crypto: Crypto, private var vdf: VDF, private val configuration: Configuration) {
    var chain: MutableList<BlockData> = mutableListOf<BlockData>()
    private lateinit var networkManager: NetworkManager
    private var pending_inclusion_requests: MutableList<String> = mutableListOf<String>()
    val lastBlock: BlockData? get() = chain.lastOrNull()
    private var validator: Boolean = false;
    private var timer: Timer = Timer();
    private var synced: Boolean = false
    data class ExpectedBlock(val previous_block_hash: String, var vdf_proof: String? = null, var blockProducer: String ? = null, val height: Int)
    private lateinit var expectedBlock: ExpectedBlock


    fun addBlock(blockData: BlockData) : Boolean{
        chain("Adding block ${blockData.height}")
        Logger.debug("Running " + java.lang.Thread.activeCount() + " threads");
        if (chain.isEmpty() && blockData.height == 0) {//genesis blocks does not require verification
            chain.add(blockData)
            expectedBlock = ExpectedBlock(blockData.hash?:"",null,null,1)
            chain("Added genesis block")
            return true
        } else if (!synced) { //sync chain
            debug("Sync chain")
            networkManager.initiate(ProtocolTasks.requestBlocks, chain.size)
        }
        else if (isSuccessorBlock(blockData)) { //is successor block
            if (isProofValid(blockData)) { //is proof valid
                chain.add(blockData)
                expectedBlock = ExpectedBlock(blockData.hash?:"",null,null,blockData.height+1) //move expected block
                chain("Block ${blockData.hash} added at height ${blockData.height}")
                if (validator) {
                    vdf.runVDF(blockData.difficulty, blockData.hash, (blockData.height + 1))
                    consensus("We're validators. Running VDF for ${(blockData.height +1)}")
                }else{
                    consensus("We're not a validator node, skipping block creation")
                }
                return true;
            }else{
                consensus("Proof validation failed")
            }
        }else{
            chain("Block validation failed")
        }
        this.validator = blockData.consensusNodes.contains(crypto.publicKey)
        debug("Is validator $validator")
        return false;
    }

    fun updateVdf(vdfProof: String, height: Int): Boolean {
        if(height == expectedBlock.height){
            if(expectedBlock.vdf_proof != null){ //
                if(expectedBlock.vdf_proof.equals(vdfProof)){
                    chain("Proof already known")
                }else{
                    chain("Proof different then ours, something wrong!")
                }
            }else{
                val current:BlockData = chain.last()
                if(vdf.verifyProof(current.difficulty, current.hash, vdfProof)){
                    chain("Proof is valid")
                    expectedBlock.blockProducer = lotteryResults(vdfProof, current.consensusNodes)[0]
                    expectedBlock.vdf_proof = vdfProof
                    //should check if we are the block producer and forge the block
                    if(validator) {
                        val lotteryResults = lotteryResults(vdfProof, chain.last().consensusNodes)
                        val myTurn = lotteryResults.indexOf(crypto.publicKey)
                        var delta: Long = myTurn * 5000L;
                        debug("Sheduling block creation in $delta")
                        timer.cancel()
                        timer = Timer()
                        timer.schedule(delta) {
                            Logger.consensus("New block forged at height $height in $myTurn epoch")
                            var newBlock: BlockData = BlockData.forgeNewBlock(chain.last(), vdfProof, crypto.publicKey, getPending_inclusion_requests())
                            if(addBlock(newBlock)) {
                                networkManager.initiate(ProtocolTasks.newBlock, newBlock)
                            }
                        }
                        consensus("Scheduled block creation in $delta ms as $myTurn best lottery drawn")
                    }else{
                        consensus("We're not in the validator set for block $height")
                    }
                }else{
                    debug("Proof: ${DigestUtils.sha256Hex(vdfProof)} for block $height is invalid")
                }
                return true;
            }
        }else{
            debug("Proof: ${DigestUtils.sha256Hex(vdfProof)} for block $height is old")
        }

        return  false;
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
        val nodeId  = DigestUtils.sha256Hex(node_id)
        val seed = java.lang.Long.parseUnsignedLong(proof.substring(0, 16), 16)
        val random = Random(seed)
        val draw = random.nextDouble()
        val front = abs(java.lang.Long.parseUnsignedLong(nodeId.substring(0, 16), 16) / Long.MAX_VALUE.toDouble())
        return (abs(front - draw) * 100000).toInt()
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
    fun isSuccessorBlock(blockData: BlockData): Boolean{
        if (expectedBlock.height == blockData.height) { //is block on the expected height
            if(expectedBlock.blockProducer != null){ //do we know who the block producer is
                if(expectedBlock.blockProducer.equals(blockData.blockProducer)){ //was the block produced by the expected winner
                    return true
                    chain("Block validation passed")
                }else{
                    chain("Block ${blockData.blockProducer?:"Unknown".substring(7)} is not the lottery winner for block ${blockData.height}")
                }
            }else{
                chain("Block came before the proof")
                return false;
            }
        }else{
            chain("Expecting block at height: ${expectedBlock.height}. Received ${blockData.height}")
            return false;
        }
        return false;
    }
    fun isProofValid(blockData: BlockData): Boolean{
        val current:BlockData = chain.last()
        if(expectedBlock.vdf_proof!=null){
            if(expectedBlock.vdf_proof.equals(blockData.vdf_proof)){
                chain("Received block's proof is as expected")
                return true;
            }
        }else if(vdf.verifyProof(current.difficulty, current.hash, blockData.vdf_proof)){
            chain("Block came before the proof but is valid")
            expectedBlock.vdf_proof = blockData.vdf_proof
            val blockProducer:String = lotteryResults(blockData.vdf_proof?:"",current.consensusNodes)[0]
            expectedBlock.blockProducer = if(blockProducer == blockData.blockProducer) blockData.blockProducer else blockProducer
            return true;
        }else{
            debug("vdf is invalid?")
        }
        return false
    }

}
