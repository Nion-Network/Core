package kademlia

import data.network.Node
import kotlinx.serialization.Serializable

/**
 * Created by mihael
 * on 10/12/2021 at 20:27
 * using IntelliJ IDEA
 */
@Serializable
class KademliaMessage(val sender: Node, val endpoint: KademliaEndpoint, val data: ByteArray)