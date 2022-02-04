import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import utils.CircularList

/**
 * Created by mihael
 * on 10/01/2022 at 14:55
 * using IntelliJ IDEA
 */
class CircularListTest {

    @Test
    fun circularTest() {
        val circularList = CircularList<Int>(3)
        for (i in 0..10) circularList.add(i)
        assertEquals(circularList.elements(), listOf(8, 9, 10))
    }
}