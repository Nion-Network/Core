package data

/**
 * Created by Mihael Valentin Berčič
 * on 15/10/2020 at 14:44
 * using IntelliJ IDEA
 */

enum class VoteType { FOR, AGAINST, SKIP }
enum class NetworkRequestType { GET, POST }
enum class SlotDuty { PRODUCER, COMMITTEE, VALIDATOR }
enum class DebugType { ALL, DEBUG, INFO, ERROR, TRACE, CHAIN, CONSENSUS }


enum class EndPoint(val identification: Byte) {
    Ping(0),
    Join(1),
    Include(2),
    Query(3),
    Found(4),
    OnJoin(5),
    Vote(6),
    BlockReceived(7),
    SyncReply(8),
    SyncRequest(9),
    OnVoteRequest(10),
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
