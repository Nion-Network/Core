package network.kademlia

import network.data.Node
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by mihael
 * on 10/12/2021 at 20:26
 * using IntelliJ IDEA
 */
class KademliaQuery(val start: Long = System.currentTimeMillis(), var hops: Int, val queue: LinkedBlockingQueue<(Node) -> Unit> = LinkedBlockingQueue())