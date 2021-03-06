import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utils.sha256
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Created by mihael
 * on 17/12/2021 at 11:56
 * using IntelliJ IDEA
 */
class ValidatorTest {

    @Test
    internal fun validatorHash() {
        val seed = 9123912309213021319
        val setA = sha256(mutableSetOf("A", "B").sorted().shuffled(Random(seed)).apply { println(this) }.joinToString(""))
        val setB = sha256(mutableSetOf("B", "A").sorted().shuffled(Random(seed)).apply { println(this) }.joinToString(""))
        assertEquals(true, setA.contentEquals(setB))
    }

    @Test
    internal fun binaryParentTest() {
        val tree = (0..10).toList()
    }
}