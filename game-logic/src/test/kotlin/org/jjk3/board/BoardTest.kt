package org.jjk3.board

import com.google.common.base.Equivalence
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.*

class BoardTest {

    lateinit var board: Board

    /**
     * A Mini test board
     *               ____
     *             /    \
     *        ____/ 0,0  \ ___
     *       /    \      /    \
     *      / -1,0 \____/ 1,0 \
     *      \      /    \     /
     *       \____/ 0,1  \___/
     *       /    \      /   \
     *      / -1,1 \____/ 1,1 \
     *      \      /    \     /
     *       \____/ 0,2  \___/
     *            \     /
     *             \___/
     */
    open class MiniBoard : Board() {
        init {
            val random = Random()
            val coords = listOf(Pair(- 1, 0), Pair(- 1, 1), Pair(0, 0), Pair(0, 1), Pair(0, 2), Pair(1, 0), Pair(1, 1))
            val desert = coords.pickRandom()
            for (c in coords) {
                val resource = if (c == desert) null else Resource.values().toList().pickRandom()
                val hex = Hex(resource, random.nextInt(5) + 1)
                hex.coords = HexCoordinate(c.first, c.second)
                addHex(hex)
            }
        }
    }

    private fun edge(x: Int, y: Int, e: Int) = board.getEdge(EdgeCoordinate(x, y, e))
    @Before
    fun setup() {
        board = MiniBoard()
    }

