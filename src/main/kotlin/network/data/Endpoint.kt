package network.data

/**
 * Created by Mihael Valentin Berčič
 * on 15/10/2020 at 14:44
 * using IntelliJ IDEA
 */
enum class Endpoint(val processing: MessageProcessing) {
    Ping(MessageProcessing.Queued),
    JoinRequest(MessageProcessing.Queued),
    InclusionRequest(MessageProcessing.Immediate),
    NodeQuery(MessageProcessing.Immediate),
    QueryReply(MessageProcessing.Immediate),
    Welcome(MessageProcessing.Queued),
    Vote(MessageProcessing.Queued),
    NewBlock(MessageProcessing.Queued),
    SyncReply(MessageProcessing.Queued),
    SyncRequest(MessageProcessing.Immediate),
    VoteRequest(MessageProcessing.Immediate),
    NodeStatistics(MessageProcessing.Immediate)
    ;

    companion object {
        private val cache = values().associateBy { it.ordinal.toByte() }

        fun byId(id: Byte) = cache[id]
    }
}
