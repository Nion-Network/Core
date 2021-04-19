package communication

import data.Configuration
import data.EndPoint
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import logging.Logger
import manager.DashboardManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer

/**
 * Created by Mihael Valentin Berčič
 * on 13/04/2021 at 00:57
 * using IntelliJ IDEA
 */
class UDPServer(configuration: Configuration, private val dashboardManager: DashboardManager, private val networkHistory: MutableMap<String, Long>, port: Int) {

    var shouldListen = true

    private val buildingPackets = hashMapOf<String, PacketBuilder>()
    private val datagramSocket = DatagramSocket(port)

    fun send(packet: DatagramPacket) {
        datagramSocket.send(packet)
    }


    fun startListening(block: (endPoint: EndPoint, data: ByteArray) -> Unit) = Thread {
        val pureArray = ByteArray(10_000_000) // TODO add to configuration.
        val packet = DatagramPacket(pureArray, pureArray.size)
        val buffer = ByteBuffer.wrap(pureArray)
        while (shouldListen) {
            try {
                packet.data = pureArray
                buffer.clear()
                datagramSocket.receive(packet)
                val packetId = ByteArray(64)
                buffer[packetId]
                val packetIdentification = String(packetId)
                if (!networkHistory.containsKey(packetIdentification)) {
                    networkHistory[packetIdentification] = System.currentTimeMillis()
                    val endPointId = buffer.get()
                    val endPoint = EndPoint.byId(endPointId) ?: throw Exception("Such ID of $endPointId does not exist.")
                    val messageIdentification = ByteArray(64)
                    buffer[messageIdentification]
                    val messageId = String(messageIdentification)
                    val totalSlices = buffer.get().toInt()
                    val currentSlice = buffer.get().toInt()
                    val dataArray = ByteArray(buffer.int)
                    buffer[dataArray]

                    if (totalSlices > 1) {
                        Logger.trace("Received $currentSlice of $totalSlices [${dataArray.size}] for ${messageId.subSequence(20, 30)}")
                        val builder = buildingPackets.computeIfAbsent(messageId) {
                            PacketBuilder(messageId, endPoint, totalSlices)
                        }
                        builder.addData(currentSlice, dataArray)
                        if (builder.isReady) {
                            Logger.trace("Running freshly built packet!")
                            GlobalScope.launch { block(endPoint, builder.asOne) }
                            buildingPackets.remove(messageId)
                        }
                    } else GlobalScope.launch { block(endPoint, dataArray) }

                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                dashboardManager.reportException(e)
            }
        }
    }.start()

    data class PacketBuilder(
        val messageIdentification: String,
        val endPoint: EndPoint,
        val arraySize: Int,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        val isReady get() = total == arraySize

        private var total = 0
        private val data = arrayOfNulls<ByteArray>(arraySize)

        fun addData(index: Int, dataToAdd: ByteArray) {
            total++
            data[index] = dataToAdd
            Logger.info("Added data, total now $total")
        }

        // Note: Use carefully!
        val asOne get() = data.fold(ByteArray(0)) { a, b -> a + b!! }

    }
}