package org.jjk3.board

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
        val board = StandardBoard()
        assertEquals(board.tiles.values.count { it.number == 0 }, 1)
        board.tiles.values.filterNot { it.resource == null }.forEach { hex ->
            assertTrue("" + hex.coords, hex.number != 0)
            assertTrue(hex.number <= 12)
            assertTrue(hex.number != 7)
            assertTrue(hex.number >= 2)
        }
    }

    @Test fun testBoardEdgesHexSize() {
        val board = StandardBoard()
        assertNotNull(board.allEdges())
        assertTrue(board.allEdges().isNotEmpty())
        board.allEdges().forEach { e ->
            assertNotNull(e)
            assertNotNull(e.hexes)
            assertTrue(e.hexes.isNotEmpty())
            assertTrue(e.hexes.size <= 2)
        }
    }

    @Test fun testGetAdjecentEdgesCount() {
        val board = StandardBoard()
        board.allNodes().forEach { node ->
            node.edges().forEach { edge ->
                assertTrue(edge.getAdjecentEdges().size <= 4)
                assertTrue(edge.getAdjecentEdges().size >= 2)
            }
        }
    }

    // Create a org.jjk3.board and visit roads that touch but have differing colors.
    @Test fun testVisit1() {
        val board = StandardBoard()
        val e1 = board.placeRoad(Road("red"), EdgeCoordinate(0, 2, 2))
        board.placeRoad(Road("red"), EdgeCoordinate(1, 2, 0))
        board.placeRoad(Road("red"), EdgeCoordinate(1, 1, 2))
        assertEquals(e1.getCompleteRoad().size, 3)

        val e4 = board.placeRoad(Road("blue"), EdgeCoordinate(1, 1, 1))
        board.placeRoad(Road("blue"), EdgeCoordinate(1, 1, 0))
        assertEquals(e4.getCompleteRoad().size, 2)

        board.placeRoad(Road("red"), EdgeCoordinate(0, 2, 3))
        board.placeRoad(Road("red"), EdgeCoordinate(0, 2, 4))
        assertEquals(e1.getCompleteRoad().size, 5)
        assertEquals(e4.getCompleteRoad().size, 2)
    }

    // Create a org.jjk3.board and visit the roads of a circle
    @Test fun testVisitRoad() {
        val board = StandardBoard()
        val e5 = board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 0))
        board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 1))
        board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 2))
        board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 3))
        board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 4))
        board.placeRoad(Road("orange"), EdgeCoordinate(0, 0, 5))
        var count = e5.getCompleteRoad().size
        assertEquals(count, 6)
    }

    @Test fun testGetAdjecentNodes2() {
        val board = StandardBoard()
        for (n in board.allNodes()) {
            val x = n.getAdjecentNodes()
            assertTrue(x.size >= 2)
            assertTrue(x.size <= 3)
        }

        assertEquals(2, board.getHex(HexCoordinate(0, 0)).nodes[0].getAdjecentNodes().size)
        assertEquals(3, board.getHex(HexCoordinate(0, 1)).nodes[5].getAdjecentNodes().size)
        assertEquals(3, board.getHex(HexCoordinate(1, 1)).nodes[3].getAdjecentNodes().size)
    }

    @Test fun testNodeHexes() {
        val board = StandardBoard()
        assertEquals(1, board.getNode(NodeCoordinate(0, 0, 0)) !!.hexes.size)
        assertEquals(2, board.getNode(NodeCoordinate(0, 0, 1)) !!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(1, 1, 1)) !!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 0)) !!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 1)) !!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 2)) !!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 3)) !!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 4)) !!.hexes.size)
        assertEquals(3, board.getNode(NodeCoordinate(0, 1, 5)) !!.hexes.size)
    }

    /* Hexes and edges should share the same nodes. */
    @Test fun testSameNodes() {
        var board = StandardBoard()
        assertSame(board.getNode(NodeCoordinate(0, 1, 0)), board.getNode(
                NodeCoordinate(0, 0, 2)))
        assertSame(board.getNode(NodeCoordinate(0, 1, 0)), board.getNode(
                NodeCoordinate(1, 0, 4)))
    }

    @Test fun testGetOppositeHex() {
        assertEquals(HexCoordinate(0, 0), HexCoordinate(0, 1).getOppositeHex(
                EdgeNumber(0)))
    }

    @Test fun testStandardBoard() {
        val board = StandardBoard()
        assertTrue(board.getHex(HexCoordinate(0, 0)).isOnOutside())
        assertFalse(board.getHex(HexCoordinate(0, 1)).isOnOutside())
    }
}