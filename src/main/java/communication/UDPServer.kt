package communication

import data.Configuration
import data.EndPoint
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
    private val datagramSocket = DatagramSocket(port)

    fun send(packet: DatagramPacket) {
        datagramSocket.send(packet)
    }


    fun startListening(block: (endPoint: EndPoint, data: ByteArray) -> Unit) {
        Thread {
            val pureArray = ByteArray(50000) // TODO add to configuration.
            val packet = DatagramPacket(pureArray, pureArray.size)
            val buffer = ByteBuffer.wrap(pureArray)
            while (shouldListen) {
                try {
                    packet.data = pureArray
                    datagramSocket.receive(packet)
                    buffer.clear()

                    val hex = ByteArray(buffer.int)
                    buffer[hex]
                    if (!networkHistory.containsKey(String(hex))) {
                        networkHistory[String(hex)] = System.currentTimeMillis()
                        val id = buffer.get()
                        val endPoint = EndPoint.byId(id) ?: throw Exception("Such ID of $id does not exist.")
                        val messageBytes = ByteArray(buffer.int)
                        buffer[messageBytes]
                        GlobalScope.launch {
                            block.invoke(endPoint, messageBytes)
                        }
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                    dashboardManager.reportException(e)
                }
            }
        }.start()
    }

}