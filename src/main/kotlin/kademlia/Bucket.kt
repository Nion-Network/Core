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
    private val lock = ReentrantLock(false)

    val size get() = set.size

    fun contains(node: Node) = lock.withLock { set.contains(node) }

    fun add(node: Node) {
        if (size >= bucketCapacity && !contains(node)) return
        lock.withLock {
            set.remove(node)
            set.add(node)
        }
    }

    fun getNode(identifier: String): Node? = lock.withLock { set.firstOrNull { it.identifier == identifier } }

    fun getNodes() = lock.withLock { set.toSet() }

}