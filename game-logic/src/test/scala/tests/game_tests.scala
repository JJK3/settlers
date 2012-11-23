import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.AssertionsForJUnit
import scala.collection.mutable.ListBuffer
import org.junit.Assert._
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.apache.log4j._

import core._
import board._

/* Some basic helper methods for the integration tests. */
trait IntegrationTest {
    var a: MockAdmin = null
    var b: Board = null
    var p1: MockPlayer = null
    var p2: MockPlayer = null
    var log: Logger = Logger.getLogger(classOf[IntegrationTest])
    var lastTurn: Turn = null

    @Before def setup = {
        b = new StandardBoard()
        a = new MockAdmin(b, 4)
        p1 = new MockPlayer("Player1", "", a, log, 5, 5, 4, b)
        p2 = new MockPlayer("Player2", "", a, log, 5, 5, 4, b)
        a.register(p1, p2)
    }

    @After
    def after_test = {
        a.set_state(GameState.Finished)
        a.shutdown()
    }

    // Creates a basic board w/ 2 players
    // each player places 2 roads and 2 settlements
    def use_test_board = {
        a.make_rich(p1)
        a.make_rich(p2)
        a.start_game
        //Setup
        p1.on_next_turn { (turn: Turn) =>
            turn.place_settlement(0, 0, 0)
            turn.place_road(0, 0, 0)
        }.get
        // Setup
        p2.on_next_turn { (turn: Turn) =>
            assertTrue(turn.isInstanceOf[SetupTurn])
            turn.place_settlement(-1, 0, 0)
            turn.place_road(-1, 0, 0)
        }.get
        p2.on_next_turn { (turn: Turn) =>
            assertTrue(turn.isInstanceOf[SetupTurn])
            turn.place_settlement(-1, 1, 0)
            turn.place_road(-1, 1, 0)
        }.get
        p1.on_next_turn { (turn: Turn) =>
            assertTrue(turn.isInstanceOf[SetupTurn])
            turn.place_settlement(1, 0, 0)
            turn.place_road(1, 0, 0)
        }.get
    }

}

class GameTest extends AssertionsForJUnit with IntegrationTest {

    /** Test that the game begins correctly */
    @Test def test_game_start: Unit = {
        a.start_game
        Util.while_with_timeout(3000) { () => a.current_turn == null || a.current_turn != p1.get_current_turn }
        assertTrue(p1.get_current_turn.isInstanceOf[SetupTurn])
    }

    /** Test that each player picks up cards for their 2nd settlement */
    @Test def test_game_start_pickup_cards: Unit = {
        a.start_game
        //Setup
        p1.on_next_turn { turn: Turn =>
            turn.place_settlement(0, 0, 0)
            turn.place_road(0, 0, 0)
        }.get
        //Setup
        p2.on_next_turn { (turn: Turn) =>
            turn.place_settlement(-1, 0, 0)
            turn.place_road(-1, 0, 0)
        }.get
        var p2_resource_hash = Map[HexType, Int]().withDefaultValue(0)
        p2.on_next_turn { (turn: Turn) =>
            turn.place_settlement(-1, 2, 0)
            turn.place_road(-1, 2, 0)
            val p2_resources = b.getNode(-1, 2, 0).hexes.map { _.card_type }
            p2_resources.foreach { r =>
                if (r != DesertType) p2_resource_hash += r -> (p2_resource_hash(r) + 1)
            }
        }.get

        var p1_resource_hash = Map[HexType, Int]().withDefaultValue(0)
        var done = false
        p1.on_next_turn { (turn: Turn) =>
            turn.place_settlement(1, 2, 0)
            turn.place_road(1, 2, 0)
            val p1_resources = b.getNode(1, 2, 0).hexes.map { _.card_type }
            p1_resources.foreach { r =>
                if (r != DesertType)
                    p1_resource_hash += r -> (p1_resource_hash(r) + 1)
            }
            done = true
        }.get

        assertEquals(p1_resource_hash, p1.get_cards)
        assertEquals(p2_resource_hash, p2.get_cards)
    }

