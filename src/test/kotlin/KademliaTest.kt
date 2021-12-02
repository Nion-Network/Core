import kademlia.Kademlia
import kademlia.KademliaNode
import kademlia.asBitSet
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256

/**
 * Created by mihael
 * on 02/12/2021 at 22:02
 * using IntelliJ IDEA
 */
class KademliaTest {

    private val kademlia = Kademlia(sha256("Hello").asHex, "HI", 69)
    private val nodes = (0..30).map { KademliaNode(sha256("$it").asHex, "$it", it) }

    @Test
    internal fun search() {
        nodes.forEach(kademlia.tree::add)
        val sha = sha256("3").asHex
        val found = kademlia.tree.find(sha.asBitSet)
        println(kademlia.tree)
        println(found)
        println(sha)
        assertTrue(found.any { it.identifier == sha })
    }


}