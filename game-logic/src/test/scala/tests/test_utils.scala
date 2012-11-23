import org.junit.Assert._

object AssertionUtils{
    def assertRaises(expectedException: Class[_ <: Exception])(block: () => Any) = {
        try {
            block()
            fail("Exception was expected but never thrown:" + expectedException)
        } catch {
            case expectedException => // The expected exception happened
            case err: Exception => fail("An unexpected exception was thrown: " + err)
        }
    }
}