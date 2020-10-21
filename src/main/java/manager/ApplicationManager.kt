package manager

import chain.BlockProducer
import data.*
import logging.Logger
import utils.Crypto
import utils.Utils
import java.net.InetAddress

/**
 * Created by Mihael Valentin Berčič
 * on 24/09/2020 at 14:15
 * using IntelliJ IDEA
 */
class ApplicationManager(configFileContent: String) {

    val configuration: Configuration = Utils.gson.fromJson<Configuration>(configFileContent, Configuration::class.java)
    val currentState = State(0, 0, 0, configuration.initialDifficulty)
    val crypto = Crypto(".")
    val timeManager = TimeManager()

    val vdfManager = VDFManager()
    val dhtManager = DHTManager(this)
    val chainManager = ChainManager(this)
    val blockProducer = BlockProducer(this)
    val networkManager = NetworkManager(this)
    val validatorManager = ValidatorManager(this)
    val committeeManager = CommitteeManager(this)


    val isTrustedNode: Boolean get() = InetAddress.getLocalHost().hostAddress == configuration.trustedNodeIP && configuration.trustedNodePort == configuration.listeningPort

    val currentValidators: MutableSet<String> = mutableSetOf()
    val validatorSetChanges: MutableMap<String, Boolean> = if (isTrustedNode) mutableMapOf(crypto.publicKey to true) else mutableMapOf()

    val ourNode get() = Node(crypto.publicKey, myIP, configuration.listeningPort)
    private val myIP: String get() = InetAddress.getLocalHost().hostAddress

    //InfluxDB
    val dashboardManager = DashboardManager(this)


    init {
        try {
            Logger.trace("My IP is $myIP")
            networkManager.start()
            if (!isTrustedNode) chainManager.requestSync()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val identificationMessage: Message<Node> get() = generateMessage(ourNode)

    fun updateValidatorSet(block: Block) = block.validatorChanges.forEach { (publicKey, change) ->
        if (change) currentValidators.add(publicKey) else currentValidators.remove(publicKey)
    }

    /**
     * Creates DHT search query message looking for a node with specified public key.
     *
     * @param lookingFor Public key of the node we're looking for.
     * @return Message with body of type QueryMessage.
     */
    fun createQueryMessage(lookingFor: String): Message<QueryMessage> = generateMessage(QueryMessage(ourNode, lookingFor))

    /**
     * Create a generics message ready to be sent across the network.
     *
     * @param T Message body class type
     * @param data Body of type T to be serialized into JSON.
     * @return Message with the signed body type of T, current publicKey and the body itself.
     */
    fun <T> generateMessage(data: T): Message<T> = Message(crypto.publicKey, crypto.sign(Utils.gson.toJson(data)), data)
}
