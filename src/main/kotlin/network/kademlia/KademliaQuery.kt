package network.kademlia

import network.data.Node
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by mihael
 * on 10/12/2021 at 20:26
 * using IntelliJ IDEA
 *
 * Represents a Kademlia network query used to locate nodes or retrieve values from the DHT.
 *
 * A network query traverses the Kademlia network by iteratively contacting the closest known nodes
 * to the target identifier. It updates its shortlist of candidates based on responses until
 * either the target is found or no closer nodes are discovered.
 */
class KademliaQuery(
    val identifier: String,
    var hops: Int = 0,
    var revives: Int = 0,
    var lastUpdate: Long = System.currentTimeMillis(),
    var start: Long = System.currentTimeMillis(),
    val queue: LinkedBlockingQueue<(Node) -> Unit> = LinkedBlockingQueue()
)