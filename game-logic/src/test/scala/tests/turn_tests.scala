import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.AssertionsForJUnit
import scala.collection.mutable.ListBuffer
import org.junit.Assert._
import org.junit._
import org.apache.log4j._

import core._
import board._

class TurnTest extends AssertionsForJUnit {
    var board: Board = null
    var admin: MockAdmin = null
    var player: Player = null
    var log: Logger = Logger.getLogger(classOf[TurnTest])
    var turn: Turn = null

    @Before
    def setup = {
        board = new StandardBoard()
        admin = new MockAdmin(board, 2)
        player = new MockPlayer("player1", "", admin, log, 5, 5, 4, board)
        player.preferred_color = "red"
        admin.register(player)

        admin.make_rich(player)
        turn = new Turn(admin, player, board, log)
        //    admin.should_receive(:currentTurn).and_return(turn)
        admin.set_current_turn(turn)
    }
    
    @After def teardown()={
        admin.shutdown()
    }

    @Test def testRegister = {
        assertNotNull(player.color)
        assertTrue(admin.get_players.size > 0)
        assertNotNull(admin.get_player(player.color))
    }

    @Test def testRollDice(): Unit = {
        turn.roll_dice
        assertTrue(turn.has_rolled)
    }

    @Test def test_place_road_before_dice_roll(): Unit = {
        board.getNode(0, 0, 0).city = new Settlement("red")
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.place_road(0, 0, 0) }
    }

    @Test def test_place_road(): Unit = {
        board.getNode(0, 0, 0).city = new Settlement("red")
        turn.roll_dice
        turn.place_road(0, 0, 0)
    }

    /**
     * This test makes sure that placing a road is a transaction
     * Nothing should be affected if the player cannot but a piece
     */
    @Test def test_place_road_cannot_afford: Unit = {
        board.getNode(0, 0, 0).city = new Settlement("red")
        admin.should_roll = (5, 5)
        turn.roll_dice
        //make the player have not enough cards
        admin.set_cards(player, Map(WoodType -> 0))
        val num_of_roads = player.piecesLeft(classOf[Road])
        val cards = player.get_cards
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.place_road(0, 0, 0) }
        assertEquals("piecesLeft decreased during transaction",
            num_of_roads,
            player.piecesLeft(classOf[Road]))
        assertEquals(cards, player.get_cards)
    }

    @Test def test_place_settlement: Unit = {
        board.getEdge(0, 0, 1).road = new Road("red")
        assertTrue(board.getEdge(0, 0, 1).has_road)
        turn.roll_dice
        turn.place_settlement(0, 0, 0)
    }

    @Test def test_place_settlement_before_roll: Unit = {
        board.getEdge(0, 0, 1).road = new Road("red")
        assertTrue(board.getEdge(0, 0, 1).has_road)
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.place_settlement(0, 0, 0) }
    }

    @Test def test_place_city: Unit = {
        board.getNode(0, 0, 0).city = new Settlement("red")
        turn.roll_dice
        turn.place_city(0, 0, 0)
    }

    @Test def test_place_city_before_roll: Unit = {
        board.getNode(0, 0, 0).city = new Settlement("red")
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.place_city(0, 0, 0) }
    }

    /** A helper method to count the number of tiles with the bandit. */
    def count_bandit: Int = board.tiles.count { _._2.has_bandit }

    @Test def test_move_bandit: Unit = {
        assertEquals(1, count_bandit)

        //Play a soldier card
        val card = new SoldierCard()
        player.add_cards(List(card))
        turn.play_development_card(card)
        assertEquals(1, count_bandit)

        admin.should_roll = (3, 4)
        turn.roll_dice
        assertEquals(1, count_bandit)
    }

    @Test def test_roll_dice_with_soldier: Unit = {
        val card = new SoldierCard()
        admin.set_cards(player, Map(card -> 1))

        turn.play_development_card(card)
        turn.roll_dice
        assertTrue(turn.has_rolled)
    }

    @Test def test_buy_development_card_before_roll: Unit = {
        //    admin.should_receive(:buy_development_card).once
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.buy_development_card }
    }

    @Test def test_buy_development_card: Unit = {
        //    admin.should_receive(:buy_development_card).once
        turn.roll_dice
        val cards = player.get_cards
        var card = turn.buy_development_card
        assertEquals(cards(OreType) - 1, player.get_cards(OreType))
        assertEquals(cards(WheatType) - 1, player.get_cards(WheatType))
        assertEquals(cards(SheepType) - 1, player.get_cards(SheepType))
        assertEquals(1, player.get_cards(card))
    }

    /**
     * This test makes sure that placing a settlement is a transaction
     * Nothing should be affected if the player cannot but a piece
     */
    @Test def test_place_settlement_cannot_afford: Unit = {
        //make the player have not enough cards
        board.getEdge(0, 0, 1).road = new Road("red")
        turn.roll_dice
        admin.set_cards(player, Map(WheatType -> 0))
        val num_of_pieces = player.piecesLeft(classOf[Settlement])
        val cards = player.get_cards
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.place_settlement(0, 0, 0) }
        assertEquals("piecesLeft decreased during transaction",
            num_of_pieces,
            player.piecesLeft(classOf[Settlement]))
        assertEquals(cards, player.get_cards)
    }

    /**
     * This test makes sure that placing a city is a transaction
     * Nothing should be affected if the player cannot but a piece
     */
    @Test def test_place_city_cannot_afford: Unit = {
        //make the player have not enough cards
        board.getNode(0, 0, 0).city = new Settlement("red")
        turn.roll_dice
        admin.set_cards(player, Map(OreType -> 0))
        val num_of_pieces = player.piecesLeft(classOf[City])
        val cards = player.get_cards
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.place_city(0, 0, 0) }
        assertEquals("piecesLeft decreased during transaction", num_of_pieces,
            player.piecesLeft(classOf[City]))
        assertEquals(cards, player.get_cards)
    }

    @Test def test_done_before_dice_roll: Unit = {
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.done }
    }

    @Test def test_done: Unit = {
        turn.roll_dice
        turn.done
    }

    @Test def test_play_development_card_before_dice_roll: Unit = {
        val card = new MockCard()
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.play_development_card(card) }
    }

    @Test def test_play_development_card_without_enough_cards: Unit = {
        turn.roll_dice
        val card = new MockCard()
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.play_development_card(card) }
    }

    @Test def test_play_development_card: Unit = {
        val card = new MockCard()
        turn.player.add_cards(List(card))
        turn.roll_dice
        assertTrue(!card.was_used)
        turn.play_development_card(card)
        assertTrue(card.was_used)
    }

    @Test def test_get_quotes_before_dice_roll: Unit = {
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.get_quotes(List(BrickType), List(OreType)) }
    }

    @Test def test_get_quotes: Unit = {
        turn.roll_dice
        turn.get_quotes(List(BrickType), List(OreType))
    }

    @Test def test_get_quotes_from_bank_before_dice_roll: Unit = {
        AssertionUtils.assertRaises(classOf[RuleException]) { () =>
            turn.get_quotes_from_bank(List(BrickType), List(OreType))
        }
    }

    @Test def test_get_quotes_from_bank: Unit = {
        turn.roll_dice
        turn.get_quotes_from_bank(List(BrickType), List(OreType))
    }

    @Test def test_accept_quote_before_dice_roll: Unit = {
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.accept_quote(null) }
    }

    @Test def test_accept_quote: Unit = {
        turn.roll_dice
        // Haven't received any quotes yet
        AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.accept_quote(null) }
    }

    def dev_card_test_helper(card: DevelopmentCard)(block: (DevelopmentCard) => Unit) = {
        turn.player.add_cards(List(card))
        var dev_card_count = turn.player.get_cards(card)
        assertEquals(1, dev_card_count)
        assertEquals(0, turn.active_cards.size)
        block(card)
        assertEquals(0, turn.active_cards.size)
        dev_card_count = turn.player.get_cards(card)
        assertEquals(0, dev_card_count)
    }

    @Test def test_play_development_card_soldier: Unit = {
        dev_card_test_helper(new SoldierCard()) { (card: DevelopmentCard) =>
            turn.play_development_card(card)
        }
    }

    @Test def test_play_development_card_resource_monopoly = {
        dev_card_test_helper(new ResourceMonopolyCard()) { (card: DevelopmentCard) =>
            turn.roll_dice
            turn.play_development_card(card)
        }
    }

    @Test def test_play_development_card_road_builder = {
        dev_card_test_helper(new RoadBuildingCard()) { (card: DevelopmentCard) =>
            turn.roll_dice
            turn.play_development_card(card)
            assertEquals(2, player.purchased_pieces)
        }

        //place each of the roads
        board.getNode(1, 1, 1).city = new Settlement(player.color)
        val placeFirstAvailableRoad = () => {
            var spots = board.get_valid_road_spots(player.color)
            var coords = spots(0).coords
            turn.place_road(coords._1, coords._2, coords._3)
        }

        placeFirstAvailableRoad()
        assertEquals(1, player.purchased_pieces)

        placeFirstAvailableRoad()
        assertEquals(0, player.purchased_pieces)
    }

    /** Try to end the turn before you've placed all the roads */
    @Test def test_play_development_card_road_builder_cheat = {
        dev_card_test_helper(new RoadBuildingCard()) { (card: DevelopmentCard) =>
            turn.roll_dice
            turn.play_development_card(card)
            //End the turn before placing the roads
            AssertionUtils.assertRaises(classOf[RuleException]) { () => turn.done }
        }
    }

    @Test def test_play_development_card_year_of_plenty = {
        dev_card_test_helper(new YearOfPlentyCard()) { (card: DevelopmentCard) =>
            val player_card_count = player.resource_cards.size
            //Make sure that the player has the same # of cards
            assertEquals(player_card_count, player.resource_cards.size)
            turn.roll_dice
            turn.play_development_card(card)
            //Make sure that the player got 2 more cards
            assertEquals(player_card_count + 2, player.resource_cards.size)
        }
    }
}