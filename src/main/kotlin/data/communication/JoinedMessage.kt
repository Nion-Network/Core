package data.communication

import data.network.Node
import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:44
 * using IntelliJ IDEA
 */
@Serializable
class JoinedMessage(val acceptor: Node, val knownNodes: Array<Node>)