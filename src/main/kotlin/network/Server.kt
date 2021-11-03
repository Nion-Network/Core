package network

import data.Configuration
import data.communication.TransmissionType
import data.network.Endpoint
import data.network.Node
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import utils.Crypto
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Created by Mihael Valentin Berčič
 * on 13/04/2021 at 00:57
 * using IntelliJ IDEA
 */
abstract class Server {

    val configuration = Json.decodeFromString<Configuration>(File("./config.json").readText())
    val crypto = Crypto(".")
    val localAddress = InetAddress.getLocalHost()
    val localNode = Node(crypto.publicKey, localAddress.hostAddress, configuration.port)

    protected val knownNodes = ConcurrentHashMap<String, Node>()
    private val networkHistory = ConcurrentHashMap<String, Long>()
    private val udpSocket = DatagramSocket(configuration.port)
    private val tcpSocket = ServerSocket(configuration.port + 1)

    fun launch() {
        startHistoryCleanup()
        Thread(this::listenForUDP).start()
    }

    private fun listenForUDP() {
        val dataBuffer = ByteBuffer.allocate(configuration.packetSplitSize)
        val packet = DatagramPacket(dataBuffer.array(), configuration.packetSplitSize)
        while (true) dataBuffer.apply {
            udpSocket.receive(packet)
            // TODO finish...
        }
    }

    private fun sendUDP() {

    }

    open fun onMessageReceived() {}

    open fun <T> send(endpoint: Endpoint, transmissionType: TransmissionType, data: T, vararg nodes: Node) {
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

    fun pickRandomNodes(): List<Node> {
        val totalKnownNodes = knownNodes.size
        return knownNodes.values.take(5 + (configuration.broadcastSpreadPercentage * Integer.max(totalKnownNodes, 1) / 100))
    }



}