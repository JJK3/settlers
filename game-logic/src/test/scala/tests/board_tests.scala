import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.AssertionsForJUnit
import scala.collection.mutable.ListBuffer
import org.junit.Assert._
import org.junit.Test
import org.junit.Before

import core._
import board._

class EdgeTest extends AssertionsForJUnit {
    @Test def testEdgeEqual = {
        var e1 = new Edge(1, 2, 3)
        var e2 = new Edge(1, 2, 3)
        assertEquals(e1, e2)
    }

    @Test def testEdgeEqual2 = {
        var e1 = new Edge(1, 2, 3)
        var e2 = new Edge(1, 2, 4)
        assertFalse(e1 == e2)
    }

    @Test def testGetAdjecentEdges = {
        var e1 = new Edge(0, 0, 0)
        var e2 = new Edge(0, 0, 1)
        var e3 = new Edge(0, 0, 2)
        var e4 = new Edge(0, 0, 3)
        var n1 = new Node(0, 0, 1)
        var n2 = new Node(0, 0, 2)
        e1.nodes = List(n1)
        n1.edges = List(e1, e2)
        e2.nodes = List(n1, n2)
        n2.edges = List(e2, e3, e4)
        e3.nodes = List(n2)
        e4.nodes = List(n2)
        assertEquals(e1.get_adjecent_edges.size, 1)
        assertEquals(e2.get_adjecent_edges.size, 3)
        assertEquals(e3.get_adjecent_edges.size, 2)
        assertEquals(e4.get_adjecent_edges.size, 2)
    }

    @Test def testRoadTraversal = {
        var e1 = new Edge(0, 0, 0)
        var e2 = new Edge(0, 0, 1)
        var e3 = new Edge(0, 0, 2)
        var e4 = new Edge(0, 0, 3)
        var n1 = new Node(0, 0, 1)
        var n2 = new Node(0, 0, 2)
        e1.nodes = List(n1)
        n1.edges = List(e1, e2)
        e2.nodes = List(n1, n2)
        n2.edges = List(e2, e3, e4)
        e3.nodes = List(n2)
        e4.nodes = List(n2)
        List(e1, e2, e3, e4).foreach { _.road = new Road("blue") }
        var road_count = 0
        e1.visit_road(
            { (e: Edge) => road_count += 1 })
        assertEquals(road_count, 4)
    }

    //Assert that each edge has adjecent 2 <= edges <= 4
    @Test def testGetAdjecentEdgesCount = {
        var board = new StandardBoard
        board.all_nodes.foreach { node =>
            node.edges.foreach { edge =>
                assertTrue(edge.get_adjecent_edges.size <= 4)
                assertTrue(edge.get_adjecent_edges.size >= 2)
            }
        }
    }

    // Create a board and visit roads that touch but have differing colors.
    @Test def testVisit1 = {
        var board = new StandardBoard
        var e1 = board.place_road(new Road("red"), 0, 2, 2)
        board.place_road(new Road("red"), 1, 2, 0)
        board.place_road(new Road("red"), 1, 1, 2)
        var count = 0
        e1.visit_road { (e: Edge) => count += 1 }
        assertEquals(count, 3)

        var e4 = board.place_road(new Road("blue"), 1, 1, 1)
        board.place_road(new Road("blue"), 1, 1, 0)
        count = 0
        e4.visit_road { (e: Edge) => count += 1 }
        assertEquals(count, 2)

        board.place_road(new Road("red"), 0, 2, 3)
        board.place_road(new Road("red"), 0, 2, 4)
        count = 0
        e1.visit_road { (e: Edge) => count += 1 }
        assertEquals(count, 5)
        count = 0
        e4.visit_road { (e: Edge) => count += 1 }
        assertEquals(count, 2)
    }

    // Create a board and visit the roads of a circle
    @Test def testVisitRoad = {
        var board = new StandardBoard
        var e5 = board.place_road(new Road("orange"), 0, 0, 0)
        board.place_road(new Road("orange"), 0, 0, 1)
        board.place_road(new Road("orange"), 0, 0, 2)
        board.place_road(new Road("orange"), 0, 0, 3)
        board.place_road(new Road("orange"), 0, 0, 4)
        board.place_road(new Road("orange"), 0, 0, 5)
        var count = 0
        e5.visit_road { (e: Edge) => count += 1 }
        assertEquals(count, 6)
    }

