package abstraction

import Main
import utils.Utils

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:11
 * using IntelliJ IDEA
 */

data class Message(val publicKey: String, val signature: String, val body: String = "") {
    val asJson get():String = Main.gson.toJson(this)
}


data class Node(val publicKey: String, val ip: String, val port: Int) {
    fun sendMessage(path: String, message: Message) = Utils.sendMessageTo("http://$ip:$port", path, message)
}


enum class NetworkRequest { GET, POST }
enum class StartProtocol{newBlock,requestBlocks }