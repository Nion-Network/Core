package network.kademlia

import network.data.Node
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by mihael
 * on 10/12/2021 at 20:26
 * using IntelliJ IDEA
 */
class KademliaQuery(
    val identifier: String,
    var hops: Int = 0,
    var revives: Int = 0,
    var lastUpdate: Long = System.currentTimeMillis(),
    var start: Long = System.currentTimeMillis(),
    val queue: LinkedBlockingQueue<(Node) -> Unit> = LinkedBlockingQueue()
)