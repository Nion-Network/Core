package communication

import data.Configuration
import data.communication.PacketBuilder
import data.communication.TransmissionType
import data.network.Endpoint
import data.network.Node
import logging.Dashboard
import logging.Logger
import utils.Crypto
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256
import utils.coroutineAndReport
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Created by Mihael Valentin Berčič
 * on 13/04/2021 at 00:57
 * using IntelliJ IDEA
 */
class UDPServer(
    private val configuration: Configuration,
    private val crypto: Crypto,
    private val knownNodes: Map<String, Node>,
    port: Int
) {

    private val networkHistory = ConcurrentHashMap<String, Long>()
    private val messageQueue = LinkedBlockingQueue<UDPMessage>()
    private val buildingPackets = ConcurrentHashMap<String, PacketBuilder>()
    private val datagramSocket = DatagramSocket(port)
    private val sendingSocket = DatagramSocket(port + 1)
    private val broadcastingSocket = DatagramSocket(port + 2) // TODO remove if not needed.

    /** Sends the specific message using [transmission type][TransmissionType] to [nodes] on [endpoint][Endpoint]. */
    fun send(endpoint: Endpoint, transmissionType: TransmissionType, messageId: String, messageData: ByteArray, vararg nodes: Node) {
        messageQueue.put(UDPMessage(endpoint, messageId, messageData, nodes, transmissionType == TransmissionType.Broadcast))
    }

    init {
        startOutput()
        startHistoryCleanup()
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
            buildingPackets.forEach { (key, builder) ->
                val difference = System.currentTimeMillis() - builder.createdAt
                val shouldBeRemoved = TimeUnit.MILLISECONDS.toMinutes(difference) >= configuration.historyMinuteClearance
                if (shouldBeRemoved) networkHistory.remove(key)
            }
        }, 0, configuration.historyCleaningFrequency, TimeUnit.MINUTES)
    }


    /** Schedules message history cleanup service that runs at fixed rate. */
    private fun startHistoryCleanup() {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
            networkHistory.forEach { (messageHex, timestamp) ->
                val difference = System.currentTimeMillis() - timestamp
                val shouldBeRemoved = TimeUnit.MILLISECONDS.toMinutes(difference) >= configuration.historyMinuteClearance
                if (shouldBeRemoved) networkHistory.remove(messageHex)
            }
        }, 0, configuration.historyCleaningFrequency, TimeUnit.MINUTES)
    }

    /** Starts the thread for sending out queued messages. */
    private fun startOutput() {
        Thread {
            val writingBuffer = ByteBuffer.allocate(65535)
            while (true) {
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
                                val packetId = sha256(signedTimestamp + data).asHex
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
                                Dashboard.sentMessage(messageId, endpoint, crypto.publicKey, it.publicKey, dataSize, totalDelay)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Dashboard.reportException(e)
                    }
                }
            }
        }.start()
    }

    /** Starts the thread for listening and receiving UDP packets on the specified port. */
    fun startListening(block: (endpoint: Endpoint, data: ByteArray) -> Unit) {
        Thread {
            val pureArray = ByteArray(65535)
            val packet = DatagramPacket(pureArray, pureArray.size)
            val buffer = ByteBuffer.wrap(pureArray)
            while (true) {
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
                        // Logger.trace("Received $endPoint ${currentSlice + 1} of $totalSlices [${dataArray.size}]\tfor ${messageId.subSequence(20, 30)}\tNeed $text")
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
                    Dashboard.reportException(e)
                }
            }
        }.start()
    }


}