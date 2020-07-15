package network

import Main
import abstraction.Message
import abstraction.Node
import common.BlockData
import configuration.Configuration
import messages.*
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

    fun <T> broadcast(path: String, message: Message<T>) {
        for (node in nodeMap.values) node.sendMessage(path, message)
    }

    fun pickRandomNodes(amount: Int): List<Node> = nodeMap.values.shuffled().take(amount)

    fun <T> createGenericsMessage(data: T): Message<T> = Message(crypto.publicKey, crypto.sign(Main.gson.toJson(data)), data)

    /**
     * In order to avoid nasty code writing in other protocols, we create messages here...
     */

    // Message Creation
    fun createIdentificationMessage(): Message<IdentificationMessage> = createGenericsMessage(IdentificationMessage(Node(crypto.publicKey, myIP, configuration.listeningPort)))
    fun createVdfProofMessage(proof: String): Message<VdfProofBody> = createGenericsMessage(VdfProofBody(proof))
    fun createQueryMessage(lookingFor: String): Message<QueryMessageBody> = createGenericsMessage(QueryMessageBody(myIP, configuration.listeningPort, lookingFor))
    fun createNewBlockMessage(block: BlockData): Message<NewBlockMessageBody> = createGenericsMessage(NewBlockMessageBody(block))
    fun createRequestBlocksMessage(height: Int): Message<RequestBlocksMessageBody> = createGenericsMessage(RequestBlocksMessageBody(myIP, configuration.listeningPort, height))
    fun createResponseBlocksMessage(blocks: List<BlockData>): Message<ResponseBlocksMessageBody> = createGenericsMessage(ResponseBlocksMessageBody(blocks))
    fun createValidatorInclusionRequestMessage(publicKey: String): Message<RequestInclusionBody> = createGenericsMessage(RequestInclusionBody(publicKey))
}