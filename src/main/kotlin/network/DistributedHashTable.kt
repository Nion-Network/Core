package network

import data.communication.QueryMessage
import data.communication.TransmissionType
import data.network.Endpoint

/**
 * Created by Mihael Valentin Berčič
 * on 02/11/2021 at 18:39
 * using IntelliJ IDEA
 */
abstract class DistributedHashTable : Server() {

    fun queryFor(vararg publicKeys: String) {
        val unknown = publicKeys.filter { !knownNodes.containsKey(it) }
        if (unknown.isNotEmpty()) {
            val queryMessage = QueryMessage(localNode, publicKeys)
            send(Endpoint.NodeQuery, TransmissionType.Unicast, queryMessage)
        }
    }

    override fun send(endpoint: Endpoint, transmissionType: TransmissionType, data: Any, vararg publicKeys: String) {
        if (publicKeys.isNotEmpty()) queryFor(*publicKeys)
        super.send(endpoint, transmissionType, data, *publicKeys)
    }


}