package communication

import data.Configuration
import data.Endpoint
import data.Node
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import logging.Logger
import logging.Dashboard
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.random.Random

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

    fun send(endpoint: Endpoint, messageId: String, messageData: ByteArray, transmissionType: TransmissionType, nodes: Array<out Node>) {
        messageQueue.put(UDPMessage(endpoint, messageId, messageData, nodes, transmissionType == TransmissionType.Broadcast))
    }

    init {
        Thread {
            val writingBuffer = ByteBuffer.allocate(65535)
            while (shouldListen) {
                messageQueue.take().apply {
                    try {
                        val dataSize = messageData.size
                        val packetSize = configuration.packetSplitSize
                        val slicesNeeded = dataSize / packetSize + 1
                        var totalDelay: Long = 0

                        writingBuffer.apply {
                            (0 until slicesNeeded).forEach { slicePosition ->
                                clear()
                                val timestamp = "${System.currentTimeMillis()}${UUID.randomUUID()}".toByteArray()
                                val signedTimestamp = crypto.sign(timestamp)
                                val from = slicePosition * packetSize
                                val to = Integer.min(from + packetSize, dataSize)
                                val data = messageData.sliceArray(from until to)
                                val packetId = DigestUtils.sha256Hex(signedTimestamp + data)
                                val broadcastByte: Byte = if (isBroadcast) 1 else 0

                                put(packetId.toByteArray())
                                put(broadcastByte)
                                put(endpoint.identification)
                                put(messageId.toByteArray())
                                put(slicesNeeded.toByte())
                                put(slicePosition.toByte())
                                putInt(data.size)
                                put(data)

                                val packet = DatagramPacket(array(), 0, position())
                                val started = System.currentTimeMillis()
                                recipients.forEach {
                                    packet.socketAddress = InetSocketAddress(it.ip, it.port)
                                    sendingSocket.send(packet)
                                    if (false && isBroadcast) {
                                        val randomDelay = Random.nextLong(10, 30)
                                        totalDelay += randomDelay
                                        Thread.sleep(randomDelay)
                                    }
                                }
                                totalDelay += System.currentTimeMillis() - started
                            }
                            recipients.forEach {
                                dashboard.sentMessage(messageId, endpoint, crypto.publicKey, it.publicKey, dataSize, totalDelay)
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

    fun startListening(block: (endpoint: Endpoint, data: ByteArray) -> Unit) = Thread {
        val pureArray = ByteArray(65535)
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
                    val endPoint = Endpoint.byId(endPointId) ?: throw Exception("Such ID of $endPointId does not exist.")
                    val messageIdentification = ByteArray(64)
                    buffer[messageIdentification]
                    val messageId = String(messageIdentification)
                    // dashboardManager.receivedMessage(messageId, DigestUtils.sha256Hex(crypto.publicKey))
                    val totalSlices = buffer.get().toInt()
                    val currentSlice = buffer.get().toInt()
                    val dataArray = ByteArray(buffer.int)
                    buffer[dataArray]

                    val builder = buildingPackets.computeIfAbsent(messageId) {
                        PacketBuilder(knownNodes.values, configuration, messageId, endPoint, totalSlices)
                    }
                    builder.addData(currentSlice, dataArray)
                    val neededMore = builder.data.count { it == null }
                    val text = if (neededMore == 0) "${Logger.green}DONE${Logger.reset}" else "$neededMore pieces."
                    Logger.trace("Received $endPoint ${currentSlice + 1} of $totalSlices [${dataArray.size}]\tfor ${messageId.subSequence(20, 30)}\tNeed $text")
                    if (builder.isReady) {
                        coroutineAndReport { block(endPoint, builder.asOne) }
                        buildingPackets.remove(messageId)
                    }

                    if (isBroadcast) {
                        packet.length = buffer.position()
                        builder.recipients.forEach {
                            packet.socketAddress = InetSocketAddress(it.ip, it.port)
                            broadcastingSocket.send(packet)
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                dashboard.reportException(e)
            }
        }
    }.start()

    private fun coroutineAndReport(block: () -> Unit) {
        GlobalScope.launch {
            try {
                block()
            } catch (e: Exception) {
                dashboard.reportException(e)
            }
        }
    }

    data class PacketBuilder(
        val knownNodes: Collection<Node>,
        val configuration: Configuration,
        val messageIdentification: String,
        val endpoint: Endpoint,
        val arraySize: Int,
        val createdAt: Long = System.currentTimeMillis()
    ) {

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

}

class UDPMessage(
    val endpoint: Endpoint,
    val messageId: String,
    val messageData: ByteArray,
    val recipients: Array<out Node>,
    val isBroadcast: Boolean
)