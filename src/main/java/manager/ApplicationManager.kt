package manager

import blockchain.BlockChain
import blockchain.BlockProducer
import configuration.Configuration
import network.NetworkManager
import protocols.DHT
import protocols.ValidatorManager
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
    val currentState = State(0, 0, 0, configuration.initialDifficulty)
    val currentValidators = mutableListOf<String>()
    val crypto = Crypto(".")

    val vdf = VDF("http://localhost: ${configuration.listeningPort}/vdf")
    val networkManager = NetworkManager(this)
    val dhtProtocol: DHT = DHT(this)

    val blockProducer = BlockProducer(this)
    val validatorManager = ValidatorManager(this)
    val blockChain = BlockChain(this)

    val isTrustedNode: Boolean get() = InetAddress.getLocalHost().hostAddress == configuration.trustedNodeIP && configuration.trustedNodePort == configuration.listeningPort
}