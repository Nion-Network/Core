package data.communication

import data.network.Node
import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:45
 * using IntelliJ IDEA
 */
@Serializable
data class SyncRequest(val node: Node, val fromSlot: Long)