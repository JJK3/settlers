package org.jjk3.core

import org.junit.Assert.*
import org.junit.Test


class StandardBoardTest {


    @Test
    fun testAllEdges() {
        assertEquals(StandardBoard().allEdges().size, 72)
    }

    @Test
    fun testAllNodes() {
        assertEquals(StandardBoard().allNodes().size, 54)
    }

    @Test
    fun testTiles() {
        assertEquals(StandardBoard().tiles.values.size, 19)
    }

    @Test fun testStandardBoardHexNumbers() {
        var board = StandardBoard()
        assertEquals(board.tiles.values.count { it.number == 0 }, 1)
        board.tiles.values.filterNot { it.resource == null }.forEach { hex ->
            assertTrue("" + hex.coords, hex.number != 0)
            assertTrue(hex.number <= 12)
            assertTrue(hex.number != 7)
            assertTrue(hex.number >= 2)
        }
    }

    @Test fun testBoardEdgesHexSize() {
        var board = StandardBoard()
        assertNotNull(board.allEdges())
        assertTrue(board.allEdges().size != 0)
        board.allEdges().forEach { e ->
            assertNotNull(e)
            assertNotNull(e.hexes)
            assertTrue(e.hexes.size >= 1)
            assertTrue(e.hexes.size <= 2)
        }
    }

}

class EdgeTest2 {


    @Test fun testRoadTraversal() {
        val hex = Hex(null, 5)
        hex.edges.forEach { it.road = Road("blue") }
        var road_count = hex.edges.first().getCompleteRoad().size
        assertEquals(road_count, 6)
    }

    //Assert that each edge has adjecent 2 <= edges <= 4
    @Test fun testGetAdjecentEdgesCount() {
        var board = StandardBoard()
        val t = board.allEdges().map { it.getAdjecentEdges() }

        board.allNodes().forEach { node ->
            node.edges().forEach { edge ->
                assertTrue(edge.getAdjecentEdges().size <= 4)
                assertTrue(edge.getAdjecentEdges().size >= 2)
            }
        }
    }

    // Create a org.jjk3.board and visit roads that touch but have differing colors.
    @Test fun testVisit1() {
        var board = StandardBoard()
        var e1 = board.placeRoad(Road("red"), EdgeCoordinate(0, 2, 2))
        board.placeRoad(Road("red"), EdgeCoordinate(1, 2, 0))
        board.placeRoad(Road("red"), EdgeCoordinate(1, 1, 2))
        assertEquals(e1.getCompleteRoad().size, 3)

        var e4 = board.placeRoad(Road("blue"), EdgeCoordinate(1, 1, 1))
        board.placeRoad(Road("blue"), EdgeCoordinate(1, 1, 0))
        assertEquals(e4.getCompleteRoad().size, 2)

        board.placeRoad(Road("red"), EdgeCoordinate(0, 2, 3))
        board.placeRoad(Road("red"), EdgeCoordinate(0, 2, 4))
        assertEquals(e1.getCompleteRoad().size, 5)
        assertEquals(e4.getCompleteRoad().size, 2)
    }

    // Create a org.jjk3.board and visit the roads of a circle
    @Test fun testVisitRoad() {
        var board = StandardBoard()
        var e5 = board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 0))
        board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 1))
        board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 2))
        board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 3))
        board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 4))
        board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 5))
        var count = e5.getCompleteRoad().size
        assertEquals(count, 6)
    }

}

class NodeTest2 {
    //Test nodes know which nodes are touching.

    //Test nodes know which nodes are touching.
    @Test fun testGetAdjecentNodes2() {
        var board = StandardBoard()
        for (n in board.allNodes()) {
            var x = n.getAdjecentNodes()
            assertTrue(x.size >= 2)
            assertTrue(x.size <= 3)
        }

        assertEquals(2, board.getHex(HexCoordinate(0, 0)).nodes[0].getAdjecentNodes().size)
        assertEquals(3, board.getHex(HexCoordinate(0, 1)).nodes[5].getAdjecentNodes().size)
        assertEquals(3, board.getHex(HexCoordinate(1, 1)).nodes[3].getAdjecentNodes().size)
    }