	/* Hexes and edges should share the same nodes. */
    @Test def testSameNodes = { 
	  var board = new StandardBoard
	  assertSame(board.getEdge(0, 1, 0), board.getEdge(0, 0, 3))
	  assertSame(board.getEdge(0, 1, 1), board.getEdge(1, 0, 4))
	  assertSame(board.getEdge(1, 1, 2), board.getEdge(2, 2, 5))
	}
}

class NodeTest extends AssertionsForJUnit {
    //Test nodes know which nodes are touching.
    @Test def testGetAdjecentNodes = {
        var board = new StandardBoard
        var n5 = board.getTile(0, 0).nodes(5)
        var n0 = board.getTile(0, 0).nodes(0)
        var n1 = board.getTile(0, 0).nodes(1)
        assertTrue(n5.get_adjecent_nodes.contains(n0))
        assertTrue(n0.get_adjecent_nodes.contains(n1))
        assertTrue((!n5.get_adjecent_nodes.contains(n1)))
    }

    //Test nodes know which nodes are touching.
    @Test def testGetAdjecentNodes2 = {
        var board = new StandardBoard
        for (n: Node <- board.all_nodes) {
            var x = n.get_adjecent_nodes
            assertTrue(x.length >= 2)
            assertTrue(x.length <= 3)
        }

        //test some specific nodes
        //tileCoord, node//, expectedResult
        var tileNodeResult = List((0, 0, 0, 2), (0, 1, 5, 3), (1, 1, 3, 3))
        for ((x: Int, y: Int, node: Int, expected_result: Int) <- tileNodeResult) {
            val len = board.getTile(x, y).nodes(node).get_adjecent_nodes.length
            assertEquals(expected_result, len)
        }
    }

    @Test def testNodeHexes = {
        var board = new StandardBoard
        assertEquals(1, board.getNode(0, 0, 0).hexes.size)
        assertEquals(1, board.getNode(0, 0, 1).hexes.size)
        assertEquals(3, board.getNode(1, 1, 1).hexes.size)
        assertEquals(3, board.getNode(0, 1, 0).hexes.size)
        assertEquals(3, board.getNode(0, 1, 1).hexes.size)
        assertEquals(3, board.getNode(0, 1, 2).hexes.size)
        assertEquals(3, board.getNode(0, 1, 3).hexes.size)
        assertEquals(3, board.getNode(0, 1, 4).hexes.size)
        assertEquals(3, board.getNode(0, 1, 5).hexes.size)
    }
  
	/* Hexes and edges should share the same nodes. */
    @Test def testSameNodes = { 
	  var board = new StandardBoard
	  assertSame(board.getNode(0, 1, 0), board.getNode(-1, 0, 2))
//	  assertSame(board.getNode(0, 1, 0), board.getNode(0, 0, 4))
//	  assertSame(board.getNode(), board.getNode()))
	}

    //Test that a node knows the probablities touching it.
    @Test def testGetHexProb = {
        assertEquals((1.0 / 36.0), Hex.dice_probs(2), 0)
        assertEquals((1.0 / 36.0), Hex.dice_probs(12), 0)
        assertEquals((2.0 / 36.0), Hex.dice_probs(3), 0)

        var board = new StandardBoard
        board.getTile(1, 1).number = 3
        board.getTile(0, 2).number = 2
        board.getTile(0, 1).number = 12
        val n = board.getNode(1, 1, 5).get_hex_prob
        assertEquals((4.0 / 36.0), n, 0)
    }
}

