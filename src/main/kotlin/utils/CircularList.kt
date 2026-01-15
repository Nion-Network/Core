package utils

import kotlinx.serialization.Serializable

/**
 * Created by mihael
 * on 10/01/2022 at 14:07
 * using IntelliJ IDEA
 */
@Serializable
class CircularList<T>(private val maxCapacity: Int, val items: ArrayList<T> = ArrayList(maxCapacity)) {

    /** Adds a new element to the list and removes the oldest element.*/
    fun add(element: T): Boolean {
        if (items.size == maxCapacity) items.removeFirst()
        return items.add(element)
    }

}