    fun checkOpposingEdges(board: Board) {
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

    @Test
    fun testMiniboard() {
        assertEquals(board.getHex(HexCoordinate(0, 0)).edges[3].coords(),
                board.getHex(HexCoordinate(0, 1)).edges[0].coords())
        assertEquals(board.getHex(HexCoordinate(0, 0)).edges[4].coords(),
                board.getHex(HexCoordinate(- 1, 0)).edges[1].coords())
        checkOpposingEdges(board)
    }

    private fun assertNodeCount(b: Board, expectedNodeCount: Int) {
        val nodes = b.tiles.values.flatMap { it.nodes.toList() }
        val identity = Equivalence.identity()
        assertEquals(nodes.map { identity.wrap(it) }.toSet().size, expectedNodeCount)
    }

    private fun assertEdgeCount(b: Board, expectedEdgeCount: Int) {
        val edges = b.tiles.values.flatMap { it.edges.toList() }
        val identity = Equivalence.identity()
        assertEquals(edges.map { identity.wrap(it) }.toSet().size, expectedEdgeCount)
    }

    @Test
    fun allNodes() {
        assertNodeCount(board, 24)
    }

    @Test
    fun allEdges() {
        assertEdgeCount(board, 30)
    }

    @Test
    fun copy() {
        val copy = board.copy()
        assertNotSame(copy, board)
        for (edge1 in board.allEdges()) {
            for (edge2 in copy.allEdges()) {
                assertNotSame(edge1, edge2)
            }
        }
        for (node1 in board.allNodes()) {
            for (node2 in copy.allNodes()) {
                assertNotSame(node1, node2)
            }
        }
        assertEquals(7, copy.tiles.size)
        assertEdgeCount(copy, 30)
        assertNodeCount(copy, 24)
    }

    @Test
    fun getHex() {
        board.getHex(HexCoordinate(0, 0))
    }

    @Test
    fun getHexOrNull() {
        assertNotNull(board.getHexOrNull(HexCoordinate(0, 0)))
        assertNull(board.getHexOrNull(HexCoordinate(1, 120)))
    }

    @Test
    fun getNode() {
        assertSame(board.getNode(NodeCoordinate(0, 1, 0)), board.getNode(NodeCoordinate(0, 0, 2)))
        assertSame(board.getNode(NodeCoordinate(0, 1, 3)), board.getNode(NodeCoordinate(- 1, 1, 1)))
    }

    @Test
    fun getEdge() {
        assertSame(board.getEdge(EdgeCoordinate(0, 1, 0)), board.getEdge(EdgeCoordinate(0, 0, 3)))
        assertSame(board.getEdge(EdgeCoordinate(0, 1, 4)), board.getEdge(EdgeCoordinate(- 1, 1, 1)))
    }

    @Test
    fun getAllRoadGroups() {
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 0))
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 1))
        board.placeRoad(Road("blue"), EdgeCoordinate(0, 0, 2))
        board.placeRoad(Road("blue"), EdgeCoordinate(0, 0, 3))
        board.placeRoad(Road("blue"), EdgeCoordinate(0, 2, 3))
        val roadGroups = board.getAllRoadGroups()
        assertEquals(roadGroups.size, 3)
        assertTrue(roadGroups.contains(setOf(edge(0, 0, 0), edge(0, 0, 1))))
        assertTrue(roadGroups.contains(setOf(edge(0, 0, 2), edge(0, 0, 3))))
        assertTrue(roadGroups.contains(setOf(edge(0, 2, 3))))
    }

    @Test
    fun hasLongestRoad() {
        addLongestRoad()
        assertTrue(board.hasLongestRoad("red"))
        assertFalse(board.hasLongestRoad("blue"))
    }

    @Test
    fun hasLongestRoadContested() {
        addLongestRoad()
        board.placeRoad(Road("blue"), EdgeCoordinate(1, 1, 4))
        assertFalse(board.hasLongestRoad("red"))
        assertFalse(board.hasLongestRoad("blue"))
    }

    private fun addLongestRoad() {
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 0))
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 1))
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 2))
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 3))
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 4))

        board.placeRoad(Road("blue"), EdgeCoordinate(1, 1, 0))
        board.placeRoad(Road("blue"), EdgeCoordinate(1, 1, 1))
        board.placeRoad(Road("blue"), EdgeCoordinate(1, 1, 2))
        board.placeRoad(Road("blue"), EdgeCoordinate(1, 1, 3))
    }

    @Test
    fun getLongestRoad() {
        addLongestRoad()
        assertEquals(board.getLongestRoad(edge(0, 0, 0)),
                listOf(edge(0, 0, 0), edge(0, 0, 1), edge(0, 0, 2), edge(0, 0, 3), edge(0, 0, 4)))
        assertEquals(board.getLongestRoad(edge(1, 1, 0)),
                listOf(edge(1, 1, 0), edge(1, 1, 1), edge(1, 1, 2), edge(1, 1, 3)))
    }

    @Test
    fun getLongestRoadWithLoop() {
        addLongestRoad()
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 5))
        assertEquals(board.getLongestRoad(edge(0, 0, 0)).size, 6)
        board.placeRoad(Road("red"), EdgeCoordinate(- 1, 0, 0))
        assertEquals(board.getLongestRoad(edge(0, 0, 0)).size, 7)
        assertEquals(board.getLongestRoad(edge(0, 0, 0)).toSet(),
                setOf(edge(0, 0, 0), edge(0, 0, 1), edge(0, 0, 2), edge(0, 0, 3), edge(0, 0, 4), edge(0, 0, 5),
                        edge(- 1, 0, 0)))
    }

    @Test
    fun getLongestRoadWithLoop2() {
        addLongestRoad()
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 5))
        board.placeRoad(Road("red"), EdgeCoordinate(- 1, 0, 0))
        board.placeRoad(Road("red"), EdgeCoordinate(1, 0, 0))
        board.placeRoad(Road("red"), EdgeCoordinate(1, 0, 1))
        assertEquals(board.getLongestRoad(edge(0, 0, 0)).size, 8)
    }

    @Test
    fun portNodes() {
        val coord = NodeCoordinate(0, 0, 0)
        val node = board.getNode(coord)
        node.port = Port(Resource.Brick, 2)
        board.placeCity(Settlement("red"), coord)
        assertEquals(board.portNodes("red"), listOf(board.getNode(coord)))
    }

    @Test
    fun getPorts() {
        assertTrue(board.getPorts("red").isEmpty())
        val coords = NodeCoordinate(0, 0, 0)
        val port = Port(Resource.Brick, 2)
        board.getNode(coords).port = port
        assertTrue(board.getPorts("red").isEmpty())
        board.placeCity(Settlement("red"), coords)
        assertEquals(board.getPorts("red"), listOf(port))
    }

    @Test
    fun getCards() {
        board.tiles.values.forEach { it.number = 12 }

        val tile = board.tiles.values.filter { it.resource != null }.first()
        tile.hasBandit = false
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

    /** Test that cities and settlements produce the right cards */
    @Test
    fun getCardsWithBandit() {
        val tile = board.getHex(HexCoordinate(0, 0))
        tile.hasBandit = true
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

    @Test
    fun placeRoad() {
        assertEquals(board.getAllRoadGroups(), emptyList<Set<Edge>>())
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 0))
        assertEquals(board.getAllRoadGroups(), listOf(setOf(edge(0, 0, 0))))
    }

    @Test
    fun removeRoad() {
        assertEquals(board.getAllRoadGroups(), emptyList<Set<Edge>>())
        val coord = EdgeCoordinate(0, 0, 0)
        board.placeRoad(Road("red"), coord)
        assertEquals(board.getAllRoadGroups(), listOf(setOf(edge(0, 0, 0))))
        board.removeRoad(coord)
        assertEquals(board.getAllRoadGroups(), emptyList<Set<Edge>>())
    }

    @Test
    fun placeCity() {
        fun countCities() = board.allNodes().filter(Node::hasCity).size
        assertEquals(countCities(), 0)
        board.placeCity(Settlement("red"), NodeCoordinate(0, 0, 0))
        assertEquals(countCities(), 1)
    }

    @Test
    fun placeBanditOnDesert() {
        assertNull(board.findBandit())
        board.placeBanditOnDesert()
        assertNotNull(board.findBandit())
    }

    @Test
    fun findBandit() {
        assertNull(board.findBandit())
        val hex = board.getHex(HexCoordinate(0, 0))
        hex.hasBandit = true
        assertEquals(board.findBandit(), hex)
    }

    @Test(expected = RuleException::class)
    fun moveBandit1() {
        board.moveBandit(HexCoordinate(0, 0))
    }

    @Test
    fun findDesert() {
        assertNotNull(board.findDesert())
    }

    @Test
    fun moveBandit2() {
        board.placeBanditOnDesert()
        val anotherHex = board.tiles.values.filter { it.resource != null }.pickRandom()
        board.moveBandit(anotherHex.coords)
        assertNotNull(board.findBandit())
        assertNotEquals(board.findBandit(), board.findDesert())
    }

    @Test
    fun getValidSettlementSpots() {
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

    @Test
    fun getValidSettlementSpots1() {
        assertEquals(board.getValidSettlementSpots(), board.allNodes())
    }

    @Test
    fun getValidRoadSpots1() {
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

    @Test
    fun getValidRoadSpots2() {
        assertEquals(board.getValidRoadSpots("red").size, 0)
        val settlementNode = NodeCoordinate(0, 0, 0)
        board.placeCity(Settlement("red"), settlementNode)
        assertEquals(board.getValidRoadSpots("red", settlementNode).size, 2)
        val settlementNode2 = NodeCoordinate(0, 1, 4)
        board.placeCity(Settlement("red"), settlementNode2)
        assertEquals(board.getValidRoadSpots("red", settlementNode2).size, 3)
    }

    /** Test that if you have 2 settlements connected with 1 road, You're still allowed to build other roads */
    @Test
    fun testValidRoadSpotsAndSettlements() {
        board.placeCity(Settlement("red"), NodeCoordinate(0, 0, 0))
        board.placeRoad(Road("red"), EdgeCoordinate(0, 0, 1))
        board.placeRoad(Road("red"), EdgeCoordinate(1, 0, 0))
        board.placeRoad(Road("blue"), EdgeCoordinate(0, 0, 2))
        board.placeRoad(Road("blue"), EdgeCoordinate(0, 0, 0))
        assertEquals(board.getValidRoadSpots("red").size, 1)
        board.placeCity(Settlement("red"), NodeCoordinate(1, 0, 0))
        assertEquals(board.getValidRoadSpots("red").size, 1)
    }

    @Test
    fun getValidCitySpots() {
        assertTrue(board.getValidCitySpots("red").isEmpty())
        board.placeCity(Settlement("red"), NodeCoordinate(0, 0, 0))
        assertEquals(board.getValidCitySpots("red"), listOf(board.getNode(NodeCoordinate(0, 0, 0))))
    }

    @Test
    fun addHex() {
        val b = Board()
        b.addHex(Hex(Resource.Brick, 5, HexCoordinate(0, 0)))
        b.addHex(Hex(Resource.Brick, 6, HexCoordinate(1, 0)))
        assertNodeCount(b, 10)
        assertEdgeCount(b, 11)
    }

    @Test
    fun addHex2() {
        val b = Board()
        b.addHex(Hex(Resource.Brick, 5, HexCoordinate(0, 0)))
        b.addHex(Hex(Resource.Brick, 6, HexCoordinate(0, 1)))
        assertNodeCount(b, 10)
        assertEdgeCount(b, 11)
    }

    @Test(expected = IllegalArgumentException::class)
    fun addHexNegative() {
        val b = Board()
        b.addHex(Hex(Resource.Brick, 5, HexCoordinate(0, 0)))
        b.addHex(Hex(Resource.Brick, 6, HexCoordinate(0, 0)))
    }

}
