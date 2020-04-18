package network

import Main
import abstraction.Message
import abstraction.Node
import utils.Crypto
import java.security.KeyPair

/**
 * Created by Mihael Valentin Berčič
 * on 16/04/2020 at 13:45
 * using IntelliJ IDEA
 */
class NodeNetwork(private val maxNodes: Int, private val keyPair: KeyPair) {

    // <PublicKey, Node>
    val nodeMap: HashMap<String, Node> = hashMapOf()
    var isInNetwork = false

    val isFull get(): Boolean = nodeMap.size >= maxNodes

    fun pickRandomNodes(amount: Int): List<Node> = nodeMap.values.shuffled().take(amount)
    fun createMessage(text: String): Message = Message(keyPair.public.toString(), Crypto.sign(text, keyPair.private), text)
    fun createMessage(any: Any): Message = Main.gson.toJson(any).let { json -> Message(keyPair.public.toString(), Crypto.sign(json, keyPair.private), json) }


}