package network

import network.data.Endpoint
import network.data.Node
import network.data.communication.TransmissionType

/**
 * Created by mihael
 * on 10/12/2021 at 20:58
 * using IntelliJ IDEA
 *
 * This class stores data ready to be sent through UDP socket in [Server].
 */
class OutgoingQueuedMessage(
    val transmissionType: TransmissionType,
    val message: ByteArray,
    override val endpoint: Endpoint,
    override val messageUID: ByteArray,
    override val recipient: Node
) : Outgoing

class OutgoingQueuedPacket(
    val data: ByteArray,
    override val endpoint: Endpoint,
    override val messageUID: ByteArray,
    override val recipient: Node
) : Outgoing

interface Outgoing {
    val endpoint: Endpoint
    val messageUID: ByteArray
    val recipient: Node
}
