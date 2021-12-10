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

    fun contains(node: Node) = lock.withLock { set.contains(node) }

    fun add(node: Node): Boolean {
        val containsNode = contains(node)
        if (size >= bucketCapacity && !containsNode) return false
        lock.withLock {
            set.remove(node)
            set.add(node)
        }
        return !containsNode
    }

    fun getNode(identifier: String): Node? = lock.withLock { set.firstOrNull { it.identifier == identifier } }

    fun getNodes() = lock.withLock { set.toSet() }

}