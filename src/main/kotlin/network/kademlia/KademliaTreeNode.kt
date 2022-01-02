package network.kademlia

import logging.Logger
import network.data.Node
import java.util.*
import kotlin.random.Random

/**
 * Created by mihael
 * on 01/12/2021 at 13:06
 * using IntelliJ IDEA
 */
class KademliaTreeNode(val depth: Int) {

    private val identifier = Random.nextInt().toString(16)

    private val nodes = mutableSetOf<Node>()
    private val maxNodes = 5
    private var left: KademliaTreeNode? = null
    private var right: KademliaTreeNode? = null
    private var neighbour: KademliaTreeNode? = null

    fun add(node: Node) {
        val tree = if (node.bitSet.get(depth)) right else left
        if (tree == null) {
            Logger.debug("Adding ${node.identifier}.")
            nodes.add(node)
            if (nodes.size >= maxNodes) spill()
        } else tree.add(node)
    }

    fun find(identifier: BitSet): List<Node> {
        val tree = if (identifier.get(depth)) right else left
        Logger.info("Attempting to find it in the ${tree?.identifier}.")
        val closestNodes = tree?.find(identifier) ?: nodes.toList()
        Logger.info("Attempting to find it. Closest nodes: ${closestNodes.size} ... ")
        return closestNodes
    }

    private fun spill() {
        Logger.debug("Spilling!")
        left = KademliaTreeNode(depth + 1)
        right = KademliaTreeNode(depth + 1)
        left?.neighbour = right
        right?.neighbour = left

        nodes.forEach { node ->
            val bitset = BitSet.valueOf(node.identifier.toBigInteger(16).toByteArray())
            val tree = if (bitset.get(depth)) right else left
            tree?.add(node)
        }
        nodes.clear()
    }


    override fun toString(): String = StringBuilder().apply {
        if (left != null) append("\"$identifier\" -> \"${left!!.identifier}\" [label=\"0\"]\n$left")
        if (right != null) append("\"$identifier\" -> \"${right!!.identifier}\" [label=\"1\"]\n$right")
        if (left == null && right == null) append("\"$identifier\" [label=\"${nodes.map { it.identifier.substring(0..5) }}\"]\n")
    }.toString()
}