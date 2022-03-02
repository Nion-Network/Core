package network.data.messages

import Configuration
import network.data.Endpoint
import network.data.Node

/**
 * Created by Mihael Valentin Berčič
 * on 21/08/2021 at 15:20
 * using IntelliJ IDEA
 *
 * Packet builder used for building one big message from small partitions (packets).
 */
data class PacketBuilder(
    val knownNodes: Collection<Node>,
    val configuration: Configuration,
    val messageIdentification: String,
    val endpoint: Endpoint,
    val arraySize: Int
) {

    val createdAt: Long = System.currentTimeMillis()
    val isReady get() = data.none { it == null }

    val data = arrayOfNulls<ByteArray>(arraySize)
    val recipients = knownNodes.shuffled().let {
        val toTake = 5 + (configuration.broadcastSpreadPercentage * Integer.max(it.size, 1) / 100)
        it.take(toTake)
    }

    fun addData(index: Int, dataToAdd: ByteArray) {
        data[index] = dataToAdd
    }

    // Note: Use carefully!
    val asOne get() = data.fold(ByteArray(0)) { a, b -> a + b!! }

}