package manager

import blockchain.BlockProducer
import configuration.Configuration
import network.NetworkManager
import protocols.DHT
import state.State
import utils.Crypto
import utils.Utils
import utils.VDF
import java.net.InetAddress

/**
 * Created by Mihael Valentin Berčič
 * on 24/09/2020 at 14:15
 * using IntelliJ IDEA
 */
class ApplicationManager(configFileContent: String) {

    val configuration: Configuration = Utils.gson.fromJson<Configuration>(configFileContent, Configuration::class.java)
    val timerManager = TimeManager()
    val currentState = State(0, 0, 0, configuration.initialDifficulty)
    val crypto = Crypto(".")

    val currentValidators: MutableSet<String> = if (isTrustedNode) mutableSetOf(crypto.publicKey) else mutableSetOf()
    val validatorSetChanges: MutableMap<String, Boolean> = if (isTrustedNode) mutableMapOf(crypto.publicKey to true) else mutableMapOf()

    val networkManager = NetworkManager(this)

    val vdf = VDF("http://localhost:${configuration.listeningPort}/vdf")
    val vdfManager = VDFManager(this)
    val dhtProtocol: DHT = DHT(this)

    // Blockchain related
    val chainManager = ChainManager(this)
    val blockProducer = BlockProducer(this)
    val validatorManager = ValidatorManager(this)

    val isTrustedNode: Boolean get() = InetAddress.getLocalHost().hostAddress == configuration.trustedNodeIP && configuration.trustedNodePort == configuration.listeningPort

    init {

        try {
            networkManager.start()
            if (!isTrustedNode) chainManager.requestSync(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
