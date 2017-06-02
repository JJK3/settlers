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
