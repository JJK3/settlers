package core

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeNumberTest {

    @Test
    operator fun next() {
        assertEquals(EdgeNumber(1).next(), EdgeNumber(2))
        assertEquals(EdgeNumber(5).next(), EdgeNumber(0))
    }

    @Test
    fun prev() {
        assertEquals(EdgeNumber(0).prev(), EdgeNumber(5))
        assertEquals(EdgeNumber(5).prev(), EdgeNumber(4))
    }

    @Test
    fun opposite() {
        assertEquals(EdgeNumber(0).opposite(), EdgeNumber(3))
        assertEquals(EdgeNumber(5).opposite(), EdgeNumber(2))
    }

    @Test
    fun prevNode() {
        assertEquals(EdgeNumber(0).prevNode(), NodeNumber(5))
    }

    @Test
    fun nextNode() {
        assertEquals(EdgeNumber(0).nextNode(), NodeNumber(0))
        assertEquals(EdgeNumber(0).nextNode(), NodeNumber(0))
    }

}