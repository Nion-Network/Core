package chain.data

import kotlinx.serialization.Serializable
import network.data.Node

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:47
 * using IntelliJ IDEA
 */
@Serializable
data class VoteRequest(val block: Block, val producer: Node)