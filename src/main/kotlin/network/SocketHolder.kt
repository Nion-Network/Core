package network

import Configuration
import java.net.DatagramSocket
import java.net.ServerSocket

/**
 * Created by mihael
 * on 21/01/2022 at 10:44
 * using IntelliJ IDEA
 *
 * Utility class for keeping track of sockets when it comes to passing them around.
 */
open class SocketHolder(configuration: Configuration) {
    protected val udpSocket: DatagramSocket = DatagramSocket()
    protected val tcpSocket: ServerSocket = ServerSocket(0)
    protected val migrationSocket: ServerSocket = ServerSocket(0)
}