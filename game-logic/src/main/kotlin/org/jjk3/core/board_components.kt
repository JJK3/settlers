package org.jjk3.core

import java.util.*

interface Purchaseable {
    val price: List<Resource>
}

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
    override val price = listOf(Resource.Ore, Resource.Ore, Resource.Ore, Resource.Wheat, Resource.Wheat)
}

class Settlement(color: String) : City(color) {
    override val points = 1
    override val price = listOf(Resource.Wheat, Resource.Brick, Resource.Wood, Resource.Sheep)
}

class Road(color: String) : BoardPiece(color) {
    override val price = listOf(Resource.Wood, Resource.Brick)
}

class Port(val kind: Resource?, val rate: Int)
enum class Resource {
    Ore, Wood, Sheep, Brick, Wheat
}

interface DiceRoll{
    fun sum() : Int
}
class NormalDiceRoll : DiceRoll {
    val r: Random = Random()
    val die1 = r.nextInt(5) + 1
    val die2 = r.nextInt(5) + 1
    override fun sum() = die1 + die2
}
