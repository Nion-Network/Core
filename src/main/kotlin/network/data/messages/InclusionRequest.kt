package network.data.messages

import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:44
 * using IntelliJ IDEA
 */
@Serializable
data class InclusionRequest(val currentSlot: Long, val publicKey: String)