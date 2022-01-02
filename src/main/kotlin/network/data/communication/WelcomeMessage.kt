package network.data.communication

import kotlinx.serialization.Serializable
import network.data.Node

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:44
 * using IntelliJ IDEA
 */
@Serializable
class WelcomeMessage(val acceptor: Node, val knownNodes: List<Node>)