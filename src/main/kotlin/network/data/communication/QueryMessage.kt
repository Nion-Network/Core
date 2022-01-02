package network.data.communication

import kotlinx.serialization.Serializable
import network.data.Node

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 16:33
 * using IntelliJ IDEA
 */
@Serializable
data class QueryMessage(val seeker: Node, val publicKeys: Collection<String>)