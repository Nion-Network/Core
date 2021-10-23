package data.network

/**
 * Created by Mihael Valentin Berčič
 * on 15/10/2020 at 14:44
 * using IntelliJ IDEA
 */
enum class Endpoint(val identification: Byte) {
    Ping(0),
    JoinRequest(1),
    InclusionRequest(2),
    NodeQuery(3),
    NodeFound(4),
    Welcome(5),
    VoteReceived(6),
    NewBlock(7),
    SyncReply(8),
    SyncRequest(9),
    VoteRequest(10),
    UpdateDockerStats(11),
    RunNewImage(12),
    RunMigratedImage(13),
    NodeStatistics(14),
    RepresentativeStatistics(15);


    companion object {
        private val cache = values().map { it.identification to it }.toMap()

        fun byId(id: Byte) = cache[id]
    }
}
