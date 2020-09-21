package network

import Main
import abstraction.Message
import abstraction.Node
import common.BlockData
import configuration.Configuration
import logging.Logger
import messages.*
import org.apache.commons.codec.digest.DigestUtils
import utils.Crypto
import utils.networkHistory
import java.net.InetAddress

/**
 * Created by Mihael Valentin Berčič
 * on 16/04/2020 at 13:45
 * using IntelliJ IDEA
 */
class NodeNetwork(private val configuration: Configuration, private val crypto: Crypto) {

    val nodeMap: HashMap<String, Node> = hashMapOf()    // <PublicKey, Node>
    var isInNetwork = false

    val myIP: String get() = InetAddress.getLocalHost().hostAddress
    val isFull get(): Boolean = nodeMap.size >= configuration.maxNodes

    fun <T> broadcast(path: String, message: Message<T>, limited: Boolean = false) {
        val hexHash = DigestUtils.sha256Hex(message.signature)
        if (networkHistory.containsKey(hexHash)) return
        Logger.debug("Broadcasting a message to path $path [limited = $limited]...")
        networkHistory[hexHash] = message.timeStamp
        val shuffledNodes = nodeMap.values.shuffled()
        val amountToTake = if (limited) configuration.broadcastSpread else shuffledNodes.size
        for (node in shuffledNodes.take(amountToTake)) node.sendMessage(path, message)
    }

    fun pickRandomNodes(amount: Int): List<Node> = nodeMap.values.shuffled().take(amount)

    /**
     * Create a generics message ready to be sent across the network.
     *
     * @param T Message body class type
     * @param data Body of type T to be serialized into JSON.
     * @return Message with the signed body type of T, current publicKey and the body itself.
     */
    fun <T> createGenericsMessage(data: T): Message<T> = Message(crypto.publicKey, crypto.sign(Main.gson.toJson(data)), data)

    private val ourNode = Node(crypto.publicKey, myIP, configuration.listeningPort)

    fun createIdentificationMessage(): Message<IdentificationMessage> = createGenericsMessage(IdentificationMessage(ourNode))
    fun createVdfProofMessage(proof: String, block: Int): Message<VdfProofBody> = createGenericsMessage(VdfProofBody(proof, block))
    fun createQueryMessage(lookingFor: String): Message<QueryMessageBody> = createGenericsMessage(QueryMessageBody(ourNode, lookingFor))
    fun createNewBlockMessage(block: BlockData): Message<NewBlockMessageBody> = createGenericsMessage(NewBlockMessageBody(block))
    fun createRequestBlocksMessage(height: Int): Message<RequestBlocksMessageBody> = createGenericsMessage(RequestBlocksMessageBody(ourNode, height))
    fun createResponseBlocksMessage(blocks: List<BlockData>): Message<ResponseBlocksMessageBody> = createGenericsMessage(ResponseBlocksMessageBody(blocks))
    fun createValidatorInclusionRequestMessage(publicKey: String): Message<RequestInclusionBody> = createGenericsMessage(RequestInclusionBody(publicKey))
}