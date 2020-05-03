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
    public var chain: MutableList<BlockData> = mutableListOf<BlockData>()
    private lateinit var networkManager: NetworkManager
    private var pending_inclusion_requests: MutableList<String> = mutableListOf<String>()
    val lastBlock: BlockData? get() = chain.lastOrNull();
    init {
        //TODO: Timers
    }

    fun addBlock(blockData: BlockData) {
        if (chain.isEmpty() && blockData.height == 0) {
            chain.add(blockData)
            Logger.chain("Genesis block added")
        } else if (chain.last().height + 1 == blockData.height) { //is successor block
            if (chain.last().hash.equals(blockData.previous_hash)) { //is on the right chain
                if (vdf.verifyProof(blockData.difficulty ?: 0, chain.last().hash, blockData.vdf_proof)) { //is proof valid
                    if (blockData.blockProducer.equals(lotteryResults(blockData.vdf_proof ?: "", (blockData.consensusNodes ?: emptyList<String>()) as MutableList<String>).get(0))) { //lottery winner. TODO: Change to rolling epoch to guarantee liveliness.
                        chain.add(blockData);
                        Logger.chain("Block ${blockData.hash} added at height ${blockData.height}")
                    } else {
                        Logger.chain("Block ${blockData.hash} at height ${blockData.height} is not the lottery winner!")
                    }
                } else {
                    Logger.chain("Block ${blockData.hash} added at height ${blockData.height} invalid VDF proof")
                }
            } else {
                Logger.chain("Hash missmatch at block ${blockData.height} ")
            }
        } else {
            if (Math.abs(chain.last().height - blockData.height) > 1) { //we're out of syncF
                Logger.chain("Node fell out of sync 2.")
                networkManager.initiate(ProtocolTasks.requestBlocks, chain.last().height)
            } else {
                Logger.chain("Hash miss-match on candidate block: ${blockData.hash}")
            }
        }
    }

    fun lotteryResults(vdf_proof: String, candidates: MutableList<String>): MutableList<String> {
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
            addBlock(b)
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
}