package data.communication

import kotlinx.serialization.Serializable
import utils.Utils
import utils.Utils.Companion.asHex
import java.util.*

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:43
 * using IntelliJ IDEA
 * Encapsulation of data that is sent to the client. The data will be verified via the signature and public key upon arrival. */
@Serializable
class Message<T>(
    val publicKey: String,
    val signature: ByteArray,
    val body: T,
    val timestamp: Long = System.currentTimeMillis(),
    val uid: String = Utils.sha256(UUID.randomUUID().toString()).asHex
)