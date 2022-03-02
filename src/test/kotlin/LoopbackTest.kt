import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import utils.getLocalAddress

/**
 * Created by mihael
 * on 24/02/2022 at 22:51
 * using IntelliJ IDEA
 */
class LoopbackTest {

    @Test
    fun retrievedIpIsNotLoopback() {
        val currentAddress = getLocalAddress()
        println(currentAddress)
        assertTrue(!currentAddress.isLoopbackAddress)
    }


}