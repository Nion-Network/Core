@file:Suppress("EnumEntryName")

package abstraction

import Main.gson
import utils.Utils

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:11
 * using IntelliJ IDEA
 */
data class Message<T>(val publicKey: String, val signature: String, val body: T) {
    val asJson: String get() = gson.toJson(this)
    val bodyAsString: String get() = gson.toJson(body)
}

data class Node(val publicKey: String, val ip: String, val port: Int) {
    fun <T> sendMessage(path: String, message: Message<T>): Pair<Int, String> = Utils.sendMessageTo("http://$ip:$port", path, message)
}


enum class NetworkRequest { GET, POST }
enum class ProtocolTasks { newBlock, requestBlocks, requestInclusion }