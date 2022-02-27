package network.data

import network.data.communication.TransmissionLayer
import network.data.communication.TransmissionLayer.TCP
import network.data.communication.TransmissionLayer.UDP
import network.data.communication.TransmissionType
import network.data.communication.TransmissionType.Broadcast
import network.data.communication.TransmissionType.Unicast

/**
 * Created by Mihael Valentin Berčič
 * on 15/10/2020 at 14:44
 * using IntelliJ IDEA
 */
enum class Endpoint(val processing: MessageProcessing, val transmissionLayer: TransmissionLayer, val transmissionType: TransmissionType) {
    Ping(MessageProcessing.Queued, UDP, Broadcast),
    JoinRequest(MessageProcessing.Queued, UDP, Unicast),
    InclusionRequest(MessageProcessing.Immediate, UDP, Unicast),
    Welcome(MessageProcessing.Queued, UDP, Unicast),
    Vote(MessageProcessing.Immediate, TCP, Unicast),
    NewBlock(MessageProcessing.Queued, TCP, Broadcast),
    SyncReply(MessageProcessing.Queued, TCP, Unicast),
    SyncRequest(MessageProcessing.Immediate, UDP, Unicast),
    VoteRequest(MessageProcessing.Immediate, TCP, Unicast),
    NodeStatistics(MessageProcessing.Queued, UDP, Unicast)
    ;

    companion object {
        private val cache = values().associateBy { it.ordinal.toByte() }

        fun byId(id: Byte) = cache[id]
    }
}

