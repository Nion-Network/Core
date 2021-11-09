package network

import Nion
import data.communication.Message
import data.network.Endpoint
import logging.Logger

/**
 * Created by Mihael Valentin Berčič
 * on 06/11/2021 at 13:50
 * using IntelliJ IDEA
 */
class ChainBuilder(private val nion: Nion) {

    @MessageEndpoint(Endpoint.Ping)
    fun onPing(message: Message) {
        Logger.chain("Message: ${message.bodyAs<String>()}")
    }
    
}