    /** Test that setup turns DO NOT allow normal turn actions */
    @Test def test_setup_turn: Unit = {
        a.start_game
        p1.on_next_turn { (turn: Turn) =>
            AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.roll_dice }
            AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.buy_development_card }
            AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.place_city(0, 0, 0) }
        }.get
    }

    /**
     * Test that setup turns force you to add pieces.
     * An error should occur if a user does not place a settlement
     * AND a road on his turn
     */
    @Test def test_setup_turn__premature_end1: Unit = {
        a.start_game
        p1.on_next_turn { (turn: Turn) =>
            AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.done }
        }.get
    }

    @Test def test_setup_turn__premature_end2: Unit = {
        a.start_game
        p1.on_next_turn { (turn: Turn) =>
            turn.place_settlement(0, 0, 0)
            AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.done }
        }.get
    }

    /** Users should not be allowed to ask for quotes for cards that they don't have */
    @Test def test_bad_get_quotes: Unit = {
        use_test_board
        p1.on_next_turn { (turn: Turn) =>
            turn.roll_dice
            a.set_cards(p1, Map(OreType -> 0, WheatType -> 0))
            AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.get_quotes(List(OreType), List(WheatType)) }
        }.get
    }

    /** Roll the dice, and collect some cards for a settlement. */
    @Test def test_get_cards_1: Unit = {
        use_test_board
        b.tiles.values.foreach { _.number = 1 }
        val prev_ore_cards = p1.get_cards(OreType)
        b.getTile(0, 0).card_type = OreType
        b.getTile(0, 0).has_bandit = false
        b.getTile(0, 0).number = 10
        p1.on_next_turn { turn =>
            a.should_roll = (5, 5)
            turn.roll_dice
        }.get
        assertEquals(prev_ore_cards + 1, p1.get_cards(OreType))
    }

    @Test def test_place_pieces: Unit = {
        use_test_board
        p1.on_next_turn { (turn: Turn) =>
            turn.roll_dice
            AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.place_road(0, 0, 0) }
            AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.place_city(0, 0, 1) }
            AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.place_settlement(0, 0, 0) }
        }.get
        p2.on_next_turn { (turn: Turn) =>
            a.make_rich(p2)
            turn.roll_dice
            turn.place_road(-1, 0, 5)
            turn.place_road(-1, 0, 4)
            turn.place_settlement(-1, 0, 4)
            turn.place_city(-1, 0, 4)
        }.get
    }

    /** Roll the dice, and collect some cards for a settlement. */
    @Test def test_get_cards_with_bandit: Unit = {
        use_test_board
        b.tiles.values.foreach { _.number = 1 }
        b.getTile(0, 0).card_type = OreType
        b.getTile(0, 0).has_bandit = true
        b.getTile(0, 0).number = 10

        p1.on_next_turn { (turn: Turn) =>
            val prev_ore_cards = p1.get_cards(OreType)
            a.should_roll = (5, 5)
            turn.roll_dice
            assertEquals(prev_ore_cards, p1.get_cards(OreType))
        }.get
    }

    /**
     * Roll the dice, and collect some cards for a city.
     * TODO: figure out why this breaks once in a while.
     */
    @Test def test_get_cards_2: Unit = {
        use_test_board
        b.tiles.values.foreach { _.number = 1 }
        b.getTile(0, 0).card_type = OreType
        b.getTile(0, 0).has_bandit = false
        b.getTile(0, 0).number = 10

        p1.on_next_turn { (turn: Turn) =>
            a.should_roll = (1, 1) //don't roll the ore
            turn.roll_dice
            turn.place_city(0, 0, 0)
        }.get
        p2.on_next_turn { (turn: Turn) =>
            a.should_roll = (1, 1) //don't roll the ore
            turn.roll_dice
        }.get
        p1.on_next_turn { (turn: Turn) =>
            //      p1.reset_counter(:add_cards)
            val prev_ore_cards = p1.get_cards(OreType)
            a.should_roll = (5, 5) //ROLL the ore
            turn.roll_dice
            assertEquals(prev_ore_cards + 2, p1.get_cards(OreType))
            ///assertEquals(1, p1.times_called(:add_cards))
        }.get
    }

    /** check that the bandit moves after a 7 is rolled */
    @Test def test_roll7_move_bandit: Unit = {
        use_test_board
        p1.on_next_turn { (turn: Turn) =>
            val bandit_hex = b.tiles.values.find { _.has_bandit }.getOrElse(null)
            a.should_roll = (3, 4)
            turn.roll_dice
            val new_bandit_hex = b.tiles.values.find { _.has_bandit }.getOrElse(null)
            assertTrue(!new_bandit_hex.equals(bandit_hex))
        }.get
    }

    /** check that you cannot move the bandit to the hex it's already on */
    @Test def test_roll7_move_bandit_wrong: Unit = {
        use_test_board
        p1.on_next_turn { (turn: Turn) =>
            val bandit_hex = b.tiles.values.find { _.has_bandit }.getOrElse(null)
            a.should_roll = (3, 4)
            p1.move_bandit_to = bandit_hex
            //The player will try to place the bandit on the old hex
            AssertionUtils.assertRaises(classOf[RuleException]) { () =>
                turn.roll_dice
            }
        }.get
    }

    /** check that everyone loses 1/2 their cards if a 7 is rolled */
    @Test def test_roll7_lose_cards: Unit = {
        use_test_board
        p1.on_next_turn { (turn: Turn) =>
            val p1_resource_cards = p1.count_resources
            val p2_resource_cards = p2.count_resources

            a.should_roll = (3, 4)
            turn.roll_dice
            val p1_resource_cards_now = p1.count_resources
            val p2_resource_cards_now = p2.count_resources

            assertEquals(p1_resource_cards - (p1_resource_cards / 2), p1_resource_cards_now)
            assertEquals(p2_resource_cards - (p2_resource_cards / 2), p2_resource_cards_now)
        }.get
    }

    /** check that you don't lose cards if you have 7 or less */
    @Test def test_roll7_lose_cards2: Unit = {
        use_test_board
        a.set_cards(p1, Map(WheatType -> 0, BrickType -> 0, WoodType -> 0, OreType -> 0, SheepType -> 8))
        a.set_cards(p2, Map(WheatType -> 0, BrickType -> 7, WoodType -> 0, OreType -> 0, SheepType -> 0))

        p1.on_next_turn { (turn: Turn) =>
            a.should_roll = (3, 4)
            turn.roll_dice
            val p1_total_resource_cards = p1.count_resources
            val p2_total_resource_cards = p2.count_resources

            assertEquals(4, p1_total_resource_cards)
            assertEquals(7, p2_total_resource_cards)
        }.get
    }

    /** roll a 7 and only discard 2 cards */
    @Test def test_roll7_cheat1: Unit = {
        use_test_board
        a.set_cards(p1, Map(SheepType -> 8))
        a.set_cards(p2, Map(BrickType -> 7))

        p1.on_next_turn { (turn: Turn) =>
            a.should_roll = (3, 4)
            p1.select_resources_num = 2 //only discard 2 cards
            turn.roll_dice
            assertTrue(a.kicked_out.contains(a.get_player(p1.color)))
        }.get
    }

    /** Roll a 7 and try to discard a LOT of resources */
    @Test def test_roll7_cheat2: Unit = {
        use_test_board
        a.set_cards(p1, Map(SheepType -> 8))
        a.set_cards(p2, Map(BrickType -> 7))

        p1.on_next_turn { (turn: Turn) =>
            a.should_roll = (3, 4)
            p1.select_resources_num = 8
            turn.roll_dice
            assert(a.kicked_out.contains(a.get_player(p1.color)))
        }.get
    }

    /** Roll a 7 and try to discard nil cards */
    @Test def test_roll7_cheat3: Unit = {
        use_test_board
        p1.on_next_turn { (turn: Turn) =>
            a.should_roll = (3, 4)
            p1.select_nil_resources = true
            turn.roll_dice
            assertTrue(a.kicked_out.contains(a.get_player(p1.color)))
        }.get
    }

    @Test def test_play_soldier: Unit = {
        use_test_board
        a.get_player(p1.color).add_cards(Nil.padTo(10, new SoldierCard()))
        a.get_player(p2.color).add_cards(Nil.padTo(10, new SoldierCard()))
        val p1_card_count = p1.count_resources
        val p2_card_count = p2.count_resources
        assertTrue("Has no soldier cards:" + p1.get_cards, p1.get_cards(new SoldierCard()) > 0)
        val f = p1.on_next_turn { (turn: Turn) =>
            a.should_roll = (0, 0)
            turn.roll_dice
            val new_tile = b.tiles.values.find { t =>
                //select a tile that doesn't have the bandit and p2 has a city/settlement on
                val has_p2_city = t.nodes.exists { n => n.has_city && n.city.color == p2.color }
                val has_p1_city = t.nodes.exists { n => n.has_city && n.city.color == p1.color }
                !t.has_bandit && has_p2_city && !has_p1_city
            }.getOrElse(null)
            p1.move_bandit_to = new_tile
            turn.play_development_card(new SoldierCard())
            val bandit_loc = b.tiles.values.find { _.has_bandit }.getOrElse(null)
            assertEquals(new_tile, bandit_loc)
        }
        f.get()
        val new_p1_card_count = p1.count_resources
        val new_p2_card_count = p2.count_resources
        //assert that you took a card
        assertEquals(p2_card_count - 1, new_p2_card_count)
        assertEquals(p1_card_count + 1, new_p1_card_count)
    }

    @Test def test_has_largest_army = {
        use_test_board
        a.get_player(p1.color).add_cards(Nil.padTo(10, new SoldierCard()))
        a.get_player(p2.color).add_cards(Nil.padTo(10, new SoldierCard()))

        //play 2 soldier cards
        val play_cards = { (player: MockPlayer, num_cards: Int) =>
            player.on_next_turn { (turn: Turn) =>
                turn.roll_dice
                (1 to num_cards).foreach { i =>
                    turn.play_development_card(new SoldierCard())
                    //move the bandit
                    val new_tile = b.tiles.values.find { !_.has_bandit }.getOrElse(null)
                    turn.move_bandit(new_tile)
                }
            }.get
        }

        play_cards(p1, 2)
        assertEquals(2, a.get_score(p1))
        assertEquals(2, a.get_score(p2))
        assertEquals(null, a.who_has_largest_army)
        play_cards(p2, 3)
        assertEquals(p2.info, a.who_has_largest_army)
        assertEquals(2, a.get_score(p1))
        assertEquals(4, a.get_score(p2))
        play_cards(p1, 1)
        assertEquals(null, a.who_has_largest_army)
        assertEquals(2, a.get_score(p1))
        assertEquals(2, a.get_score(p2))

        play_cards(p2, 0)
        play_cards(p1, 1)
        assertEquals(p1.info, a.who_has_largest_army)
        assertEquals(4, a.get_score(p1))
        assertEquals(2, a.get_score(p2))
    }

    /** If you place a city in the middle of your turn, the game should end right away */
    @Test def test_mid_turn_win: Unit = {
        b = new StandardBoard()
        a = new MockAdmin(b, 4, 4)
        p1 = new MockPlayer("Player1", "", a, log, 5, 5, 4, b)
        p2 = new MockPlayer("Player2", "", a, log, 5, 5, 4, b)
        a.register(p1, p2)

        use_test_board
        p1.on_next_turn { (turn: Turn) =>
            turn.roll_dice
            var spots = b.get_valid_city_spots(p1.color)
            var first_coords = spots(0).coords
            turn.place_city(first_coords._1, first_coords._3, first_coords._3)
            assert(!a.is_game_done)
            spots = b.get_valid_city_spots(p1.color)
            first_coords = spots(0).coords
            turn.place_city(first_coords._1, first_coords._3, first_coords._3)
            assertEquals(4, a.get_score(p1))
            assertEquals(p1, a.has_winner)
            assertTrue(a.is_game_done)
            //assert(p1.was_called(:game_end))
        }
    }

    /**
     * Tests getting the standard 4:1 quotes from the bank
     * Inherritly, any user can trade resources at a 4:1 ratio on their turn.
     */
    @Test def test_get_quotes_default: Unit = {
        a.start_game
        p1.on_next_turn { (turn: Turn) =>
            a.make_rich(p1)
            assertEquals(1, turn.get_quotes(List(OreType), List(WheatType)).length)
            assertEquals(4, turn.get_quotes(List(OreType), List(WheatType)).first.receiveNum)

            //Setup
            turn.place_settlement(0, 0, 0)
            turn.place_road(0, 0, 0)
            assertEquals(1, turn.get_quotes(List(OreType), List(WheatType)).length)
            assertEquals(4, turn.get_quotes(List(OreType), List(WheatType)).first.receiveNum)
        }
    }

    /** Test turn.getquotes */
    @Test def test_get_quotes: Unit = {
        a.make_rich(p1)
        b.getTile(1, 0).nodes.first.port = new Port(WheatType, 2)
        b.getTile(-1, 0).nodes.first.port = new Port(null, 3)
        b.getTile(-1, 1).nodes.first.port = new Port(null, 3)
        a.start_game
        //Setup
        p1.on_next_turn { (turn: Turn) =>
            turn.place_settlement(0, 0, 0)
            turn.place_road(0, 0, 0)
        }
        p2.on_next_turn { (turn: Turn) =>
            turn.place_settlement(-1, 0, 0)
            turn.place_road(-1, 0, 0)
            a.make_rich(p2)
            assertEquals(1, turn.get_quotes(List(OreType), List(WheatType)).length)
            assertEquals(3, turn.get_quotes(List(OreType), List(WheatType)).first.receiveNum)
        }
        p2.on_next_turn { (turn: Turn) =>
            turn.place_settlement(-1, 1, 0)
            turn.place_road(-1, 1, 0)
            assertEquals(1, turn.get_quotes(List(OreType), List(WheatType)).length)
            assertEquals(3, turn.get_quotes(List(OreType), List(WheatType)).first.receiveNum)
        }
        p1.on_next_turn { (turn: Turn) =>
            turn.place_settlement(1, 0, 0)
            turn.place_road(1, 0, 0)
            assertEquals(1, turn.get_quotes(List(OreType), List(WheatType)).length)
            assertEquals(2, turn.get_quotes(List(OreType), List(WheatType)).first.receiveNum)
        }
    }

}