class HexTest extends AssertionsForJUnit {
    @Test def testConnectHexes = {
        var h1 = new Hex(BrickType, 8)
        var h2 = new Hex(BrickType, 8)
        var h3 = new Hex(BrickType, 8)
        h1.coords = (0, 1)
        h2.coords = (1, 0)
        h3.coords = (1, 1)

        assertTrue(h1.edges.forall { _ == null })
        assertTrue(h2.edges.forall { _ == null })
        assertTrue(h3.edges.forall { _ == null })
        assertTrue(h1.nodes.forall { _ == null })
        assertTrue(h2.nodes.forall { _ == null })
        assertTrue(h3.nodes.forall { _ == null })
        /*
        h1.connect_hex(1, h2)
        h2.connect_hex(3, h3)
        h1.connect_hex(2, h3)
        assertEquals(h1.edges(1), h2.edges(4))
        assertEquals(h1.edges(2), h3.edges(5))
        assertNull(h1.edges(3))
        assertNull(h1.edges(0))
        assertEquals(h2.edges(3), h3.edges(0))
        assertNull(h2.edges(2))
        assertNull(h2.edges(1))
*/
    }

    @Test def testGetOppositeHex = {
  	  var board = new StandardBoard
	  assertSame(board.getTile(0, 0), board.get_opposite_hex(board.getTile(0, 1), 0))
    }

    @Test def testGetClockwiseConnectingEdge = {
        var board = new MiniBoard
        assertEquals(board.getTile(0, 0).get_clockwise_connecting_edge.coords, board.getEdge(0, 0, 3).coords)
        assertEquals(board.getTile(0, 1).get_clockwise_connecting_edge.coords, board.getEdge(0, 1, 4).coords)
        assertEquals(board.getTile(-1, 1).get_clockwise_connecting_edge.coords, board.getEdge(-1, 1, 0).coords)
        assertEquals(board.getTile(-1, 0).get_clockwise_connecting_edge.coords, board.getEdge(-1, 0, 1).coords)
    }

    @Test def testMiniboard = {
        var board = new MiniBoard
        board.tiles.values.foreach { hex =>
            assertTrue(hex.is_on_edge)
        }
    }

    @Test def testStandardBoard = {
        var board = new StandardBoard
        assertTrue(board.getTile(0, 0).is_on_edge)
        assertFalse(board.getTile(0, 1).is_on_edge)
    }

    @Test def testDirections = {
        var hex = new Hex(BrickType, 8)
        hex.coords = (0, 0)
        assertEquals(hex.up, (0, -1))
        assertEquals(hex.right_up, (1, -1))
        assertEquals(hex.right_down, (1, 0))
        assertEquals(hex.left_up, (-1, -1))
        assertEquals(hex.left_down, (-1, 0))
        assertEquals(hex.down, (0, 1))

        hex.coords = (1, 1)
        assertEquals(hex.up, (1, 0))
        assertEquals(hex.right_up, (2, 1))
        assertEquals(hex.right_down, (2, 2))
        assertEquals(hex.left_up, (0, 1))
        assertEquals(hex.left_down, (0, 2))
        assertEquals(hex.down, (1, 2))

        hex.coords = (-1, 0)
        assertEquals(hex.up, (-1, -1))
        assertEquals(hex.right_up, (0, 0))
        assertEquals(hex.right_down, (0, 1))
        assertEquals(hex.left_up, (-2, 0))
        assertEquals(hex.left_down, (-2, 1))
        assertEquals(hex.down, (-1, 1))
	}
}

class MiniBoard extends StandardBoard {
    override def subclass_init = {
        var coords: List[(Int, Int)] = List(
            (0, 0), (0, 1), (-1, 0), (-1, 1))
        for (c <- coords) {
            var hex = new RandomHexFromBag
            hex.coords = c
            add_hex(hex)
        }
    }
}

class MicroBoard extends StandardBoard {
    override def subclass_init = {
        var coords: List[(Int, Int)] = List(
            (0, 0), (0, 1))
        for (c <- coords) {
            var hex = new RandomHexFromBag
            hex.coords = c
            add_hex(hex)
        }
    }
}

class BoardTest extends AssertionsForJUnit {
    def check_opposing_edges(board: Board) = {
        for (hex <- board.tiles.values) {
            assertEquals(hex.edges.size, 6)
            for (i <- 0 to 5) {
                var opposite_hex = board.get_opposite_hex(hex, i)
                if (opposite_hex != null) {
                    assertEquals(opposite_hex.edges((i + 3) % 6).coords, hex.edges(i).coords)
                }
            }
        }
    }

