import data.communication.TransmissionType
import data.network.Endpoint
import network.DistributedHashTable

/**
 * Created by Mihael Valentin Berčič
 * on 26/03/2020 12:35
 * using IntelliJ IDEA
 */
class Nion : DistributedHashTable() {

}

fun main(args: Array<String>) {
    System.setProperty("kotlinx.coroutines.scheduler", "off")
    Nion().apply {
        launch()
        send(Endpoint.NewBlock, TransmissionType.Unicast, "Hejla!", "kiha")
    }
}