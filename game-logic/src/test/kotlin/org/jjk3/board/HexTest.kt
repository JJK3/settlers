package org.jjk3.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class HexTest {

    @Test
    fun testInit(){
        val hex1 = Hex(null, 5)
        assertTrue(hex1.edges.all { it.hexes.size == 1})
        assertTrue(hex1.edges.all { it.nodes().size == 2})
        assertTrue(hex1.nodes.all { it.hexes.size == 1})
        assertTrue(hex1.nodes.all { it.edges().size == 2})
    }

    @Test
    fun replaceEdge() {
        val hex1 = Hex(null, 5, HexCoordinate(0,0))
        val hex2 = Hex(null, 5, HexCoordinate(0,1))
        assertEquals(12, (hex1.edges + hex2.edges).size)
        assertEquals(12, (hex1.nodes + hex2.nodes).toHashSet().size)

        val oldEdge = hex1.edges.first()
        val edge1Index = hex1.getEdgeIndex(oldEdge)
        val newEdge = hex2.edge(edge1Index.opposite())
        hex1.replaceEdge(oldEdge, newEdge)

        assertEquals(newEdge.hexes.size, 2)
        assertEquals(11, (hex1.edges + hex2.edges).toHashSet().size)
        assertEquals(10, (hex1.nodes + hex2.nodes).toHashSet().size)
        assertTrue(newEdge.nodes().all{it.hexes.size == 2})
        assertEquals(oldEdge.nodes().size, 2)
        assertEquals(newEdge.nodes().size, 2)
        assertEquals(newEdge.getAdjecentEdges().size, 4)
    }

    @Test
    fun get_opposite_hex() {
    }

    @Test
    fun nextEdge() {
    }

}