package network

import Configuration
import java.net.DatagramSocket
import java.net.ServerSocket

/**
 * Created by mihael
 * on 21/01/2022 at 10:44
 * using IntelliJ IDEA
 */
open class SocketHolder(configuration: Configuration) {

    protected val udpSocket: DatagramSocket = DatagramSocket()
    protected val tcpSocket: ServerSocket = ServerSocket(0)
    protected val migrationSocket: ServerSocket = ServerSocket(0)

}