    @Test def testMiniboardShouldBuildCorrectly = {
        var board = new MiniBoard
        assertEquals(board.all_edges.size, 19)
        assertEquals(board.all_nodes.size, 16)
        assertEquals(board.getTile(0, 0).edges(3).coords, board.getTile(0, 1).edges(0).coords)
        assertEquals(board.getTile(0, 0).edges(4).coords, board.getTile(-1, 0).edges(1).coords)
        check_opposing_edges(board)
    }

    @Test def testStandardBoardElements = {
        var board = new StandardBoard
        check_opposing_edges(board)
        assertEquals(board.tiles.values.size, 19)
        assertEquals(board.all_nodes.size, 54)
        assertEquals(board.all_edges.size, 72)
    }

    @Test def testStandardBoardHexNumbers = {
        var board = new StandardBoard
        assertEquals(board.tiles.values.count { _.number == 0 }, 1)
        board.tiles.values.filterNot { _.card_type == DesertType }.foreach { hex =>
            assertTrue("" + hex.coords, hex.number != 0)
            assertTrue(hex.number <= 12)
            assertTrue(hex.number != 7)
            assertTrue(hex.number >= 2)
        }
    }

    @Test def testBoardEdgesHexSize = {
        var board = new StandardBoard
        assertNotNull(board.all_edges)
        assertTrue(board.all_edges.size != 0)
        board.all_edges.foreach { e =>
            assertNotNull(e)
            assertNotNull(e.hexes)
            assertTrue(e.hexes.size >= 1)
            assertTrue(e.hexes.size <= 2)
        }
    }

    // Create a board and place some roads on it.
    @Test def testLongestRoadShouldBeDetected = {
        var board = new StandardBoard
        var e1 = board.place_road(new Road("red"), 0, 2, 2)
        board.place_road(new Road("red"), 1, 2, 0)
        board.place_road(new Road("red"), 1, 1, 2)
        var e4 = board.place_road(new Road("blue"), 1, 1, 1)
        board.place_road(new Road("blue"), 1, 1, 0)
        board.place_road(new Road("red"), 0, 2, 3)
        board.place_road(new Road("red"), 0, 2, 4)
        assertTrue(board.has_longest_road("red"))
        assertTrue((!board.has_longest_road("blue")))

        var e5 = board.place_road(new Road("orange"), 0, 0, 0)
        board.place_road(new Road("orange"), 0, 0, 5)
        board.place_road(new Road("orange"), -1, 0, 0)
        board.place_road(new Road("orange"), -1, 0, 5)
        board.place_road(new Road("orange"), -1, 0, 4)
        assertTrue((!board.has_longest_road("red")))
        assertTrue((!board.has_longest_road("blue")))
        assertTrue((!board.has_longest_road("orange")))

        board.place_road(new Road("orange"), -1, 0, 1)
        var count = board.longest_road(e5)
        assertEquals(count, 5)
        assertTrue((!board.has_longest_road("red")))
        assertTrue((!board.has_longest_road("blue")))
        assertTrue((!board.has_longest_road("orange")))

        board.place_road(new Road("orange"), -1, 0, 3)
        count = board.longest_road(e5)
        assertEquals(count, 6)
        assertTrue(board.has_longest_road("orange"))

        board.place_road(new Road("orange"), -1, 0, 2)
        count = board.longest_road(e5)
        assertEquals(count, 8)
        assertTrue(board.has_longest_road("orange"))
    }

    // test the find_longest_road method
    @Test def testSimpleLongestRoad = {
        var board = new StandardBoard
        board.place_road(new Road("orange"), 0, 0, 0)
        var edge1 = board.place_road(new Road("orange"), 0, 0, 1)
        var edge2 = board.place_road(new Road("orange"), 0, 0, 4)
        board.place_road(new Road("orange"), 0, 0, 5)
        board.place_road(new Road("orange"), -1, 0, 0)
        board.place_road(new Road("orange"), -1, 0, 2)
        if (board.find_longest_road(edge1, 1) == 5) {
            assertEquals(board.find_longest_road(edge1, 1), 5)
            assertEquals(board.find_longest_road(edge1, 0), 1)
        } else {
            assertEquals(board.find_longest_road(edge1, 1), 1)
            assertEquals(board.find_longest_road(edge1, 0), 5)
        }

        //since we don't know the exact order of the edge nodes, we test for one or the other
        if (board.find_longest_road(edge2, 1) == 2) {
            assertEquals(board.find_longest_road(edge2, 1), 2)
            assertEquals(board.find_longest_road(edge2, 0), 4)
        } else {
            assertEquals(board.find_longest_road(edge2, 1), 4)
            assertEquals(board.find_longest_road(edge2, 0), 2)
        }
    }

