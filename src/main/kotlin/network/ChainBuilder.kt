package network

import Nion
import data.chain.Block
import data.communication.InclusionRequest
import data.communication.Message
import data.network.Endpoint
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Created by Mihael Valentin Berčič
 * on 06/11/2021 at 13:50
 * using IntelliJ IDEA
 */
class ChainBuilder(private val nion: Nion) {

    private val validatorSet = ValidatorSet()
    private val verifiableDelay = VerifiableDelay()
    private val chain = Chain(verifiableDelay, nion.configuration.initialDifficulty)


    @MessageEndpoint(Endpoint.InclusionRequest)
    private fun inclusionRequested(message: Message) {
        val inclusionRequest = message.decodeAs<InclusionRequest>()

    }

}

class ValidatorSet : LinkedHashSet<String>() {

    private val lock = ReentrantLock()

    fun inclusionChange(publicKey: String, isAdded: Boolean) {
        lock.withLock {
            if (isAdded) add(publicKey)
            else remove(publicKey)
        }
    }

}

class Chain(private val verifiableDelay: VerifiableDelay, private val initialDifficulty: Int) {

    private val lock = ReentrantLock()
    private val blocks = mutableListOf<Block>()

    fun isSynced(slot: Long): Boolean {
        return lock.withLock {
            val lastSlot = blocks.lastOrNull()?.slot ?: 0
            lastSlot == slot
        }
    }


    fun addBlocks(vararg newBlocks: Block): Boolean {
        lock.withLock {
            newBlocks.forEach { newBlock ->
                val lastBlock = newBlocks.lastOrNull()
                val lastHash = lastBlock?.hash ?: "FFFF"
                val difficulty = lastBlock?.difficulty ?: initialDifficulty
                val isLegitimate = verifiableDelay.verifyProof(lastHash, difficulty, newBlock.vdfProof)
                if (isLegitimate) blocks.add(newBlock)
                else return false
            }
        }
        return true
    }

}