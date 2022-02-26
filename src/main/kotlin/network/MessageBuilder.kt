package network

/**
 * Created by Mihael Valentin Berčič
 * on 03/11/2021 at 18:54
 * using IntelliJ IDEA
 */
class MessageBuilder(private val numberOfPackets: Int, val nodes: Array<String> = emptyArray()) {

    private var totalAdded = 0
    private val array = arrayOfNulls<ByteArray>(numberOfPackets)

    fun addPart(position: Int, data: ByteArray): Boolean {
        array[position] = data
        totalAdded++
        return array.none { it == null }
    }

    val isReady get() = array.none { it == null }

    fun gluedData(): ByteArray {
        return array.requireNoNulls().fold(byteArrayOf()) { a, b -> a + b }
    }

}