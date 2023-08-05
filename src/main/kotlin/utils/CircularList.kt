package utils

import kotlinx.serialization.Serializable

/**
 * Created by mihael
 * on 10/01/2022 at 14:07
 * using IntelliJ IDEA
 */
@Serializable
class CircularList<T>(private val maxCapacity: Int) : ArrayList<T>(maxCapacity) {

    /** Adds a new element to the list and removes the oldest element.*/
    override fun add(element: T): Boolean {
        if (size == maxCapacity) removeFirst()
        return super.add(element)
    }

    /** Returns all elements in this circular list. */
    fun elements() = toList()

}