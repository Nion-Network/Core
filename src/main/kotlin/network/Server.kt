package network

import data.Configuration
import data.communication.TransmissionType
import data.network.Endpoint
import data.network.Node
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.DatagramSocket
import java.net.ServerSocket
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
    private val knownNodes = ConcurrentHashMap<String, Node>()
    private val networkHistory = ConcurrentHashMap<String, Long>()
    private val udpSocket = DatagramSocket(configuration.port)
    private val tcpSocket = ServerSocket(configuration.port + 1)

    init {
        startHistoryCleanup()
    }

    open fun send(endpoint: Endpoint, transmissionType: TransmissionType, vararg nodes: Node) {

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

}