    // test that cities and settlements produce the right cards.
    @Test def testGetCards = {
        var board = new StandardBoard
        board.tiles.values.foreach { _.number = 12 }
        assertEquals(board.get_cards(1, null).size, 0)
        assertEquals(board.get_cards(13, null).size, 0)

        var tile = board.getTile(0, 0)
        tile.card_type = OreType
        tile.has_bandit = false
        tile.number = 5
        tile.nodes(0).city = new City("red")
        tile.nodes(5).city = new Settlement("blue")
        var cards = board.get_cards(5, "red")
        assertEquals(cards.length, 2)

        cards = board.get_cards(5, "blue")
        assertEquals(cards.length, 1)
        tile.nodes(1).city = new Settlement("red")
        board.getTile(1, 0).number = 2
        cards = board.get_cards(5, "red")
        assertEquals(cards.length, 3)
    }

    // test that cities and settlements produce the right cards.
    @Test def testGetCardsWithBandit = {
        var board = new StandardBoard
        var tile = board.getTile(0, 0)
        tile.has_bandit = true
        tile.number = 5
        tile.nodes(0).city = new City("red")
        tile.nodes(5).city = new Settlement("blue")
        assertEquals(board.get_cards(5, "red").size, 0)
        assertEquals(board.get_cards(5, "blue").size, 0)
        tile.nodes(1).city = new Settlement("red")
        board.getTile(1, 0).number = 2
        assertEquals(board.get_cards(5, "red").size, 0)
        assertEquals(board.get_cards(5, "blue").size, 0)
    }

    @Test def testValidRoadSpots = {
        var board = new StandardBoard
        assertEquals(board.get_valid_road_spots("red").length, 0)
        board.place_city(new Settlement("red"), 0, 0, 0)
        assertEquals(board.get_valid_road_spots("red").length, 2)
        board.place_road(new Road("red"), 0, 0, 1)
        assertEquals(board.get_valid_road_spots("red").length, 3)
        board.place_road(new Road("red"), 0, 0, 2)
        assertEquals(board.get_valid_road_spots("red").length, 4)

        board.place_city(new Settlement("blue"), 0, 0, 2)
        assertEquals(board.get_valid_road_spots("red").length, 2)
        assertEquals(board.get_valid_road_spots("blue").length, 2)
    }

    // Test that if you have 2 settlements connected with 1 road, 
    // You're still allowed to build other roads
    @Test def testValidRoadSpotsAndSettlements = {
        var board = new StandardBoard
        board.place_city(new Settlement("red"), 0, 0, 0)
        board.place_road(new Road("red"), 0, 0, 1)
        board.place_road(new Road("red"), 1, 0, 0)
        board.place_road(new Road("blue"), 0, 0, 2)
        board.place_road(new Road("blue"), 0, 0, 0)
        assertEquals(board.get_valid_road_spots("red").length, 1)
        board.place_city(new Settlement("red"), 1, 0, 0)
        assertEquals(board.get_valid_road_spots("red").length, 1)
    }

    @Test def testGetValidSettlementSpots = {
        var board = new StandardBoard
        /*        evaluating {
            board.get_valid_settlement_spots(false, null).length
        } should produce[IllegalArgumentException]
*/
        var i = board.get_valid_settlement_spots(true, "red").length
        assertEquals(i, 0)
        board.place_road(new Road("red"), 0, 0, 0)
        i = board.get_valid_settlement_spots(true, "red").length
        assertEquals(i, 2)

        board.place_road(new Road("red"), 0, 0, 1)
        i = board.get_valid_settlement_spots(true, "red").length
        assertEquals(i, 3)

        board.place_road(new Road("red"), 0, 0, 2)
        board.place_road(new Road("red"), 1, 0, 0)
        i = board.get_valid_settlement_spots(true, "red").length
        assertEquals(i, 5)
        i = board.get_valid_settlement_spots(true, "blue").length
        assertEquals(i, 0)

        board.place_city(new Settlement("red"), 0, 0, 0)
        i = board.get_valid_settlement_spots(true, "red").length
        assertEquals(i, 2)
    }

