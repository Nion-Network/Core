package network

import data.network.Node

/**
 * Created by Mihael Valentin Berčič
 * on 03/11/2021 at 18:54
 * using IntelliJ IDEA
 */
class MessageBuilder(private val numberOfPackets: Int, vararg val nodes: Node) {

    private var totalAdded = 0
    private val array = arrayOfNulls<ByteArray>(numberOfPackets)

    fun addPart(position: Int, data: ByteArray): Boolean {
        array[position] = data
        return totalAdded == numberOfPackets
    }

    fun gluedData(): ByteArray {
        return array.requireNoNulls().fold(byteArrayOf()) { a, b -> a + b }
    }

}