    @Test fun testNodeHexes() {
        var board = StandardBoard()
        assertEquals(1, board.getNode(NodeCoordinate(0, 0, 0))!!.hexes.size)
        assertEquals(2, board.getNode(NodeCoordinate(0, 0, 1))!!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(1, 1, 1))!!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 0))!!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 1))!!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 2))!!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 3))!!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 4))!!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 5))!!.hexes.size)
    }

    /* Hexes and edges should share the same nodes. */
    @Test fun testSameNodes() {
        var board = StandardBoard()
        assertSame(board.getNode(NodeCoordinate(0, 1, 0)), board.getNode(NodeCoordinate(0, 0, 2)))
        assertSame(board.getNode(NodeCoordinate(0, 1, 0)), board.getNode(NodeCoordinate(1, 0, 4)))
    }

    /* //Test that a node knows the probablities touching it.
     @Test fun testGetHexProb() {
         assertEquals(1.0 / 36.0, Hex.dice_probs[2]!!, 0.0)
         assertEquals(1.0 / 36.0, Hex.dice_probs[12]!!, 0.0)
         assertEquals(2.0 / 36.0, Hex.dice_probs[3]!!, 0.0)

         var org.jjk3.board = StandardBoard()
         org.jjk3.board.getHex(1, 1)!!.randomNumber = 3
         org.jjk3.board.getHex(0, 2)!!.randomNumber = 2
         org.jjk3.board.getHex(0, 1)!!.randomNumber = 12
         val n = org.jjk3.board.getNode(1, 1, 5)!!.get_hex_prob()
         assertEquals((4.0 / 36.0), n, 0.0)
     }*/
}

class HexTest2 {

    @Test fun testGetOppositeHex() {
        assertEquals(HexCoordinate(0, 0), HexCoordinate(0, 1).getOppositeHex(EdgeNumber(0)))
    }

/*
    @Test fun testGetClockwiseConnectingEdge() {
        var org.jjk3.board = MiniBoard()
        assertEquals(org.jjk3.board.getHex(0, 0)!!.getClockwiseConnectingEdge().coords, org.jjk3.board.getEdge(0, 0, 3)!!.coords)
        assertEquals(org.jjk3.board.getHex(0, 1)!!.getClockwiseConnectingEdge().coords, org.jjk3.board.getEdge(0, 1, 4)!!.coords)
        assertEquals(org.jjk3.board.getHex(-1, 1)!!.getClockwiseConnectingEdge().coords, org.jjk3.board.getEdge(-1, 1, 0)!!.coords)
        assertEquals(org.jjk3.board.getHex(-1, 0)!!.getClockwiseConnectingEdge().coords, org.jjk3.board.getEdge(-1, 0, 1)!!.coords)
    }
*/

    @Test fun testMiniboard() {
        var board = MiniBoard()
        board.tiles.values.forEach { hex ->
            assertTrue(hex.isOnOutside())
        }
    }

    @Test fun testStandardBoard() {
        var board = StandardBoard()
        assertTrue(board.getHex(HexCoordinate(0, 0)).isOnOutside())
        assertFalse(board.getHex(HexCoordinate(0, 1)).isOnOutside())
    }

