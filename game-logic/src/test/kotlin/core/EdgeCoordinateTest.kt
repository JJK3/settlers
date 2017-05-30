package core

import org.junit.Test

import org.junit.Assert.*

class EdgeCoordinateTest {

    @Test
    fun equivalent() {
        assertTrue(EdgeCoordinate(0, 1, 0).equivalent(EdgeCoordinate(0, 0, 3)))
    }

}