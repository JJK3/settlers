package org.jjk3.board

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.*

class SpiralIteratorTest {

    val first = HexCoordinate(0, 0)
    val second = HexCoordinate(0, 1)
    val third = HexCoordinate(- 1, 1)
    val fourth = HexCoordinate(- 1, 0)
    lateinit var board: Board
    lateinit var spiral: SpiralIterator

    open class TinyBoard : Board() {
        init {
            val random = Random()
            val coords = listOf(Pair(- 1, 0), Pair(- 1, 1), Pair(0, 0), Pair(0, 1))
            val desert = coords.pickRandom()
            for (c in coords) {
                val resource = if (c == desert) null else Resource.values().toList().pickRandom()
                val hex = Hex(resource, random.nextInt(5) + 1)
                hex.coords = HexCoordinate(c.first, c.second)
                addHex(hex)
            }
        }
    }

    @Before
    fun setup() {
        board = TinyBoard()
        spiral = SpiralIterator(board)
        spiral.startingTile = board.getHex(first)
    }

    @Test
    fun getHexes() {
        assertEquals(spiral.getHexes().map { it.coords }, listOf(first, second, third, fourth))
    }

    @Test
    fun getClockwiseConnectingEdge() {
    }

    @Test
    fun getNextClockwiseEdge() {
        val hex1 = board.getHex(first)
        val top = hex1.edge(EdgeNumber(0))
        val expected = hex1.edge(EdgeNumber(3))
        assertEquals(spiral.getNextClockwiseEdge(hex1, top), expected)
    }

    @Test
    fun getNextNewTile() {
        val hex1 = board.getHex(first)
        val hex2 = board.getHex(second)
        val top = hex1.edge(EdgeNumber(0))
        val secondTop = hex2.edge(EdgeNumber(0))
        assertEquals(spiral.getNextNewTile(hex1, top), Pair(hex2, secondTop))
    }

    @Test
    fun getNextNewTile2() {
        val hex2 = board.getHex(second)
        val hex3 = board.getHex(third)
        val top = hex2.edge(EdgeNumber(0))
        val thirdTopRight = hex3.edge(EdgeNumber(1))
        assertEquals(spiral.getNextNewTile(hex2, top), Pair(hex3, thirdTopRight))
    }

    @Test
    fun testMediumBoard() {
        val b = MediumBoard()
        val spiralIterator = SpiralIterator(b)
        spiralIterator.startingTile = b.getHex(HexCoordinate(0, 0))
        val hexes = spiralIterator.getHexes()
        assertEquals(MediumBoard.coords, hexes.map { it.coords })
    }

    class MediumBoard : Board() {
        companion object {
            var coords = listOf(HexCoordinate(0, 0), HexCoordinate(1, 0), HexCoordinate(1, 1), HexCoordinate(0, 2),
                    HexCoordinate(- 1, 1), HexCoordinate(- 1, 0), HexCoordinate(0, 1))
        }

        init {
            var tileBag = TileBag.newBag()
            for (c in coords) {
                val (newBag, hex) = tileBag.removeRandom()
                tileBag = newBag
                hex.coords = c
                addHex(hex)
            }
        }
    }
}
