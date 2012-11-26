package board

import core._
import UtilList._

/** The randomized bag of tile hexes and ports */
class TileBag(randAdd: Int = 0) extends RandomBag[Hex] {
    //(randAdd) add a number of completely random tiles
    add(new Hex(DesertType, 0))
    (1 to 3).foreach { i =>
        add(new Hex(BrickType, 0))
        add(new Hex(OreType, 0))
    }
    (1 to 4).foreach { i =>
        add(new Hex(WheatType, 0))
        add(new Hex(WoodType, 0))
        add(new Hex(SheepType, 0))
    }
    (1 to randAdd).foreach { i =>
        add(new Hex(HexType.RESOURCE_TYPES.pick_random, 0))
    }
}

class StandardPortBag extends RandomBag[Port] {
    add(new Port(BrickType, 2))
    add(new Port(WheatType, 2))
    add(new Port(WoodType, 2))
    add(new Port(OreType, 2))
    add(new Port(SheepType, 2))
    (1 to 4).foreach { i => add(new Port(null, 3)) }
}

class NumberBag extends RandomBag[Int] {
    List(5, 2, 6, 3, 8, 10, 9, 12, 11, 4, 8, 10, 9, 4, 5, 6, 3, 11).foreach { add(_) }
    //@items = (2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12)
}

/** The Standard Board */
class StandardBoard extends Board {

    def make_tile_bag = new TileBag
    def make_port_bag = new StandardPortBag
    def make_number_bag = new NumberBag

    /**
     * Initializes all the sub-class specific data.
     * i.e. name, expansion, tile-locations, tile-numbers etc.
     */
    def subclass_init = {
        //@recomended_players = 3..4
        //@expansion = StandardGame.new
        //@name = 'Standard Board'

        var coords: List[(Int, Int)] = List((-2, 1), (-2, 2), (-2, 3),
            (-1, 0), (-1, 1), (-1, 2), (-1, 3),
            (0, 0), (0, 1), (0, 2), (0, 3), (0, 4),
            (1, 0), (1, 1), (1, 2), (1, 3),
            (2, 1), (2, 2), (2, 3))

        for (c <- coords) {
            var hex = new RandomHexFromBag
            hex.coords = c
            add_hex(hex)
        }

        //Ports
        //each one is a tile coord + the edge number to put the port on
        var coords2 = List((1, 0, 0), (2, 1, 1), (2, 2, 2), (1, 3, 2), (0, 4, 3), (-1, 3, 4), (-2, 2, 4), (-2, 1, 5), (-1, 0, 0))

        for (triplet <- coords2) {
            var x = triplet._1
            var y = triplet._2
            var edge = triplet._3
            var portEdge = getTile(x, y).edges(edge)
            var port = new RandomPortFromBag
            for (n <- portEdge.nodes) {
                n.port = port
			    println("port:" + n);
            }
        }
    }
}

class NSizeNumberBag(val size: Int) extends NumberBag {
    for (i <- 1 to size) {
        add(new_number)
    }

    //Pick at a hex number at random
    def new_number: Int = {
        var n = rand.nextInt(10) + 2
        if (n == 7) {
            new_number
        } else {
            n
        }
    }
}

// a "Square" board where x and y dimenstions are the same.
class SquareBoard(side: Int) extends StandardBoard {
    override def make_tile_bag = new TileBag(side * side)
    override def make_number_bag = new NSizeNumberBag(side * side)

    override def subclass_init {
        var bag = new TileBag(side * side)
        //Tiles
        var coords: List[(Int, Int)] = Nil
        for (x <- 1 to side) {
            for (y <- 1 to side) {
                coords = (x, y) :: coords
            }
        }

        for (c <- coords) {
            var hex = bag.grab
            hex.coords = c
            add_hex(hex)
        }
    }
}
