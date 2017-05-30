package core

import board.TileBag
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test


/**
 * Copyright (c) 2017 Ovitas Inc, All rights reserved.
 */
class SpiralIteratorTest {

    val first = HexCoordinate(0, 0)
    val second = HexCoordinate(0, 1)
    val third = HexCoordinate(-1, 1)
    val fourth = HexCoordinate(-1, 0)
    lateinit var board: Board
    lateinit var spiral: SpiralIterator
    @Before
    fun setup() {
        board = MiniBoard()
        spiral = SpiralIterator(board)
        spiral.startingTile = board.getTile(first)!!
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
        val hex1 = board.getTile(first)!!
        val top = hex1.edge(EdgeNumber(0))
        val expected = hex1.edge(EdgeNumber(3))
        assertEquals(spiral.getNextClockwiseEdge(hex1, top), expected)
    }

    @Test
    fun getNextNewTile() {
        val hex1 = board.getTile(first)!!
        val hex2 = board.getTile(second)!!
        val top = hex1.edge(EdgeNumber(0))
        val secondTop = hex2.edge(EdgeNumber(0))
        assertEquals(spiral.getNextNewTile(hex1, top), Pair(hex2, secondTop))
    }

    @Test
    fun getNextNewTile2() {
        val hex2 = board.getTile(second)!!
        val hex3 = board.getTile(third)!!
        val top = hex2.edge(EdgeNumber(0))
        val thirdTopRight = hex3.edge(EdgeNumber(1))
        assertEquals(spiral.getNextNewTile(hex2, top), Pair(hex3, thirdTopRight))
    }

    @Test
    fun testMediumBoard() {
        val b = MediumBoard()
        val spiralIterator = SpiralIterator(b)
        spiralIterator.startingTile = b.getTile(0, 0)!!
        val hexes = spiralIterator.getHexes()
        assertEquals(MediumBoard.coords, hexes.map { it.coords })
    }

}

class MediumBoard : Board() {
    companion object {
        var coords = listOf(HexCoordinate(0, 0), HexCoordinate(1, 0), HexCoordinate(1, 1), HexCoordinate(0, 2),
                HexCoordinate(-1, 1), HexCoordinate(-1, 0), HexCoordinate(0, 1))
    }

    init {
        val tileBag = TileBag()
        for (c in coords) {
            val hex = tileBag.grab()
            hex.coords = c
            add_hex(hex)
        }
    }
}