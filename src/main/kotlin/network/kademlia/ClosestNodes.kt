package network.kademlia

import kotlinx.serialization.Serializable
import network.data.Node

/**
 * Created by mihael
 * on 10/12/2021 at 20:29
 * using IntelliJ IDEA
 *
 * Utility class for holding the closest nodes relative to a given identifier in the Kademlia network.
 *
 * This class maintains a sorted collection of nodes based on their XOR distance to a target ID.
 * It is commonly used during node lookups or value searches to track the closest known peers
 * as defined by the Kademlia distance metric.
 */
@Serializable
class ClosestNodes(val identifier: String, val nodes: Array<Node>)