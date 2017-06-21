package org.jjk3.board

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

    override fun toString(): String {
        return "${javaClass.simpleName}(color='$color')"
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