    @Test fun testDirections() {
        var hex = Hex(Resource.Brick, 8)
        hex.coords = HexCoordinate(0, 0)
        assertEquals(hex.coords.up(), HexCoordinate(0, -1))
        assertEquals(hex.coords.right_up(), HexCoordinate(1, -1))
        assertEquals(hex.coords.right_down(), HexCoordinate(1, 0))
        assertEquals(hex.coords.left_up(), HexCoordinate(-1, -1))
        assertEquals(hex.coords.left_down(), HexCoordinate(-1, 0))
        assertEquals(hex.coords.down(), HexCoordinate(0, 1))

        hex.coords = HexCoordinate(1, 1)
        assertEquals(hex.coords.up(), HexCoordinate(1, 0))
        assertEquals(hex.coords.right_up(), HexCoordinate(2, 1))
        assertEquals(hex.coords.right_down(), HexCoordinate(2, 2))
        assertEquals(hex.coords.left_up(), HexCoordinate(0, 1))
        assertEquals(hex.coords.left_down(), HexCoordinate(0, 2))
        assertEquals(hex.coords.down(), HexCoordinate(1, 2))

        hex.coords = HexCoordinate(-1, 0)
        assertEquals(hex.coords.up(), HexCoordinate(-1, -1))
        assertEquals(hex.coords.right_up(), HexCoordinate(0, 0))
        assertEquals(hex.coords.right_down(), HexCoordinate(0, 1))
        assertEquals(hex.coords.left_up(), HexCoordinate(-2, 0))
        assertEquals(hex.coords.left_down(), HexCoordinate(-2, 1))
        assertEquals(hex.coords.down(), HexCoordinate(-1, 1))
    }
}

class MiniBoard : Board() {
    init {
        var tileBag = TileBag.newBag()
        var coords = listOf(HexCoordinate(0, 0), HexCoordinate(0, 1), HexCoordinate(-1, 0), HexCoordinate(-1, 1))
        for (c in coords) {
            val (newBag, hex) = tileBag.removeRandom()
            tileBag = newBag
            hex.coords = c
            addHex(hex)
        }
    }
}

class BoardTest {
    fun check_opposing_edges(board: Board) {
        for (hex in board.tiles.values) {
            assertEquals(hex.edges.size, 6)
            EdgeNumber.ALL.forEach { i ->
                val opposite_hex = board.getHexOrNull(hex.coords.getOppositeHex(i))
                if (opposite_hex != null) {
                    assertEquals(opposite_hex.edge(i.opposite()).coords(), hex.edge(i).coords())
                }
            }
        }
    }

    /* Hexes and edges should share the same nodes. */
    @Test fun testSameNodes() {
        var board = StandardBoard()
        assertSame(board.getEdge(EdgeCoordinate(0, 1, 0)), board.getEdge(org.jjk3.core.EdgeCoordinate(0, 0, 3)))
        assertSame(board.getEdge(EdgeCoordinate(0, 1, 1)), board.getEdge(org.jjk3.core.EdgeCoordinate(1, 0, 4)))
        assertSame(board.getEdge(EdgeCoordinate(1, 1, 2)), board.getEdge(org.jjk3.core.EdgeCoordinate(2, 2, 5)))
    }

    @Test fun testMiniboardShouldBuildCorrectly() {
        var board = MiniBoard()
        assertEquals(board.allEdges().size, 19)
        assertEquals(board.allNodes().size, 16)
        assertEquals(board.getHex(HexCoordinate(0, 0)).edges[3].coords(),
                board.getHex(org.jjk3.core.HexCoordinate(0, 1)).edges[0].coords())
        assertEquals(board.getHex(HexCoordinate(0, 0)).edges[4].coords(),
                board.getHex(org.jjk3.core.HexCoordinate(-1, 0)).edges[1].coords())
        check_opposing_edges(board)
    }

