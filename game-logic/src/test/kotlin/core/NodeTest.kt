package core

import org.junit.Test

import org.junit.Assert.*

/**
 * Copyright (c) 2017 Ovitas Inc, All rights reserved.
 */
class NodeTest {

    @Test fun testGetAdjecentNodes() {
        val hex = Hex(null, 6)
        val n5 = hex.node(NodeNumber(5))
        val n0 = hex.node(NodeNumber(0))
        val n1 = hex.node(NodeNumber(1))
        assertTrue(n5.get_adjecent_nodes().contains(n0))
        assertTrue(n0.get_adjecent_nodes().contains(n1))
        assertTrue((!n5.get_adjecent_nodes().contains(n1)))
    }

}