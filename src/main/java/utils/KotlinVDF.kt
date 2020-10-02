package utils

import logging.Logger.debug
import logging.Logger.error
import logging.Logger.info

/**
 * Created by Mihael Valentin Berčič
 * on 02/10/2020 at 16:59
 * using IntelliJ IDEA
 */
class KotlinVDF {

    private val runtime: Runtime by lazy { Runtime.getRuntime() }


    private fun killAll() = Runtime.getRuntime().exec("ps -ef | grep vdf-cli | grep -v \"grep\" | awk '{print $2}' | xargs kill; ").waitFor()

    fun findProof(difficulty: Int, hash: String, epoch: Int): String {
        debug("VDF HASH: $hash for epoch: $epoch")
        killAll()
        val vdfProcess = runtime.exec("vdf-cli $hash $difficulty -u http://localhost:3000/fuck -b $epoch").apply {
            waitFor()
        }.inputStream.reader().readText()
        error("VDF proof: $vdfProcess")
        return vdfProcess
    }

    fun verifyProof(difficulty: Int, hash: String, proof: String): Boolean {
        debug("Verifying proof: Hash:$hash")
        val proofProcess = runtime.exec("vdf-cli $hash $difficulty $proof")
        val processOutput = proofProcess.inputStream.reader().readText()
        val exitCode = proofProcess.waitFor()

        if (exitCode != 0) info("Verify proof exited with something else than 0! [ Result = $exitCode ]")
        return exitCode == 0 && processOutput == "Proof is valid"
    }

}