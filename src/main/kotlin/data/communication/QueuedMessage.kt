package data.communication

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 00:44
 * using IntelliJ IDEA
 */
@Serializable
data class QueuedMessage<T>(
    val value: Message<T>,
    @Transient val block: (Message<T>) -> Unit = {},
    @Transient val execute: () -> Unit = { block.invoke(value) }
)