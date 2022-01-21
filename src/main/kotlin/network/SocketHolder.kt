package network

import logging.Logger
import java.net.DatagramSocket
import java.net.ServerSocket

/**
 * Created by mihael
 * on 21/01/2022 at 10:44
 * using IntelliJ IDEA
 */
open class SocketHolder {

    protected lateinit var udpSocket: DatagramSocket
        private set

    protected lateinit var tcpSocket: ServerSocket
        private set

    protected lateinit var kademliaSocket: DatagramSocket
        private set

    init {
        setup(5000)
    }

    private fun setup(port: Int) {
        try {
            udpSocket = DatagramSocket(port)
            tcpSocket = ServerSocket(port + 1)
            kademliaSocket = DatagramSocket(port + 2)
            Logger.info("We're using ports: $port ... ${port + 2}")
        } catch (_: Exception) {
            setup(port + 3)
        }
    }

}