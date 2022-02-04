package network

import network.data.Endpoint

/**
 * Created by Mihael Valentin Berčič
 * on 03/11/2021 at 18:54
 * using IntelliJ IDEA
 */
class MessageBuilder(val endpoint: Endpoint, private val numberOfPackets: Int, val nodes: Array<String>) {

    private var totalAdded = 0
    private val array = arrayOfNulls<ByteArray>(numberOfPackets)

    val missing get() = numberOfPackets - totalAdded

    fun addPart(position: Int, data: ByteArray): Boolean {
        array[position] = data
        totalAdded++
        return totalAdded == numberOfPackets
    }

    fun gluedData(): ByteArray {
        return array.requireNoNulls().fold(byteArrayOf()) { a, b -> a + b }
    }

}