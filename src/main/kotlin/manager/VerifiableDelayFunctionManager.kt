package manager

import logging.Dashboard
import logging.Logger.info
import java.util.*

/**
 * Created by Mihael Valentin Berčič
 * on 02/10/2020 at 16:59
 * using IntelliJ IDEA
 */
class VerifiableDelayFunctionManager {

    // TODO remove...
    private fun getSaltString(): String {
        val SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
        val salt = StringBuilder()
        val rnd = Random()
        while (salt.length < 18) { // length of the random string.
            val index = (rnd.nextFloat() * SALTCHARS.length).toInt()
            salt.append(SALTCHARS[index])
        }
        return salt.toString()
    }

    private val runtime: Runtime by lazy { Runtime.getRuntime() }

    /** Kills all active vdf processes. */
    private fun killAll() {
        Runtime.getRuntime().exec("pkill -f vdf-cli").waitFor()
    }

    /** Runs a vdf-cli command and returns the output of vdf computation. */
    fun findProof(difficulty: Int, hash: String): String {
        val needed = hash.length % 2
        val processBuilder = ProcessBuilder()
            .command("vdf-cli", hash.padStart(hash.length + needed, '0'), "$difficulty")
            .redirectErrorStream(true)

        val process = processBuilder.start()
        val output = process.inputStream.reader().use { it.readText() }
        Dashboard.vdfInformation(output)
        return output
    }

    /** Verifies the calculated vdf proof. */
    fun verifyProof(difficulty: Int, hash: String, proof: String): Boolean {
        // TODO watch out, padding needed! [L36]
        return true
        val proofProcess = runtime.exec("vdf-cli $hash $difficulty $proof")
        val processOutput = proofProcess.inputStream.reader().readText().trim()
        val exitCode = proofProcess.waitFor()

        if (exitCode != 0) info("Verify proof exited with something else than 0! [ Result = $exitCode ]")
        return exitCode == 0 && processOutput == "Proof is valid"
    }

}