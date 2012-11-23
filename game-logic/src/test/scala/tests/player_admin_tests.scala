import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.AssertionsForJUnit
import scala.collection.mutable.ListBuffer
import org.junit.Assert._
import org.junit.Test
import org.junit.Before
import org.apache.log4j._
import core._
import board._

class AdminTest extends AssertionsForJUnit {
    var board: Board = null
    var admin: MockAdmin = null
    var p1: MockPlayer = null
    var turn: Turn = null
    var log: Logger = Logger.getLogger(classOf[AdminTest])

    @Before def setup = {
        board = new StandardBoard()
        admin = new MockAdmin(board, 4)
        p1 = new MockPlayer("test player", "", admin, log, 5, 5, 4, board)
        p1.update_board(board)
        turn = new Turn(admin, p1, board, log)
        admin.set_current_turn(turn)
    }

    /* Roll the dice, make sure that they have valid numbers */
    @Test def test_roll_dice1 = {
        (0 to 100).foreach { x =>
            val roll = admin.roll_dice
            assertTrue("die roll <= 6", roll._1 <= 6)
            assertTrue("die roll >= 1", roll._1 >= 1)
            assertTrue("die roll <= 6", roll._2 <= 6)
            assertTrue("die roll >= 1", roll._2 >= 1)
        }
    }

    /* register some players */
    @Test def test_register_1 = {
        p1 = new MockPlayer("Player1", "", admin, log, 5, 5, 4, board)
        var p2 = new MockPlayer("Player2", "", admin, log, 5, 5, 4, board)
        admin.register(p1, p2)
        assertEquals(2, admin.players.size)
    }

    /* register some players */
    @Test def test_register_2 = {
        p1 = new MockPlayer("Player1", "", admin, log, 5, 5, 4, board)
        var p2 = new MockPlayer("Player2", "", admin, log, 5, 5, 4, board)
        admin.register(p1)
        admin.register(p2)
        assertEquals(2, admin.players.size)
    }
}

class PlayerTest extends AssertionsForJUnit {
    var log: Logger = Logger.getLogger(classOf[PlayerTest])

    @Test def test_add_cards = {
        val p = new MockPlayer("P1", "", null, log, 5, 5, 4, null)
        p.add_cards(List(OreType, OreType, OreType, WheatType))
        assertEquals(3, p.get_cards(OreType))
        assertEquals(1, p.get_cards(WheatType))
    }

    @Test def test_del_cards = {
        val p = new MockPlayer("P1", "", null, log, 5, 5, 4, null)
        p.add_cards(List(OreType, OreType, OreType, WheatType))
        AssertionUtils.assertRaises(classOf[RuleException]) { () =>
            p.del_cards(List(OreType, OreType, OreType, WheatType, WheatType), 1)
        }
        assertEquals(3, p.get_cards(OreType))
        assertEquals(1, p.get_cards(WheatType))
        p.del_cards(List(OreType, OreType, OreType, WheatType), 1)
        assertEquals(0, p.get_cards(OreType))
        assertEquals(0, p.get_cards(WheatType))
    }
}