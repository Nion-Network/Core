package chain.data

import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:46
 * using IntelliJ IDEA
 *
 * Holds information about the next task assigned to an individual node.
 *
 * This class encapsulates details regarding the upcoming operation or action
 * that the node needs to perform within the network or system workflow.
 */
@Serializable
data class ChainTask(val myTask: SlotDuty, val blockProducer: String, val committee: List<String> = emptyList(), val quorum: List<String> = emptyList())