package network.abstraction

import network.data.Node

/**
 * Created by mihael
 * on 26/02/2022 at 16:38
 * using IntelliJ IDEA
 */
class OutgoingData(val recipient: Node, vararg val data: ByteArray)