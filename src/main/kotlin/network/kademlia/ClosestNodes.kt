package network.kademlia

import kotlinx.serialization.Serializable
import network.data.Node

/**
 * Created by mihael
 * on 10/12/2021 at 20:29
 * using IntelliJ IDEA
 */
@Serializable
class ClosestNodes(val identifier: String, val nodes: Array<Node>)