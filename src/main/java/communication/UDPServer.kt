package communication

import data.Configuration
import logging.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer

/**
 * Created by Mihael Valentin Berčič
 * on 13/04/2021 at 00:57
 * using IntelliJ IDEA
 */
class UDPServer(configuration: Configuration, port: Int) {

    var shouldListen = true
    private val datagramSocket = DatagramSocket(port)

    fun send(packet: DatagramPacket) {
        datagramSocket.send(packet)
    }


    fun startListening(block: ByteBuffer.() -> Unit) {
        Thread {
            val pureArray = ByteArray(10000) // TODO add to configuration.
            val packet = DatagramPacket(pureArray, pureArray.size)
            val buffer = ByteBuffer.wrap(pureArray)
            while (shouldListen) {
                packet.data = pureArray
                datagramSocket.receive(packet)
                buffer.clear()
                block(buffer)
            }
        }.start()
    }

}