/**
 * Full game tests
 * These test full game scenarios from start to finish.
 */
class GameScenarios extends AssertionsForJUnit with IntegrationTest {

    /**
     * Set up a game of 2 random bots
     * Assert that the game finishes
     */
    @Test def test_random_game: Unit = {
        val random_b = new StandardBoard()
        val random_a = new Admin(random_b, 4, 7)
        val random_p1 = new SinglePurchasePlayer("Player1", "", random_a, random_a.log, 4, 5, 15, random_b)
        val random_p2 = new SinglePurchasePlayer("Player2", "", random_a, random_a.log, 4, 5, 15, random_b)
        random_a.register(random_p1, random_p2)
        val future = random_a.start_game
        future.get
    }

   @Test def test_full_game: Unit = {
        b = new StandardBoard()
        a = new MockAdmin(b, 4, 6)
        p1 = new MockPlayer("Player1", "", a, a.log, 4, 5, 15, b)
        p2 = new MockPlayer("Player2", "", a, a.log, 4, 5, 15, b)
        a.register(p1, p2)
        use_test_board

        a.make_rich(p1)
        a.make_rich(p2)
        p1.on_next_turn { (turn: Turn) =>
            turn.roll_dice
            turn.place_road(1, 0, 1)
            turn.place_road(2, 1, 0)
            turn.place_settlement(2, 1, 0)
        }.get
        assertEquals(2, a.get_score(p2))
        assertEquals(3, a.get_score(p1))
        p2.on_next_turn { (turn: Turn) =>
            turn.roll_dice
        }.get
        p1.on_next_turn { (turn: Turn) =>
            turn.roll_dice
            turn.place_road(1, 0, 2)
            turn.place_road(1, 0, 3)
            turn.place_city(2, 1, 0)
        }.get
        assertEquals(2, a.get_score(p2))
        assertEquals(4, a.get_score(p1))
        p2.on_next_turn { (turn: Turn) =>
            turn.roll_dice
        }.get
        assert(!a.is_game_done)
        p1.on_next_turn { (turn: Turn) =>
            turn.roll_dice
            turn.place_road(1, 0, 4)
        }.get
        assertEquals(2, a.get_score(p2))
        assertEquals(6, a.get_score(p1))
        Util.while_with_timeout(2000) { () => !a.is_game_done }
        assert(a.is_game_done)
    }

