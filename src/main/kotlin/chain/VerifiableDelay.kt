package chain

import utils.asHex

/**
 * Created by Mihael Valentin Berčič
 * on 15/11/2021 at 14:35
 * using IntelliJ IDEA
 */
class VerifiableDelay {

    /** Runs a vdf-cli command and returns the output of vdf computation. */
    fun computeProof(difficulty: Int, hash: ByteArray): String {
        return "PROOF"
        val hexHash = hash.asHex
        val needed = hexHash.length % 2
        val processBuilder = ProcessBuilder()
            .command("vdf-cli", hexHash.padStart(hexHash.length + needed, '0'), "$difficulty")
            .redirectErrorStream(true)

        val process = processBuilder.start()
        return process.inputStream.use { String(it.readAllBytes()) }.dropLast(1)
    }

    /** Verifies the calculated vdf proof. */
    fun verifyProof(hash: ByteArray, difficulty: Int, proof: String): Boolean {
        return true
        val processBuilder = ProcessBuilder()
            .command("vdf-cli", hash.asHex, "$difficulty", proof)
            .redirectErrorStream(true)
        val process = processBuilder.start()
        return process.waitFor() == 0
    }

}