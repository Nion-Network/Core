package database

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

/**
 * @author Mihael Berčič on 9. 1. 26.
 */
object BlockTable : IntIdTable("blocks") {
    val slot                            = long("slot")
    val difficulty                          = integer("difficulty").default(0)
    val blockProducer                           = text("block_producer")
    val timestamp                           = long("timestamp")
    val precedentHash                           = varchar("precendtHash", 255)
    val votes                           = integer("votes").default(0)
    val committee                           = text("committee")
    val hash                            = text("hash")
    val votedMembers                            = text("voted_members")
}