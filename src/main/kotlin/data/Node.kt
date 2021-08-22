package data

import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 10/08/2021 at 01:37
 * using IntelliJ IDEA
 *
 * Stores information of some Node in the network.
 */
@Serializable
data class Node(val publicKey: String, val ip: String, val port: Int)