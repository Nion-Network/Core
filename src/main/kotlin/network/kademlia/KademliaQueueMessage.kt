package network.kademlia

/**
 * Created by mihael
 * on 10/12/2021 at 20:27
 * using IntelliJ IDEA
 */
class KademliaQueueMessage(val endpoint: KademliaEndpoint, val ip: String, val port: Int, val data: ByteArray)