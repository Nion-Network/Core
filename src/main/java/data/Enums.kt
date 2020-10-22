package data

import data.NetworkRequestType.GET
import data.NetworkRequestType.POST
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.LinkedBlockingQueue

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
    PING(POST, "/ping"),
    JOIN(POST, "/join"),
    INCLUDE(POST, "/include"),
    QUERY(POST, "/query"),
    FOUND(POST, "/found"),
    JOINED(POST, "/joined"),
    SEARCH(GET, "/search"),
    VOTE(POST, "/vote"),
    BLOCK(POST, "/block"),
    SYNC_REPLY(POST, "/syncReply"),
    SYNC_REQUEST(POST, "/syncRequest"),
    VOTE_REQUEST(POST, "/voteRequest"),
}