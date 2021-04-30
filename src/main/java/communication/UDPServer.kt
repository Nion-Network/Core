package communication

import data.Configuration
import data.EndPoint
import data.Message
import data.Node
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import logging.Logger
import manager.Dashboard
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

/**
 * Created by Mihael Valentin Berčič
 * on 13/04/2021 at 00:57
 * using IntelliJ IDEA
 */
class UDPServer(
    private val configuration: Configuration,
    private val crypto: Crypto,
    private val dashboard: Dashboard,
    private val knownNodes: Map<String, Node>,
    private val networkHistory: MutableMap<String, Long>,
    port: Int
) {

    var shouldListen = true

    private val messageQueue = LinkedBlockingQueue<UDPMessage>()

    private val buildingPackets = hashMapOf<String, PacketBuilder>()
    private val datagramSocket = DatagramSocket(port)

    private val sendingSocket = DatagramSocket(port + 1)
    private val broadcastingSocket = DatagramSocket(port + 2)

    fun send(endPoint: EndPoint, packet: Message<*>, transmissionType: TransmissionType, nodes: Array<out Node>) {
        messageQueue.put(UDPMessage(endPoint, packet, nodes, transmissionType == TransmissionType.Broadcast))
    }

    init {
        Thread {
            val writingBuffer = ByteBuffer.allocate(65535)
            while (shouldListen) {
                messageQueue.take().apply {
                    try {
                        val messageId = message.uid.toByteArray()
                        val messageBytes = message.asJson.toByteArray()
                        val dataSize = messageBytes.size

                        val packetSize = configuration.packetSplitSize
                        val slicesNeeded = dataSize / packetSize + 1

                        writingBuffer.apply {
                            (0 until slicesNeeded).forEach { slicePosition ->
                                clear()
                                val from = slicePosition * packetSize
                                val to = Integer.min(from + packetSize, dataSize)
                                val data = messageBytes.sliceArray((from until to))
                                val packetId = DigestUtils.sha256Hex(data).toByteArray()
                                val broadcastByte: Byte = if (isBroadcast) 1 else 0

                                put(packetId)
                                put(broadcastByte)
                                put(endPoint.identification)
                                put(messageId)
                                put(slicesNeeded.toByte())
                                put(slicePosition.toByte())
                                putInt(data.size)
                                put(data)
                                val packet = DatagramPacket(array(), 0, position())
                                recipients.forEach {
                                    packet.socketAddress = it.socketAddress
                                    sendingSocket.send(packet)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        dashboard.reportException(e)
                    }
                }
            }
        }.start()
    }

    fun startListening(block: (endPoint: EndPoint, data: ByteArray) -> Unit) = Thread {
        val pureArray = ByteArray(65535) // TODO add to configuration.
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
                    val isBroadcast = buffer.get() > 0
                    val endPointId = buffer.get()
                    val endPoint = EndPoint.byId(endPointId) ?: throw Exception("Such ID of $endPointId does not exist.")
                    val messageIdentification = ByteArray(64)
                    buffer[messageIdentification]
                    val messageId = String(messageIdentification)
                    // dashboardManager.receivedMessage(messageId, DigestUtils.sha256Hex(crypto.publicKey))
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

                    val dataLength = buffer.position()
                    if (isBroadcast) {
                        val shuffledNodes = knownNodes.values.shuffled()
                        val totalSize = shuffledNodes.size
                        val amountToTake = 5 + (configuration.broadcastSpreadPercentage * Integer.max(totalSize, 1) / 100)
                        val nodes = shuffledNodes.take(amountToTake)
                        packet.length = dataLength
                        Logger.debug("Started re broadcasting!")
                        nodes.forEach {
                            packet.socketAddress = InetSocketAddress(it.ip, it.port)
                            broadcastingSocket.send(packet)
                        }
                        Logger.debug("Re-Sent broadcast packet to ${nodes.size} nodes.")
                    }
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                dashboard.reportException(e)
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

class UDPMessage(val endPoint: EndPoint, val message: Message<*>, val recipients: Array<out Node>, val isBroadcast: Boolean)