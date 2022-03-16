package utils

import kotlinx.serialization.Serializable

/**
 * Created by mihael
 * on 10/01/2022 at 14:07
 * using IntelliJ IDEA
 */
@Serializable
class CircularList<T : Number>(private val maxCapacity: Int) {

    private val elements = ArrayList<T>(maxCapacity)

    /** Adds a new element to the list and removes the oldest element.*/
    fun add(element: T) {
        if (elements.size == maxCapacity) elements.removeFirst()
        elements.add(element)
    }

    /** Returns all elements in this circular list. */
    fun elements() = elements.toList()

    override fun hashCode(): Int = elements.foldIndexed(0) { index, acc, t -> acc + index * t.toInt() }

    override fun toString(): String {
        return elements.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other is CircularList<*>) return other.elements == elements
        return super.equals(other)
    }

}