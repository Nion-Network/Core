@file:Suppress("EnumEntryName")

package abstraction

import Main.gson
import utils.Utils

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:11
 * using IntelliJ IDEA
 */

/**
 * Message with body of type T.
 *
 * Encapsulation of data that is sent to the client. The data will be verified via the signature and public key upon arrival.
 *
 * @param T Message body class type.
 * @property publicKey public key of the current node.
 * @property signature encryption signature.
 * @property body information message holds.
 * @property asJson returns JSON of the data class.
 * @property bodyAsString returns @body as JSON.
 */
data class Message<T>(val publicKey: String, val signature: String, val body: T) {
    val asJson: String get() = gson.toJson(this)
    val bodyAsString: String get() = gson.toJson(body)
}

/**
 * Stores information of some Node in the network.
 *
 * @property publicKey
 * @property ip
 * @property port
 */
data class Node(val publicKey: String, val ip: String, val port: Int) {

    /**
     * Sends the given message to current node.
     *
     * @param T type of the message body.
     * @param path http(s) networking path to deliver the message to.
     * @param message message to be sent to the node.
     * @return Response code and response
     */
    fun <T> sendMessage(path: String, message: Message<T>): Pair<Int, String> = Utils.sendMessageTo("http://$ip:$port", path, message)
}


/**
 * Http method request types.
 *
 */
enum class NetworkRequest { GET, POST }

/**
 * Protocol types.
 *
 */
enum class ProtocolTasks { newBlock, requestBlocks, requestInclusion }