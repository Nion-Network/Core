package data

import data.NetworkRequestType.GET
import data.NetworkRequestType.POST

/**
 * Created by Mihael Valentin Berčič
 * on 15/10/2020 at 14:44
 * using IntelliJ IDEA
 */

enum class VoteType { FOR, AGAINST, SKIP }
enum class NetworkRequestType { GET, POST }
enum class SlotDuty { PRODUCER, COMMITTEE, VALIDATOR }
enum class DebugType { ALL, DEBUG, INFO, ERROR, TRACE, CHAIN, CONSENSUS }


enum class EndPoint(val requestType: NetworkRequestType, val path: String) {
    Ping(GET, "/ping"),
    Join(POST, "/join"),
    Include(POST, "/include"),
    Query(POST, "/query"),
    Found(POST, "/found"),
    OnJoin(POST, "/joined"),
    Search(GET, "/search"),
    Vote(POST, "/vote"),
    BlockReceived(POST, "/block"),
    SyncReply(POST, "/syncReply"),
    SyncRequest(POST, "/syncRequest"),
    OnVoteRequest(POST, "/voteRequest"),
    UpdateDockerStats(POST, "/dockerStats"),
    RunDockerImage(GET, "/run/image")
}