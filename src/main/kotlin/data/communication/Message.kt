package data.communication

import data.network.Endpoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import utils.Utils
import utils.Utils.Companion.asHex
import java.util.*

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:43
 * using IntelliJ IDEA
 * Encapsulation of data that is sent to the client. The data will be verified via the signature and public key upon arrival. */
@Serializable
class Message(
    val endpoint: Endpoint,
    val publicKey: String,
    val body: ByteArray,
    val signature: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    val uid: String = Utils.sha256(UUID.randomUUID().toString()).asHex
) {
    inline fun <reified T> decodeAs() = ProtoBuf.decodeFromByteArray<T>(body)
}