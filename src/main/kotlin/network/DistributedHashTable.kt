package network

import data.communication.TransmissionType
import data.network.Endpoint
import data.network.Node

/**
 * Created by Mihael Valentin Berčič
 * on 02/11/2021 at 18:39
 * using IntelliJ IDEA
 */
abstract class DistributedHashTable : Server() {

    fun search(publicKey: String) {}

    override fun send(endpoint: Endpoint, transmissionType: TransmissionType, vararg nodes: Node) {

        super.send(endpoint, transmissionType, *nodes)
    }

}