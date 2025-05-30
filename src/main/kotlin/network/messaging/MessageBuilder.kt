package network.messaging

/**
 * Created by Mihael Valentin Berčič
 * on 03/11/2021 at 18:54
 * using IntelliJ IDEA
 */
class MessageBuilder(numberOfPackets: Int) {

    private var totalAdded = 0
    private val array = arrayOfNulls<ByteArray>(numberOfPackets)
    val isReady get() = array.none { it == null }

    /**
     * Adds bytes to the buffer, which can later be combined to form a complete message.
     *
     * This function appends raw byte data to an internal buffer. The accumulated bytes
     * can be "glued" together and parsed as a single message at a later stage.
     */
    fun addPart(position: Int, data: ByteArray): Boolean {
        array[position] = data
        totalAdded++
        return array.none { it == null }
    }


    /**
     * Folds or "glues" the buffered bytes together to form a single, larger byte array (message).
     *
     * This function combines the accumulated byte fragments into one contiguous byte array,
     * representing a complete message ready for processing or parsing.
     */
    fun gluedData(): ByteArray {
        return array.requireNoNulls().fold(byteArrayOf()) { a, b -> a + b }
    }

}