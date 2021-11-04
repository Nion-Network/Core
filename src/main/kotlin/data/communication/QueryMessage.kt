package data.communication

import data.network.Node
import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 18/04/2020 at 16:33
 * using IntelliJ IDEA
 */
@Serializable
class QueryMessage(val seeker: Node, val publicKeys: Array<out String>)