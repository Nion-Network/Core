package common

import org.apache.commons.codec.digest.DigestUtils

data class BlockData(
        var previous_hash: String? = null,
        var height: Int = 0,
        var ticket: Int = 0,
        var difficulty: Int = 0,
        var vdfProof: String? = null,
        var blockProducer: String? = null,
        var timestamp: Long? = 0,
        var consensusNodes: List<String> = emptyList(),
        //no idea if this works every time
        var hash: String = DigestUtils.sha256Hex(previous_hash + height + ticket + difficulty + vdfProof + blockProducer + timestamp + consensusNodes)
) {
    companion object {
        //TODO: companion object
        fun genesisBlock(block_producer: String, difficulty: Int): BlockData = BlockData(
                blockProducer = block_producer,
                difficulty = difficulty,
                consensusNodes = mutableListOf(block_producer)
        )

        fun forgeNewBlock(previous_block: BlockData, vdfProof: String, publicKey: String, inclusionRequests: List<String>): BlockData = BlockData(
                vdfProof = vdfProof,
                height = previous_block.height + 1,
                difficulty = previous_block.difficulty,//TODO: Difficulty adjustment algorithm
                blockProducer = publicKey,
                previous_hash = previous_block.hash,
                consensusNodes = (previous_block.consensusNodes.plus(inclusionRequests).plus(publicKey)).distinct()
        )
    }
}


