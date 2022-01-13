import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import logging.Logger
import network.data.Node
import network.kademlia.Kademlia
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Created by mihael
 * on 13/01/2022 at 14:15
 * using IntelliJ IDEA
 */
class KademliaTest {

    private val kademlia = Kademlia(Json.decodeFromString(File("./config.json").readText()))

    @Test
    fun lookup() {

    }

}