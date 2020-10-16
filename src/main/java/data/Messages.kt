package data

import utils.Utils

/**
 * Created by Mihael
 * on 18/04/2020 at 16:33
 * using IntelliJ IDEA
 */

data class FoundMessage(val foundIp: String, val foundPort: Int, val forPublicKey: String)

data class QueryMessage(val node: Node, val searchingPublicKey: String)

/**
 * Message with body of type T.
 *
 * Encapsulation of data that is sent to the client. The data will be verified via the signature and public key upon arrival.
 *
 * @param T Message body class type.
 * @property publicKey public key of the current node.
 * @property signature encryption signature.
 * @property body information message holds.
 * @property timeStamp timestamp when the message was generated.
 * @property asJson returns JSON of the data class.
 * @property bodyAsString returns @body as JSON.
 */
data class Message<T>(val publicKey: String, val signature: String, val body: T, val timeStamp: Long = System.currentTimeMillis()) {
    val asJson: String get() = Utils.gson.toJson(this)
    val bodyAsString: String get() = Utils.gson.toJson(body)
}