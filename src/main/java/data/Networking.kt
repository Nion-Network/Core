package data

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:11
 * using IntelliJ IDEA
 */

data class Configuration(
    val bootstrapNode: String,
    val trustedNodeIP: String,
    val trustedNodePort: Int,
    val maxNodes: Int,
    val keystorePath: String,
    val slotDuration: Long,
    val broadcastSpreadPercentage: Int,
    val initialDifficulty: Int,
    val validatorsCount: Int,
    val committeeSize: Int,
    val slotCount: Int,
    val influxUrl: String,
    val influxUsername: String,
    val influxPassword: String,
    val dashboardEnabled: Boolean,
    val loggingEnabled: Boolean,
    val historyMinuteClearance: Int,
    val historyCleaningFrequency: Int,
    val mysqlUser: String,
    val mysqlPassword: String,
    val clusterCount: Int,
    val maxIterations: Int,
    val packetSplitSize: Int,
    val useCriu: Boolean
) {
    val trustedHttpAddress: String get() = "http://$trustedNodeIP:$trustedNodePort"
}

/**
 * Stores information of some Node in the network.
 *
 * @property publicKey
 * @property ip
 * @property port
 * @property returnAddress String representing URL to access the Node.
 */
data class Node(val publicKey: String, val ip: String, val port: Int) {

}