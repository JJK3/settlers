package org.jjk3.board

import org.junit.Test

/**
 * Copyright (c) 2017 Ovitas Inc, All rights reserved.
 */
class TileBagTest {

    @Test fun testGetHex() {
        var bag = TileBag.newBag()
        for (i in 1..19) {
            val (newBag, _) = bag.removeRandom()
            bag = newBag
        }
    }

    @Test fun testGetHex2() {
        var bag = TileBag.newBag()
        for (i in 1..19) {
            val (newBag, _) = bag.removeRandom()
            bag = newBag
        }
    }
}