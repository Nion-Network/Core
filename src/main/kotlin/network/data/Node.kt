package network.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import utils.asBitSet
import utils.asHex
import utils.sha256

/**
 * Created by Mihael Valentin Berčič
 * on 10/08/2021 at 01:37
 * using IntelliJ IDEA
 *
 * Stores information of some Node in the network.
 */
@Serializable
data class Node(
    val ip: String,
    val udpPort: Int,
    val tcpPort: Int,
    val kademliaPort: Int,
    val migrationPort: Int,
    val publicKey: String
) {

    @Transient
    val identifier = sha256(publicKey).asHex
    val bitSet get() = identifier.asBitSet

}