package network

import abstraction.Message
import abstraction.Node
import blockchain.Block
import blockchain.BlockVote
import logging.Logger
import manager.ApplicationManager
import messages.*
import org.apache.commons.codec.digest.DigestUtils
import utils.Utils
import utils.networkHistory
import java.net.InetAddress

val knownNodes: HashMap<String, Node> = hashMapOf()    // <PublicKey, Node>

/**
 * Created by Mihael Valentin Berčič
 * on 16/04/2020 at 13:45
 * using IntelliJ IDEA
 */
class NodeNetwork(applicationManager: ApplicationManager) {

    private val crypto = applicationManager.crypto
    private val configuration = applicationManager.configuration
    private val ourNode = Node(crypto.publicKey, myIP, configuration.listeningPort)

    init {
        Logger.trace("My IP is $myIP")
    }

    var isInNetwork = false

    val myIP: String get() = InetAddress.getLocalHost().hostAddress
    val isFull get(): Boolean = knownNodes.size >= configuration.maxNodes

    fun <T> broadcast(path: String, message: Message<T>, limited: Boolean = false) {
        val hexHash = DigestUtils.sha256Hex(message.signature)
        if (networkHistory.containsKey(hexHash)) return
        // Logger.debug("Broadcasting a message to path $path [limited = $limited]...")
        networkHistory[hexHash] = message.timeStamp
        val shuffledNodes = knownNodes.values.shuffled()
        val amountToTake = if (limited) configuration.broadcastSpread else shuffledNodes.size
        for (node in shuffledNodes.take(amountToTake)) node.sendMessage(path, message)
    }

    fun pickRandomNodes(amount: Int): List<Node> = knownNodes.values.shuffled().take(amount)

    /**
     * Create a generics message ready to be sent across the network.
     *
     * @param T Message body class type
     * @param data Body of type T to be serialized into JSON.
     * @return Message with the signed body type of T, current publicKey and the body itself.
     */
    fun <T> createGenericsMessage(data: T): Message<T> = Message(crypto.publicKey, crypto.sign(Utils.gson.toJson(data)), data)

    fun createIdentificationMessage(): Message<IdentificationMessage> = createGenericsMessage(IdentificationMessage(ourNode))


    fun <T> sendMessageToRandomNodes(path: String, spread: Int, message: Message<T>) = pickRandomNodes(spread).forEach { it.sendMessage(path, message) }

    fun createQueryMessage(lookingFor: String): Message<QueryMessageBody> = createGenericsMessage(QueryMessageBody(ourNode, lookingFor))
    fun createNewBlockMessage(block: Block): Message<NewBlockMessageBody> = createGenericsMessage(NewBlockMessageBody(block))
    fun createRequestBlocksMessage(height: Int): Message<RequestBlocksMessageBody> = createGenericsMessage(RequestBlocksMessageBody(ourNode, height))
    fun createResponseBlocksMessage(blocks: List<Block>): Message<ResponseBlocksMessageBody> = createGenericsMessage(ResponseBlocksMessageBody(blocks))
    fun createValidatorInclusionRequestMessage(publicKey: String): Message<RequestInclusionBody> = createGenericsMessage(RequestInclusionBody(publicKey))
    fun createVoteMessage(vote: BlockVote): Message<BlockVote> = createGenericsMessage(vote)
}