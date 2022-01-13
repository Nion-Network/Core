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
    val endpoint: Endpoint,
    val transmissionType: TransmissionType,
    val messageUID: ByteArray,
    val message: ByteArray,
    val recipient: Node,
    val packetId: ByteArray? = null
)