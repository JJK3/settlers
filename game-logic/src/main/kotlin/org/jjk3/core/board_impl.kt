package org.jjk3.core

import java.util.*

/** The randomized bag of tile hexes and ports */
object TileBag {
    fun newBag(randAdd: Int = 0): Bag<Hex> {
        var bag = Bag<Hex>()
        bag += Hex(null, 0)
        (1..3).forEach {
            bag += Hex(Resource.Brick, 0)
            bag += Hex(Resource.Ore, 0)
        }
        (1..4).forEach {
            bag += Hex(Resource.Wheat, 0)
            bag += Hex(Resource.Wood, 0)
            bag += Hex(Resource.Sheep, 0)
        }
        (1..randAdd).forEach {
            val randomResource = Resource.values().toList().pick_random()
            bag += Hex(randomResource, 0)
        }
        return bag
    }
}

object StandardPortBag {
    fun newBag(): Bag<Port> {
        var bag = Bag<Port>()
        bag += Port(Resource.Brick, 2)
        bag += Port(Resource.Wheat, 2)
        bag += Port(Resource.Wood, 2)
        bag += Port(Resource.Ore, 2)
        bag += Port(Resource.Sheep, 2)
        (1..4).forEach { bag += Port(null, 3) }
        return bag
    }
}

class PiecesForSale(val color: String) {
    var cities: Bag<City> = Bag((1..4).map { City(color) })
    var settlements: Bag<Settlement> = Bag((1..5).map { Settlement(color) })
    var roads: Bag<Road> = Bag((1..15).map { Road(color) })
    fun takeCity(): City = cities.removeRandom().also { cities = it.first }.second
    fun takeSettlement(): Settlement = settlements.removeRandom().also { settlements = it.first }.second
    fun takeRoad(): Road = roads.removeRandom().also { roads = it.first }.second
    fun putBack(piece: City) = { cities += piece }
    fun putBack(piece: Settlement) = { settlements += piece }
    fun putBack(piece: Road) = { roads += piece }
}


val NumberBag = Bag(listOf(5, 2, 6, 3, 8, 10, 9, 12, 11, 4, 8, 10, 9, 4, 5, 6, 3, 11))

object StandardColors {
    val colors = listOf("red", "blue", "white", "orange", "green", "brown")
}

/** The Standard Board */
open class StandardBoard : Board(true) {
    init {
        val coords: List<HexCoordinate> = listOf(HexCoordinate(-2, 1), HexCoordinate(-2, 2), HexCoordinate(-2, 3),
                HexCoordinate(-1, 0), HexCoordinate(-1, 1), HexCoordinate(-1, 2), HexCoordinate(-1, 3),
                HexCoordinate(0, 0), HexCoordinate(0, 1), HexCoordinate(0, 2), HexCoordinate(0, 3), HexCoordinate(0, 4),
                HexCoordinate(1, 0), HexCoordinate(1, 1), HexCoordinate(1, 2), HexCoordinate(1, 3),
                HexCoordinate(2, 1), HexCoordinate(2, 2), HexCoordinate(2, 3))

        var tiles: Bag<Hex> = TileBag.newBag()
        var numberBag: Bag<Int> = NumberBag

        for (c in coords) {
            val (newTiles, hex) = tiles.removeRandom()
            tiles = newTiles
            hex.coords = c
            addHex(hex)
        }

        SpiralIterator(this).getHexes().forEach { tile ->
            if (tile.resource != null) {
                val (newBag, grabbedNumber) = numberBag.removeRandom()
                numberBag = newBag
                tile.number = grabbedNumber
            }
        }

        //Ports
        var ports: Bag<Port> = StandardPortBag.newBag()
        val coords2 = listOf(EdgeCoordinate(1, 0,
                0), EdgeCoordinate(2, 1, 1), EdgeCoordinate(2, 2, 2), EdgeCoordinate(1, 3, 2), EdgeCoordinate(0, 4, 3),
                EdgeCoordinate(-1, 3, 4), EdgeCoordinate(-2, 2, 4), EdgeCoordinate(-2, 1, 5), EdgeCoordinate(-1, 0, 0))

        for (c in coords2) {
            val portEdge = getEdge(c)
            val (newPorts, port) = ports.removeRandom()
            ports = newPorts
            for (n in portEdge.nodes()) {
                n.port = port
            }
        }

        StandardColors.colors.forEach { piecesForSale.put(it, PiecesForSale(it)) }
        placeBanditOnDesert()
    }
}

object NSizeNumberBag {
    val rand = Random()
    fun create(size: Int): Bag<Int> {
        var bag = Bag<Int>()
        for (i in 1..size) {
            bag += randomNumber()
        }
        return bag
    }

    fun randomNumber(): Int {
        val n = rand.nextInt(10) + 2
        if (n == 7) {
            return randomNumber()
        } else {
            return n
        }
    }
}

// a "Square" org.jjk3.board where x and y dimenstions are the same.
class SquareBoard(side: Int) : StandardBoard() {
    init {
        var bag: Bag<Hex> = TileBag.newBag(side * side)
        //Tiles
        var coords: List<Pair<Int, Int>> = emptyList()
        for (x in 1..side) {
            for (y in 1..side) {
                coords += Pair(x, y)
            }
        }

        for (c in coords) {
            val (newTiles, hex) = bag.removeRandom()
            bag = newTiles
            hex.coords = HexCoordinate(c.first, c.second)
            addHex(hex)
        }

        var numbers: Bag<Int> = NSizeNumberBag.create(side * side)
        tiles.values.filterNot { it.resource == null }.forEach { tile ->
            val (newNumbers, grabbed) = numbers.removeRandom()
            tile.number = grabbed
            numbers = newNumbers
        }
    }
}
