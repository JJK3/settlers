package board

import core.*
import java.util.*

/** The randomized bag of tile hexes and ports */
class TileBag(randAdd: Int = 0) : RandomBag<Hex>() {
    //(randAdd) add a number of completely random tiles
    init {
        add(Hex(null, 0))
        (1..3).forEach {
            add(Hex(Resource.Brick, 0))
            add(Hex(Resource.Ore, 0))
        }
        (1..4).forEach {
            add(Hex(Resource.Wheat, 0))
            add(Hex(Resource.Wood, 0))
            add(Hex(Resource.Sheep, 0))
        }
        (1..randAdd).forEach { i ->
            val randomResource = Resource.values().toList().pick_random()
            add(Hex(randomResource, 0))
        }
    }
}

class StandardPortBag : RandomBag<Port>() {
    init {
        add(Port(Resource.Brick, 2))
        add(Port(Resource.Wheat, 2))
        add(Port(Resource.Wood, 2))
        add(Port(Resource.Ore, 2))
        add(Port(Resource.Sheep, 2))
        (1..4).forEach { i -> add(Port(null, 3)) }
    }
}

open class NumberBag : RandomBag<Int>() {
    init {
        listOf(5, 2, 6, 3, 8, 10, 9, 12, 11, 4, 8, 10, 9, 4, 5, 6, 3, 11).forEach { add(it) }
        //@items = (2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12)
    }
}

/** The Standard Board */
open class StandardBoard : Board(true) {

    init {
        var coords: List<HexCoordinate> = listOf(HexCoordinate(-2, 1), HexCoordinate(-2, 2), HexCoordinate(-2, 3),
                HexCoordinate(-1, 0), HexCoordinate(-1, 1), HexCoordinate(-1, 2), HexCoordinate(-1, 3),
                HexCoordinate(0, 0), HexCoordinate(0, 1), HexCoordinate(0, 2), HexCoordinate(0, 3), HexCoordinate(0, 4),
                HexCoordinate(1, 0), HexCoordinate(1, 1), HexCoordinate(1, 2), HexCoordinate(1, 3),
                HexCoordinate(2, 1), HexCoordinate(2, 2), HexCoordinate(2, 3))

        val tiles = TileBag()
        val numberBag = NumberBag()

        for (c in coords) {
            val hex = tiles.grab()
            hex.coords = c
            add_hex(hex)
        }

        SpiralIterator(this).getHexes().forEach { tile ->
            if (tile.resource != null) {
                tile.number = numberBag.next()
            }
        }

        //Ports
        val ports = StandardPortBag()
        var coords2 = listOf(EdgeCoordinate(1, 0,
                0), EdgeCoordinate(2, 1, 1), EdgeCoordinate(2, 2, 2), EdgeCoordinate(1, 3, 2), EdgeCoordinate(0, 4, 3),
                EdgeCoordinate(-1, 3, 4), EdgeCoordinate(-2, 2, 4), EdgeCoordinate(-2, 1, 5), EdgeCoordinate(-1, 0, 0))

        for (c in coords2) {
            var portEdge = getEdge(c)!!
            var port = ports.grab()
            for (n in portEdge.nodes()) {
                n.port = port
            }
        }
        enforce_bandit()
    }
}

class NSizeNumberBag(val size: Int) : NumberBag() {

    val rand = Random()

    init {
        for (i in 1..size) {
            add(number())
        }
    }

    //Pick at a hex number at random
    fun number(): Int {
        val n = rand.nextInt(10) + 2
        if (n == 7) {
            return number()
        } else {
            return n
        }
    }
}

// a "Square" board where x and y dimenstions are the same.
class SquareBoard(val side: Int) : StandardBoard() {
    init {
        var bag = TileBag(side * side)
        //Tiles
        var coords: List<Pair<Int, Int>> = emptyList()
        for (x in 1..side) {
            for (y in 1..side) {
                coords += Pair(x, y)
            }
        }

        for (c in coords) {
            var hex = bag.grab()
            hex.coords = HexCoordinate(c.first, c.second)
            add_hex(hex)
        }

        val number_bag = NSizeNumberBag(side * side)
        tiles.values.filterNot { it.resource == null }.forEach { tile ->
            tile.number = number_bag.grab()
        }
    }
}