    // Create a org.jjk3.board and place some roads on it.
    @Test fun testLongestRoadShouldBeDetected() {
        var board = StandardBoard()
        board.placeRoad(Road("red"), EdgeCoordinate(0, 2, 2))
        board.placeRoad(Road("red"), EdgeCoordinate(1, 2, 0))
        board.placeRoad(Road("red"), EdgeCoordinate(1, 1, 2))
        board.placeRoad(Road("blue"), EdgeCoordinate(1, 1, 1))
        board.placeRoad(Road("blue"), EdgeCoordinate(1, 1, 0))
        board.placeRoad(Road("red"), EdgeCoordinate(0, 2, 3))
        board.placeRoad(Road("red"), EdgeCoordinate(0, 2, 4))
        assertEquals(board.getLongestRoad(board.getEdge(EdgeCoordinate(0, 2, 4))!!).size, 5)
        assertTrue(board.hasLongestRoad("red"))
        assertTrue((!board.hasLongestRoad("blue")))

        var e5 = board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 0))
        board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 5))
        board.placeRoad(Road("orange"), EdgeCoordinate(-1, 0, 0))
        board.placeRoad(Road("orange"), EdgeCoordinate(-1, 0, 5))
        board.placeRoad(Road("orange"), EdgeCoordinate(-1, 0, 4))
        assertTrue((!board.hasLongestRoad("red")))
        assertTrue((!board.hasLongestRoad("blue")))
        assertTrue((!board.hasLongestRoad("orange")))

        board.placeRoad(Road("orange"), EdgeCoordinate(-1, 0, 1))
        var count = board.getLongestRoad(e5).size
        assertEquals(count, 5)
        assertTrue((!board.hasLongestRoad("red")))
        assertTrue((!board.hasLongestRoad("blue")))
        assertTrue((!board.hasLongestRoad("orange")))

        board.placeRoad(Road("orange"), EdgeCoordinate(-1, 0, 3))
        count = board.getLongestRoad(e5).size
        assertEquals(count, 6)
        assertTrue(board.hasLongestRoad("orange"))

        board.placeRoad(Road("orange"), EdgeCoordinate(-1, 0, 2))
        count = board.getLongestRoad(e5).size
        assertEquals(count, 8)
        assertTrue(board.hasLongestRoad("orange"))
    }

    // test that cities and settlements produce the right cards.
    @Test fun testGetCards() {
        var board = StandardBoard()
        board.tiles.values.forEach { it.number = 12 }

        var tile = board.getHex(HexCoordinate(0, 0))
        tile.has_bandit = false
        tile.number = 5
        tile.nodes[0].city = City("red")
        tile.nodes[5].city = Settlement("blue")
        var cards = board.getCards(5, "red")
        assertEquals(cards.size, 2)

        cards = board.getCards(5, "blue")
        assertEquals(cards.size, 1)
        tile.nodes[1].city = Settlement("red")
        board.getHex(HexCoordinate(1, 0)).number = 2
        cards = board.getCards(5, "red")
        assertEquals(cards.size, 3)
    }

    // test that cities and settlements produce the right cards.
    @Test fun testGetCardsWithBandit() {
        var board = StandardBoard()
        var tile = board.getHex(HexCoordinate(0, 0))
        tile.has_bandit = true
        tile.number = 5
        tile.nodes[0].city = City("red")
        tile.nodes[5].city = Settlement("blue")
        assertEquals(board.getCards(5, "red").size, 0)
        assertEquals(board.getCards(5, "blue").size, 0)
        tile.nodes[1].city = Settlement("red")
        board.getHex(HexCoordinate(1, 0)).number = 2
        assertEquals(board.getCards(5, "red").size, 0)
        assertEquals(board.getCards(5, "blue").size, 0)
    }

    @Test fun testValidRoadSpots() {
        var board = StandardBoard()
        assertEquals(board.getValidRoadSpots("red").size, 0)
        board.placeCity(Settlement("red"), NodeCoordinate(0, 0, 0))
        assertEquals(board.getValidRoadSpots("red").size, 2)
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 1))
        assertEquals(board.getValidRoadSpots("red").size, 3)
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 2))
        assertEquals(board.getValidRoadSpots("red").size, 4)

        board.placeCity(Settlement("blue"), NodeCoordinate(0, 0, 2))
        assertEquals(board.getValidRoadSpots("red").size, 2)
        assertEquals(board.getValidRoadSpots("blue").size, 2)
    }

    // Test that if you have 2 settlements connected , 1 road,
    // You're still allowed to build other roads
    @Test fun testValidRoadSpotsAndSettlements() {
        var board = StandardBoard()
        board.placeCity(Settlement("red"), NodeCoordinate(0, 0, 0))
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 1))
        board.placeRoad(Road("red"), EdgeCoordinate(1, 0, 0))
        board.placeRoad(Road("blue"), EdgeCoordinate(0, 0, 2))
        board.placeRoad(Road("blue"), EdgeCoordinate(0, 0, 0))
        assertEquals(board.getValidRoadSpots("red").size, 1)
        board.placeCity(Settlement("red"), NodeCoordinate(1, 0, 0))
        assertEquals(board.getValidRoadSpots("red").size, 1)
    }

    @Test fun testGetValidSettlementSpots() {
        var board = StandardBoard()
        var i = board.getValidSettlementSpots("red").size
        assertEquals(i, 0)
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 0))
        i = board.getValidSettlementSpots("red").size
        assertEquals(i, 2)

        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 1))
        i = board.getValidSettlementSpots("red").size
        assertEquals(i, 3)

        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 2))
        board.placeRoad(Road("red"), EdgeCoordinate(1, 0, 0))
        i = board.getValidSettlementSpots("red").size
        assertEquals(i, 5)
        i = board.getValidSettlementSpots("blue").size
        assertEquals(i, 0)

        board.placeCity(Settlement("red"), NodeCoordinate(0, 0, 0))
        i = board.getValidSettlementSpots("red").size
        assertEquals(i, 2)
    }

    //Test the randomNumber of nodes that are created in a standard org.jjk3.board.
    @Test fun testInsertNodes() {
        var board = StandardBoard()
        // count the nodes by edges
        var ns: List<Node> = emptyList()
        for (t in board.tiles.values) {
            for (e in t.edges) {
                for (n in e.nodes()) {
                    if (!ns.contains(n))
                        ns = ns + n
                }
            }
        }
        assertEquals(ns.size, 54)

        //count the nodes on the tiles
        var nodes: List<Node> = emptyList()
        var portNodes: List<Node> = emptyList()
        board.tiles.values.forEach { t ->
            t.nodes.forEach { n ->
                if (n.hasPort() && !portNodes.contains(n)) {
                    portNodes = portNodes + n
                }
                if (!nodes.contains(n)) {
                    nodes = nodes + n
                }
            }
        }
        assertEquals(nodes.size, 54)
        assertEquals(portNodes.size, 18)
    }

    /*  "dev_card_bag" should "removeRandom and remove cards" in {
      var dcb =  DevelopmentCardBag
      for (i <- 1 to 25){ 
        var card = dcb.get_card    
        assertTrue(card.isInstanceOf<DevelopmentCard>)
      }
      evaluating {
      	 dcb.get_card 
      } should produce <RuleException>
    }
	 */

    //A Board must have the bandit on it by funault.  ONLY one
    @Test fun testHasBandit() {
        var board = StandardBoard()
        var bandit_hex = board.tiles.values.filter { it.has_bandit }
        assertEquals(bandit_hex.size, 1)
    }

    //Test that a org.jjk3.board cannor be created , a port inland
    @Test fun testBADInlandPort() {
        //        evaluating {
        // InlandPortBoard
        //} should produce<IllegalStateException>
    }

}

