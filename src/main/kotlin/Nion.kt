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
    val nion = Nion()
    nion.launch()
}