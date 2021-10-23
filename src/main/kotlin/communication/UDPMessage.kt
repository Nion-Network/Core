package communication

import data.network.Endpoint
import data.network.Node

/**
 * Created by Mihael Valentin Berčič
 * on 21/08/2021 at 15:18
 * using IntelliJ IDEA
 *
 * Holds information for the UDP message that is either being sent or received.
 */
class UDPMessage(
    val endpoint: Endpoint,
    val messageId: String,
    val messageData: ByteArray,
    val recipients: Array<out Node>,
    val isBroadcast: Boolean
)