    /**
     * Test the flow of an exact, actual game *
     * @Test def test_exact_game = {
     * val b = test_board1
     * val a = new MockAdmin(b, 2, 10)
     * val p1 = new MockPlayer("John", "", a, a.log, 4, 5, 15, b)
     * val p2 = new MockPlayer("Lisa", "", a, a.log, 4, 5, 15, b)
     * val john = p1
     * val lisa = p2
     * a.register(john, lisa)
     * assert_points(Map(john -> 0, lisa -> 0))
     * john.on_next_turn { turn =>
     * turn.place_settlement(2, 1, 0)
     * turn.place_road(2, 1, 0)
     * }
     * lisa.on_next_turn { turn =>
     * turn.place_settlement(1, 1, 0)
     * turn.place_road(1, 1, 0)
     * }
     * lisa.on_next_turn(lisa) { turn =>
     * turn.place_settlement(-1, 1, 0)
     * turn.place_road(-1, 1, 0)
     * }
     * john.on_next_turn { turn =>
     * turn.place_settlement(1, 2, 0)
     * turn.place_road(1, 2, 0)
     * }
     * assert_points(Map(john -> 2, lisa -> 2))
     *
     * //puts "John's score: #{a.get_score(p1)}"
     * //puts "Lisa's score: #{a.get_score(p2)}"
     * assertEquals("John was supposed to win!", john, a.has_winner)
     * }
     */

    /**
     * helper function to offer, and accept a trade between the current player and another player
     * [tradingPlayer] is the player offering the quote
     *
     * def orchestrate_trade(tradingPlayer, receiveType, receiveAmount, giveType, giveAmount)
     * quote = Quote.new(tradingPlayer.info, receiveType, receiveAmount, giveType, giveAmount)
     * tradingPlayer.should_offer = [quote]
     * given_quotes = a.current_turn.get_quotes([WheatType], [OreType])
     * a.current_turn.accept_quote(quote)
     * }
     */

    /**
     * Asserts that each given player has the correct score
     * [players] is a hash of {Player => points}
     */
    def assert_points(players: Map[Player, Int]) = {
        players.foreach { kv =>
            assertEquals(kv._2, a.get_score(kv._1))
        }
    }
}

