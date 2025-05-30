package network.kademlia

import network.data.Node
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Created by mihael
 * on 06/12/2021 at 11:54
 * using IntelliJ IDEA
 *
 * Kademlia is a distributed hash table (DHT) protocol used for decentralized peer-to-peer networks.
 *
 * It enables efficient storage and lookup of key-value pairs by organizing nodes in a XOR-based metric space.
 * Nodes maintain routing tables with information about other peers, allowing for scalable and fault-tolerant
 * message routing and data retrieval.
 *
 * Kademlia is widely used in systems like BitTorrent (DHT), IPFS, and Ethereum.
 *
 * Represents a Kademlia bucket, which holds a list of known peer contacts within a specific distance range.
 *
 * Each bucket contains nodes whose XOR distance from the local node falls within a certain range,
 * defined by the number of leading bits they share. Buckets are used to efficiently manage and
 * update peer information in the routing table, supporting quick and reliable lookups.
 *
 * According to the Kademlia protocol, each bucket holds up to 'k' nodes and follows a least-recently-seen
 * eviction policy.
 */

class Bucket(private val bucketCapacity: Int) {
    // TODO: Implement pinging, removing, replacing stale nodes.
    private val set = mutableSetOf<Node>()
    private val lock = ReentrantLock(true)
    val size get() = set.size

    /** Performs a check whether the [node] is in the bucket or not. */
    fun contains(node: Node) = set.contains(node)

    /** Adds the [node] to the bucket, or if the [node] is already in the bucket, it's moved to the tail. */
    fun add(node: Node): Boolean {
        val containsNode = contains(node)
        if (size >= bucketCapacity && !containsNode) return false
        lock.withLock {
            set.remove(node)
            set.add(node)
        }
        return !containsNode
    }

    /** Return [Node] with identifier equal to [identifier] if it exists, otherwise null. */
    fun getNode(identifier: String): Node? = lock.withLock { set.firstOrNull { it.identifier == identifier } }

    /** Retrieves all Nodes in this bucket. */
    fun getNodes(): Set<Node> = lock.withLock { set.toSet() }

}