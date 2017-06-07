package org.jjk3.core

import org.junit.Assert.*
import org.junit.Test

class EdgeTest {

    @Test
    fun isOutsideEdge1() {
        assertTrue(Edge().isOutsideEdge())
    }

    @Test
    fun isOutsideEdge2() {
        val edge = Edge()
        edge.hexes += Pair(Hex(null, 5), EdgeNumber(0))
        assertTrue(edge.isOutsideEdge())
    }

    @Test
    fun isOutsideEdge3() {
        val edge = Edge()
        edge.hexes += Pair(Hex(null, 5, HexCoordinate(1, 1)), EdgeNumber(0))
        edge.hexes += Pair(Hex(null, 6, HexCoordinate(1, 2)), EdgeNumber(1))
        assertFalse(edge.isOutsideEdge())
    }

    @Test fun testGetAdjecentEdges() {
        val hex = Hex(null, 5)
        hex.edges.forEach {
            assertEquals(it.getAdjecentEdges().size, 2)
        }
    }
}

