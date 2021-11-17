package network

import logging.Logger

/**
 * Created by Mihael Valentin Berčič
 * on 15/11/2021 at 14:35
 * using IntelliJ IDEA
 */
class VerifiableDelay {

    /** Runs a vdf-cli command and returns the output of vdf computation. */
    fun computeProof(difficulty: Int, hash: String): String {
        val needed = hash.length % 2
        val processBuilder = ProcessBuilder()
            .command("vdf-cli", hash.padStart(hash.length + needed, '0'), "$difficulty")
            .redirectErrorStream(true)

        val process = processBuilder.start()
        return process.inputStream.use { String(it.readAllBytes()) }.dropLast(1)
    }

    /** Verifies the calculated vdf proof. */
    fun verifyProof(hash: String, difficulty: Int, proof: String): Boolean {
        // TODO watch out, padding needed! [L36]
        val processBuilder = ProcessBuilder()
            .command("vdf-cli", hash, "$difficulty", proof)
            .redirectErrorStream(true)
        val process = processBuilder.start()
        val output = process.inputStream.reader().use { it.readText() }
        Logger.info("|./vdf-cli $hash $difficulty $proof|")
        Logger.info(output)
        return process.waitFor() == 0
    }

}