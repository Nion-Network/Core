package kademlia

import data.network.Node
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Created by mihael
 * on 06/12/2021 at 11:54
 * using IntelliJ IDEA
 */
class Bucket(private val bucketCapacity: Int) {

    // TODO: Implement pinging, removing, replacing stale nodes.

    private val set = mutableSetOf<Node>()
    private val lock = ReentrantLock(true)

    val size get() = set.size

    /** Performs a check whether the [node] is in the bucket or not. */
    fun contains(node: Node) = lock.withLock { set.contains(node) }

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
    fun getNodes() = lock.withLock { set.toSet() }

}