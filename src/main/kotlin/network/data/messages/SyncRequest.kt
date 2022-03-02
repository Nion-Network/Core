package network.data.messages

import kotlinx.serialization.Serializable
import network.data.Node

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:45
 * using IntelliJ IDEA
 */
@Serializable
data class SyncRequest(val node: Node, val fromSlot: Long)