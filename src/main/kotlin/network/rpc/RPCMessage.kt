package network.rpc

import kotlinx.serialization.Serializable

/**
 * @author Mihael Berčič on 30. 5. 25.
 */
@Serializable
data class RPCMessage<T>(val topic: Topic, val data: T)