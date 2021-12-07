import data.Configuration
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logging.Dashboard
import logging.Logger
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256
import utils.runAfter
import utils.tryAndReport
import java.io.File
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 06/11/2021 at 19:43
 * using IntelliJ IDEA
 */
fun main(args: Array<String>) {
    System.setProperty("kotlinx.coroutines.scheduler", "off")
    val configuration = Json.decodeFromString<Configuration>(File("./config.json").readText())
    Dashboard.toString()
    Logger.toggleLogging(configuration.loggingEnabled)
    tryAndReport {
        Nion(configuration).apply {

            if (!isTrustedNode) kademlia.bootstrap(configuration.trustedNodeIP, configuration.trustedNodePort)
            launch()


            fun test() {
                runAfter(Random.nextLong(5000, 15000)) {
                    tryAndReport {
                        val randomKey = "${Random.nextInt(2, 49)}"
                        Logger.debug("Running TEST and querying for ${sha256(randomKey).asHex}")
                        val start = System.currentTimeMillis()
                        queryFor(randomKey) {
                            val duration = System.currentTimeMillis() - start
                            Logger.debug("Non-Kademlia for ${it.identifier} took ${duration}ms")
                            Dashboard.reportDHTQuery(it.identifier, -1, duration)
                        }
                        kademlia.query(sha256(randomKey).asHex)
                        test()
                        kademlia.printTree()
                    }
                }
            }

            // runAfter(120000) { test() }
        }
    }
}