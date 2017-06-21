package org.jjk3.board

import org.junit.Test

import org.junit.Assert.*

class NodeTest {

    @Test fun testGetAdjecentNodes() {
        val hex = Hex(null, 6)
        val n5 = hex.node(NodeNumber(5))
        val n0 = hex.node(NodeNumber(0))
        val n1 = hex.node(NodeNumber(1))
        assertTrue(n5.getAdjecentNodes().contains(n0))
        assertTrue(n0.getAdjecentNodes().contains(n1))
        assertTrue((!n5.getAdjecentNodes().contains(n1)))
    }

}