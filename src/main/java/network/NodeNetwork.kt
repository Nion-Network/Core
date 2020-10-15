package network

import data.Message
import data.Node
import manager.ApplicationManager
import org.apache.commons.codec.digest.DigestUtils
import utils.networkHistory

val knownNodes: HashMap<String, Node> = hashMapOf()    // <PublicKey, Node>

/**
 * Created by Mihael Valentin Berčič
 * on 16/04/2020 at 13:45
 * using IntelliJ IDEA
 */
class NodeNetwork(applicationManager: ApplicationManager) {

    var isInNetwork = false

    private val configuration = applicationManager.configuration

    val isFull: Boolean get() = knownNodes.size >= configuration.maxNodes

    private fun pickRandomNodes(amount: Int): List<Node> = knownNodes.values.shuffled().take(amount)

    /**
     * Sends the message to a few randomly chosen nodes from the known nodes.
     *
     * @param T Message body type.
     * @param path Endpoint for the message to go to.
     * @param spread Amount of nodes to choose (if less are known, all are chosen)
     * @param message Message with body of type T.
     */
    fun <T> sendMessageToRandomNodes(path: String, spread: Int, message: Message<T>) = pickRandomNodes(spread).forEach { it.sendMessage(path, message) }

    /**
     * Broadcasts the specified message to known nodes in the network.
     *
     * @param T Message body type.
     * @param path Endpoint for the message to go to.
     * @param message Message with body of type T.
     * @param limited If true, broadcast spread will be limited to the amount specified in configuration,
     */
    fun <T> broadcast(path: String, message: Message<T>, limited: Boolean = false) {
        val hexHash = DigestUtils.sha256Hex(message.signature)
        if (networkHistory.containsKey(hexHash)) return
        // Logger.debug("Broadcasting a message to path $path [limited = $limited]...")
        networkHistory[hexHash] = message.timeStamp
        val shuffledNodes = knownNodes.values.shuffled()
        val amountToTake = if (limited) configuration.broadcastSpread else shuffledNodes.size
        for (node in shuffledNodes.take(amountToTake)) node.sendMessage(path, message)
    }

}