package core

import java.util.*

interface Purchaseable
sealed class BoardPiece(val color: String) : Purchaseable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as BoardPiece

        if (color != other.color) return false

        return true
    }

    override fun hashCode(): Int {
        return color.hashCode()
    }
}

open class City(color: String) : BoardPiece(color) {
    open val points = 2
}

class Settlement(color: String) : City(color) {
    override val points = 1
}

class Road(color: String) : BoardPiece(color)
class Port(val kind: Resource?, val rate: Int)
enum class Resource {
    Ore, Wood, Sheep, Brick, Wheat
}

abstract class RandomBag<A> {
    private var items: List<A> = emptyList()
    private val rand = Random(System.currentTimeMillis())
    fun add(item: A) {
        items += item
    }

    fun grab(): A {
        if (items.isEmpty()) throw IllegalStateException("Cannot grab from an empty bag")
        val i = rand.nextInt(items.size)
        return pick_and_remove(i)
    }

    fun next() = pick_and_remove(0)
    fun pick_and_remove(i: Int): A {
        val grabbedItem = items[i]
        items = items.filterIndexed { index, _ -> index != i }
        return grabbedItem
    }
}
