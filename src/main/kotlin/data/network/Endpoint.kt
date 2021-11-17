package data.network

/**
 * Created by Mihael Valentin Berčič
 * on 15/10/2020 at 14:44
 * using IntelliJ IDEA
 */
enum class Endpoint(val identification: Byte, val processing: MessageProcessing) {
    Ping(0, MessageProcessing.Queued),
    JoinRequest(1, MessageProcessing.Queued),
    InclusionRequest(2, MessageProcessing.Queued),
    NodeQuery(3, MessageProcessing.Immediate),
    QueryReply(4, MessageProcessing.Immediate),
    Welcome(5, MessageProcessing.Queued),
    Vote(6, MessageProcessing.Queued),
    NewBlock(7, MessageProcessing.Queued),
    SyncReply(8, MessageProcessing.Queued),
    SyncRequest(9, MessageProcessing.Queued),
    VoteRequest(10, MessageProcessing.Immediate),
    NodeStatistics(12, MessageProcessing.Queued),
    RepresentativeStatistics(13, MessageProcessing.Queued);


    companion object {
        private val cache = values().associateBy { it.identification }

        fun byId(id: Byte) = cache[id]
    }
}
