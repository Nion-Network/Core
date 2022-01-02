package chain.data

import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:47
 * using IntelliJ IDEA
 */
@Serializable
data class VoteInformation(val from: String, val timestamp: Long = System.currentTimeMillis())