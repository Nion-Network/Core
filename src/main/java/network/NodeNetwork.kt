package network

import Main
import abstraction.Message
import abstraction.Node
import common.Block
import configuration.Configuration
import messages.NewBlockMessageBody
import messages.QueryMessageBody
import messages.WelcomeMessageBody
import utils.Crypto
import java.net.InetAddress

/**
 * Created by Mihael Valentin Berčič
 * on 16/04/2020 at 13:45
 * using IntelliJ IDEA
 */
class NodeNetwork(private val configuration: Configuration, private val crypto: Crypto) {

    // <PublicKey, Node>
    val nodeMap: HashMap<String, Node> = hashMapOf()
    var isInNetwork = false
    val myIP: String get() = InetAddress.getLocalHost().hostAddress

    val isFull get(): Boolean = nodeMap.size >= configuration.maxNodes

    fun broadcast(path: String, message: Message) {
        for (node in nodeMap.values) node.sendMessage(path, message)
    }

    fun pickRandomNodes(amount: Int): List<Node> = nodeMap.values.shuffled().take(amount)

    fun createMessage(text: String): Message = Message(crypto.publicKey, crypto.sign(text), text)
    fun createMessage(any: Any): Message = Main.gson.toJson(any).let { json -> Message(crypto.publicKey, crypto.sign(json), json) }

    /**
     * In order to avoid nasty code writing in other protocols, we create messages here...
     */

    // Message Creation
    fun createQueryMessage(lookingFor: String): Message = createMessage(QueryMessageBody(myIP, configuration.listeningPort, lookingFor))
    fun createWelcomeMessage(): Message = createMessage(WelcomeMessageBody(Node(crypto.publicKey, myIP, configuration.listeningPort)))
    fun createNewBlockMessage(block: Block) : Message = createMessage(NewBlockMessageBody(block));
}