package org.jjk3.player

import junit.framework.Assert.assertEquals
import junit.framework.Assert.fail
import org.jjk3.core.*
import org.jjk3.core.Resource.*
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class TurnTest {

    lateinit var turn: Turn
    lateinit var admin: Admin
    lateinit var player: Player
    lateinit var otherPlayer: Player
    lateinit var board: Board
    lateinit var observer: GameObserver
    @Before
    fun setup() {
        admin = mock(Admin::class.java)
        player = spy(MockPlayer())
        player.color = "red"
        otherPlayer = spy(MockPlayer())
        otherPlayer.color = "blue"
        board = mock(Board::class.java)
        turn = Turn(admin, player, board)
        turn.state = TurnState.Active
        observer = mock(GameObserver::class.java)
        `when`(admin.observers).thenReturn(listOf(observer))
        `when`(admin.board).thenReturn(board)
        `when`(board.developmentCards).thenReturn(DevelopmentCardBag.create())
        `when`(admin.players).thenReturn(listOf(player))
        `when`(board.getCards(anyInt(), matches("red"))).thenReturn(
                listOf(ResourceCard(Brick)))
    }

    @Test
    fun rollDice() {
        val rollDice = turn.rollDice()
        assertEquals(turn.state, TurnState.RolledDice)
        if (rollDice.sum() == 7) {
            verify(player).moveBandit(any(HexCoordinate::class.java))
        } else {
            verify(player).giveCards(listOf(ResourceCard(Brick)))
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rollDiceError() {
        turn.state = TurnState.RolledDice
        turn.rollDice()
    }

    @Test
    fun activeCards() {
    }

    @Test
    fun buyDevelopmentCard() {
        turn.state = TurnState.RolledDice
        player.giveCards(listOf(Ore, Sheep, Wheat).map(::ResourceCard))
        val card = turn.buyDevelopmentCard()
        assertTrue(card is DevelopmentCard)
        assertTrue(player.resourceCards().isEmpty())
    }

    @Test
    fun breakRule() {
        try {
            turn.breakRule("Test")
            fail("Expected rule exception")
        } catch (e: RuleException) {
            assertEquals(turn.state, TurnState.DoneWithError)
        }
    }

    @Test
    fun getQuotes() {
        val asking = listOf(Brick)
        val have = listOf(Wheat)
        player.giveCards(listOf(ResourceCard(Wheat)))
        otherPlayer.giveCards(listOf(ResourceCard(Brick), ResourceCard(Brick)))
        turn.state = TurnState.RolledDice
        val quote = listOf(Quote(otherPlayer.ref(), Brick, 2, Wheat, 1))
        `when`(admin.getQuotes(player, asking, have)).thenReturn(quote)
        val quotes = turn.getQuotes(asking, have)
        assertEquals(quotes, quote)
    }

    @Test
    fun getQuotesFromBank() {
        val asking = listOf(Brick)
        val have = listOf(Wheat)
        player.giveCards((0..4).map { ResourceCard(Wheat) })
        `when`(board.allNodes()).thenReturn(emptySet())
        turn.state = TurnState.RolledDice
        val quote = listOf(Quote(null, Wheat, 4, Brick, 1))
        `when`(admin.getQuotes(player, asking, have)).thenReturn(quote)
        val quotes = turn.getQuotesFromBank(asking, have)
        assertEquals(quotes, quote)
    }

    @Test
    fun moveBandit() {
        val hex = Hex(Brick, 5, HexCoordinate(0, 0))
        hex.hasBandit = true
        hex.node(NodeNumber(3)).city = City("red")
        val player1 = MockPlayer("red")
        player1.giveCards(listOf(ResourceCard(Brick)))
        `when`(admin.getPlayer("red")).thenReturn(player1)
        `when`(board.getHex(hex.coords)).thenReturn(hex)
        turn.moveBandit(hex.coords)
        assertTrue(player1.resourceCards().isEmpty())
    }

    @Test
    fun receivedQuote() {
    }

    @Test
    fun placeRoad() {
    }

    @Test
    fun placeSettlement() {
    }

    @Test
    fun assertCanPlaceSettlement() {
    }

    @Test
    fun placeCity() {
    }

    @Test
    fun assertGameIsNotDone() {
    }

    @Test
    fun purchaseRoad() {
    }

    @Test
    fun payFor() {
    }

    @Test
    fun done() {
    }

    @Test
    fun forceDone() {
    }

    @Test
    fun validateQuoteLists() {
    }

    @Test
    fun assertRule() {
    }

    @Test
    fun playDevelopmentCard() {
    }

    @Test
    fun acceptQuote() {
    }

}