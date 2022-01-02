package chain.data

import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:46
 * using IntelliJ IDEA
 */
@Serializable
data class ChainTask(val myTask: SlotDuty, val blockProducer: String, val committee: List<String> = emptyList())