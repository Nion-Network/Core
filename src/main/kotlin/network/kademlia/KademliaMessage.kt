package network.kademlia

import kotlinx.serialization.Serializable
import network.data.Node

/**
 * Created by mihael
 * on 10/12/2021 at 20:27
 * using IntelliJ IDEA
 */
@Serializable
class KademliaMessage(val sender: Node, val endpoint: KademliaEndpoint, val data: ByteArray)