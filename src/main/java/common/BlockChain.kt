package common

import abstraction.ProtocolTasks
import configuration.Configuration
import logging.Logger
import logging.Logger.chain
import network.NetworkManager
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import utils.VDF
import java.util.*
import java.util.function.ToIntFunction

class BlockChain(private var crypto: Crypto, private var vdf: VDF, private val configuration: Configuration) {
    private var pendingInclusionRequests: Set<String> = mutableSetOf<String>()
    var chain: MutableList<BlockData> = mutableListOf<BlockData>()
    private lateinit var networkManager: NetworkManager
    private var pending_inclusion_requests: MutableList<String> = mutableListOf<String>()
    val lastBlock: BlockData? get() = chain.lastOrNull()
    private var vdf_proof: String = String()
    private var validator: Boolean =false;

    fun addBlock(blockData: BlockData) {
        if (chain.isEmpty()) {
            if(blockData.height == 0) {
                chain.add(blockData)
                Logger.chain("Genesis block added")
            }else{
                Logger.chain("Node fell out of sync 1.")
                networkManager.initiate(ProtocolTasks.requestBlocks, 0)
            }
        } else if ((chain.lastOrNull()?.height ?:0) + 1 == blockData.height) { //is successor block
            if (chain.last().hash.equals(blockData.previous_hash)) { //is on the right chain
                if (updateVdf(blockData.vdf_proof!!)) { //is proof valid
                    if (blockData.blockProducer.equals(lotteryResults(blockData.vdf_proof
                                    ?: "", blockData.consensusNodes as MutableList<String>).get(0))) { //lottery winner. TODO: Change to rolling epoch to guarantee liveliness.
                        chain.add(blockData)
                        if(!validator && blockData.consensusNodes.contains(crypto.publicKey)){
                            Logger.consensus("Accepted to the validator set")
                            validator=!validator;
                        }else{
                            Logger.consensus("Removed from the validator set")
                            validator=false;
                        }
                        Logger.chain("Block ${blockData.hash} added at height ${blockData.height}")
                    } else {
                        Logger.chain("Block ${blockData.hash} at height ${blockData.height} is not the lottery winner!")
                    }
                } else {
                    Logger.chain("Block ${blockData.hash} added at height ${blockData.height} invalid VDF proof")
                }
            } else {
                Logger.chain("Hash miss-match at block ${blockData.height} ")
            }
        } else if((chain.lastOrNull()?.hash).equals(blockData.hash)) {
            Logger.trace("Block ${blockData.height} : ${blockData.hash} already received")
        }else{
            Logger.debug("Not succesor ${chain.lastOrNull()?.height?:0}  : ${blockData.height}")
            if (chain.lastOrNull()?.height?:0 > blockData.height) { //we're out of syncF
                Logger.chain("Node fell out of sync 2.")
                networkManager.initiate(ProtocolTasks.requestBlocks, 0)
            } else if (chain.lastOrNull()?.height?:0 > blockData.height){
                Logger.debug("Old block?  ${blockData.height} : ${blockData.hash}")
            }else{
                Logger.chain("Hash miss-match on candidate block: ${blockData.hash}")
            }
        }
    }

    fun lotteryResults(vdf_proof: String, candidates: List<String>): List<String> {
        candidates.sortedWith(Comparator.comparingInt(ToIntFunction { S: String -> distance(vdf_proof, S) }))
        if (candidates.size > 0) {
            Logger.chain("First ticket number: " + distance(vdf_proof, candidates[0]))
            Logger.chain("Last ticket number: " + distance(vdf_proof, candidates[candidates.size - 1]))
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
        for (b in blocks) {
            chain("Chain size: " + chain.size + " Adding block " + b.height + " : " + b.hash)
            //TODO: verify proofs and rebuild state by state
            chain.add(b)
        }
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
    fun updateVdf(vdf_proof: String): Boolean {
        val newProof = !this.vdf_proof.equals(vdf_proof)
        if(newProof) {
            if (vdf.verifyProof(chain.last().difficulty, chain.last().hash, vdf_proof)) {
                Logger.debug("Proof is valid")
                if (chain.last().consensusNodes.contains(crypto.publicKey)) {
                    //should stop our VDF here
                    val lotteryResults = lotteryResults(vdf_proof, chain.last().consensusNodes)
                    val myTurn = lotteryResults.indexOf(crypto.publicKey)
                    if (myTurn == 0) {
                        Logger.debug("We won the lottery")
                        val previousBlock = chain.last()
                        var newBlock: BlockData = BlockData.forgeNewBlock(previousBlock, vdf_proof, crypto.publicKey, getPending_inclusion_requests())
                        Logger.info("New Block forged ${newBlock.hash}")
                        this.vdf_proof = vdf_proof
                        addBlock(newBlock)
                        networkManager.initiate(ProtocolTasks.newBlock, newBlock)
                        vdf.runVDF(newBlock.difficulty, newBlock.hash)
                    }else{
                        Logger.consensus("We're $myTurn in row as backup node")
                    }
                } else {
                    Logger.debug("We are not included in the validator set")
                }
            } else {
                Logger.debug("Proof not valid")
                return false
            }
        }else{
            Logger.consensus("Proof outdated..")
        }
        return newProof
    }
    fun printChain() {
        chain.forEach { Logger.chain("${it.height} : ${it.hash}" )}
    }
}