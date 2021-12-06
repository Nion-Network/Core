package data.network

import kademlia.asBitSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256

/**
 * Created by Mihael Valentin Berčič
 * on 10/08/2021 at 01:37
 * using IntelliJ IDEA
 *
 * Stores information of some Node in the network.
 */
@Serializable
data class Node(val ip: String, val port: Int, val publicKey: String) {

    @Transient
    val identifier = sha256(publicKey).asHex
    val bitSet get() = identifier.asBitSet

}