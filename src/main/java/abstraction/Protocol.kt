package abstraction

import network.NetworkManager

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:11
 * using IntelliJ IDEA
 */
interface Protocol

data class Message(
        val publicKey: String,
        val signature: String,
        val body: String
)

data class Node(val publicKey: String, val port: Int) {

    fun sendJoinRequest(body: String) = ""

}


enum class NetworkRequest { GET, POST }