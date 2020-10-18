package chain

import data.Block
import manager.ApplicationManager
import org.apache.commons.codec.digest.DigestUtils

/**
 * Created by Mihael Valentin Berčič
 * on 24/09/2020 at 14:08
 * using IntelliJ IDEA
 */

class BlockProducer(private val applicationManager: ApplicationManager) {

    private val currentState = applicationManager.currentState
    private val initialDifficulty = applicationManager.configuration.initialDifficulty
    private val currentTime: Long get() = System.currentTimeMillis()

    fun genesisBlock(vdfProof: String): Block = Block(
            epoch = 0,
            slot = 0,
            difficulty = initialDifficulty,
            timestamp = currentTime,
            committeeIndex = 0,
            votes = 0,
            blockProducer = DigestUtils.sha256Hex(applicationManager.crypto.publicKey),
            validatorChanges = applicationManager.validatorSetChanges.toMap(),
            vdfProof = vdfProof
    )

    fun createBlock(previousBlock: Block, vdfProof: String = "", votes: Int = 0): Block = Block(
            epoch = currentState.currentEpoch,
            slot = currentState.currentSlot,
            difficulty = currentState.currentDifficulty,
            timestamp = currentTime,
            committeeIndex = currentState.committeeIndex,
            vdfProof = vdfProof,
            votes = votes,
            blockProducer = DigestUtils.sha256Hex(applicationManager.crypto.publicKey),
            validatorChanges = applicationManager.validatorSetChanges.toMap(),
            precedentHash = previousBlock.hash
    )
}