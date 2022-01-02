package network.kademlia

import network.data.Node

/**
 * Created by mihael
 * on 10/12/2021 at 20:26
 * using IntelliJ IDEA
 */
data class KademliaQuery(val start: Long = System.currentTimeMillis(), var hops: Int, val action: ((Node) -> Unit)? = null)