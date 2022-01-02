package network.kademlia

/**
 * Created by mihael
 * on 10/12/2021 at 20:27
 * using IntelliJ IDEA
 */
enum class KademliaEndpoint {
    PING,
    CLOSEST_NODES,
    FIND_NODE;

    companion object {
        private val cache = values().associateBy { it.ordinal }
        operator fun get(id: Int) = cache[id]
    }
}