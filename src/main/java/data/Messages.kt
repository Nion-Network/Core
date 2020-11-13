package data

import org.apache.commons.codec.digest.DigestUtils
import utils.Utils

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 16:33
 * using IntelliJ IDEA
 */

data class FoundMessage(val foundIp: String, val foundPort: Int, val forPublicKey: String)

data class QueryMessage(val node: Node, val searchingPublicKey: String)

data class InclusionRequest(val currentEpoch: Int, val currentSlot: Int, val nodePublicKey: String)

data class QueuedMessage<T>(val hex: String, val value: Message<T>, val block: (Message<T>) -> Unit, val execute: () -> Unit = { block(value) })

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
data class Message<T>(val publicKey: String, val signature: String, val body: T, val timeStamp: Long = System.currentTimeMillis(), val hex: String = DigestUtils.sha256Hex(signature)) {
    val asJson: String get() = Utils.gson.toJson(this)
    val bodyAsString: String get() = Utils.gson.toJson(body)
}