object PrintBoard {
    fun main(args: Array<String>) {
        var board = StandardBoard()
        for (tile in board.tiles.values) {
            println(tile.coords.toString() + "  " + tile.resource)

        }
    }
}

object HugeBoard {
    fun main(args: Array<String>) {
        var s = System.currentTimeMillis()
        var board = SquareBoard(args[0].toInt())
        var s2 = System.currentTimeMillis()

        println("Board Hexes:" + board.tiles.size)
        println("Took " + ((s2 - s) / 1000.0) + " seconds")
    }
}

/** A invalid org.jjk3.board used in the test_create_board_,_inland_port */
class InlandPortBoard : StandardBoard() {
    /*   override fun subclass_init() {
           super.subclass_init()
           var portEdge = getHex(0, 2)!!.edges[2]
           var port = Port(null, 3)
           for (n in portEdge.nodes) {
               n.port = port
           }
       }*/
}

class TileBagTest {
    @Test fun testGetHex() {
        var bag = TileBag.newBag()
        for (i in 1..19) {
            val (newBag, hex) = bag.removeRandom()
            bag = newBag
        }
    }

    @Test fun testGetHex2() {
        var bag = TileBag.newBag()
        for (i in 1..19) {
            val (newBag, hex) = bag.removeRandom()
            bag = newBag
        }
    }

}