    //Test the number of nodes that are created in a standard board.
    @Test def testInsertNodes = {
        var board = new StandardBoard
        // count the nodes by edges
        var ns: List[Node] = Nil
        for (t <- board.tiles.values) {
            for (e <- t.edges) {
                for (n <- e.nodes) {
                    if (!ns.contains(n))
                        ns = n :: ns
                }
            }
        }
        assertEquals(ns.length, 54)

        //count the nodes on the tiles
        var nodes: List[Node] = Nil
        var portNodes: List[Node] = Nil
        board.tiles.values.foreach { t =>
            t.nodes.foreach { n =>
                if (n.has_port && !portNodes.contains(n)) {
                    portNodes = n :: portNodes
                }
                if (!nodes.contains(n)) {
                    nodes = n :: nodes
                }
            }
        }
        assertEquals(nodes.length, 54)
        assertEquals(portNodes.length, 18)
    }

    /*  "dev_card_bag" should "grab and remove cards" in {
      var dcb = new DevelopmentCardBag
      for (i <- 1 to 25){ 
        var card = dcb.get_card    
        assertTrue(card.isInstanceOf[DevelopmentCard])
      }
      evaluating {
      	 dcb.get_card 
      } should produce [RuleException]
    }
	 */

    //A Board must have the bandit on it by default.  ONLY one
    @Test def testHasBandit = {
        var board = new StandardBoard
        var bandit_hex = board.tiles.values.filter { _.has_bandit }
        assertEquals(bandit_hex.size, 1)
    }

    //Test that a board cannor be created with a port inland
    @Test def testBADInlandPort = {
        //        evaluating {
        //new InlandPortBoard
        //} should produce[IllegalStateException]
    }

}

object PrintBoard {
    def main(args: Array[String]) {
        var board = new StandardBoard
        for (tile <- board.tiles.values) {
            println(tile.coords + "  " + tile.card_type)

        }
    }
}

object HugeBoard {
    def main(args: Array[String]) {
        var s = System.currentTimeMillis()
        var board = new SquareBoard(args(0).toInt)
        var s2 = System.currentTimeMillis()

        println("Board Hexes:" + board.tiles.size)
        println("Took " + ((s2 - s) / 1000.0) + " seconds")
    }
}

/** A invalid board used in the test_create_board_with_inland_port */
class InlandPortBoard extends StandardBoard {
    override def subclass_init {
        super.subclass_init
        var portEdge = getTile(0, 2).edges(2)
        var port = new Port(null, 3)
        for (n <- portEdge.nodes) {
            n.port = port
        }
    }
}

class TileBagTest extends AssertionsForJUnit {
    @Test def testGetHex = {
        var bag = new TileBag
        for (i <- 1 to 19) {
            bag.grab
        }
    }

    @Test def testGetHex2 = {
        var bag = new TileBag
        for (i <- 1 to 19) {
            bag.grab
        }
        /*        evaluating {
            bag.grab
        } should produce[IllegalStateException]
        */
    }

}

/*  
  //Run all the board tests but with a boarded loaded from disk
  class SavedBoardTest extends BoardTest
    def setup={
      super.setup()
      board  = BoardManager.load_board(File.join('src','boards', 'standard.board'))
  }
  }

//class SavedBoardTest2 < BoardTest
//  def setup
//    expected_hexes = 900
//    expected_edges = 2819
//    expected_nodes = 1920
//    expected_ports = 0
//    board  = BoardManager.load_board('src/boards/square.board')
//  end
//end
//
//class SavedBoardTest3 < BoardTest
//  def setup
//    expected_hexes = 24
//    expected_edges = 99
//    expected_nodes = 76
//    expected_ports = 0
//    board  = BoardManager.load_board('src/boards/l_shaped.board')
//  end
//end

*/ 
