package network

import data.communication.TransmissionType
import data.network.Endpoint
import data.network.Node

/**
 * Created by mihael
 * on 10/12/2021 at 20:58
 * using IntelliJ IDEA
 */
class OutgoingQueuedMessage(
    val endpoint: Endpoint,
    val transmissionType: TransmissionType,
    val messageUID: ByteArray,
    val message: ByteArray,
    val recipient: Node
)