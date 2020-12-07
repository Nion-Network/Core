package data

import utils.Utils

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
        val broadcastSpread: Int,
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
        val historyCleaningFrequency: Int
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
data class Node(val publicKey: String, val ip: String, val port: Int, val returnAddress: String = "http://$ip:$port") {

    /**
     * Sends the given message to current node.
     *
     * @param T type of the message body.
     * @param path http(s) networking path to deliver the message to.
     * @param message message to be sent to the node.
     * @return Response code and response
     */
    private fun <T> sendMessage(path: String, message: Message<T>): Pair<Int, String> = Utils.sendMessageTo("http://$ip:$port", path, message)

    fun <T> sendMessage(endPoint: EndPoint, message: Message<T>): Pair<Int, String> = sendMessage(endPoint.path, message)
}