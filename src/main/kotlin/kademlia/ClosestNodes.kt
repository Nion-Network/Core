package kademlia

import data.network.Node
import kotlinx.serialization.Serializable

/**
 * Created by mihael
 * on 10/12/2021 at 20:29
 * using IntelliJ IDEA
 */
@Serializable
class ClosestNodes(val lookingFor: String, val nodes: Array<Node>)