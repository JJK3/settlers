package org.jjk3.player

import junit.framework.Assert.assertEquals
import junit.framework.Assert.fail
import org.jjk3.board.*
import org.jjk3.board.Resource.*
import org.jjk3.gameplay.*
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Matchers
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
        turn = spy(Turn(admin, player, board))
        turn.state = TurnState.Active
        observer = mock(GameObserver::class.java)
        `when`(admin.getPlayer("red")).thenReturn(player)
        `when`(admin.getPlayer("blue")).thenReturn(otherPlayer)
        `when`(admin.state).thenReturn(UsesGameState.GameState.Running)
        `when`(admin.observers).thenReturn(listOf(observer))
        `when`(admin.board).thenReturn(board)
        `when`(board.developmentCards).thenReturn(DevelopmentCardBag.create())
        `when`(admin.players).thenReturn(listOf(player))
        val piecesForSale = PiecesForSale(player.color !!)
        `when`(board.getPiecesForSale(player.color !!)).thenReturn(piecesForSale)
        `when`(board.getCards(anyInt(), matches("red"))).thenReturn(
                listOf(ResourceCard(Brick)))
        `when`(board.hasLongestRoad(Matchers.anyString())).thenReturn(false)
    }

    class FixedDice(override val die1: Int, override val die2: Int) : NormalDiceRoll()

    @Test
    fun rollDice() {
        `when`(turn.getDiceRoll()).thenReturn(FixedDice(4, 5))
        turn.rollDice()
        assertEquals(turn.state, TurnState.RolledDice)
        verify(player).giveCards(listOf(ResourceCard(Brick)))
    }

    @Test
    fun rollDiceWithMovingTheBandit() {
        `when`(turn.getDiceRoll()).thenReturn(FixedDice(4, 3))
        val oldCoords = HexCoordinate(0, 0)
        val hex = Hex(null, 4, oldCoords)
        hex.hasBandit = true
        `when`(board.findBandit()).thenReturn(hex)
        val newCoords = HexCoordinate(0, 1)
        `when`(player.moveBandit(hex.coords)).thenReturn(newCoords)
        `when`(board.getHex(newCoords)).thenReturn(Hex(Brick, 3, newCoords))
        turn.rollDice()
        assertEquals(turn.state, TurnState.RolledDice)
        verify(player).moveBandit(oldCoords)
    }

    @Test
    fun rollDiceWithMovingWithDiscarding() {
        val cards = (1..9).map { ResourceCard(Brick) }
        player.giveCards(cards)
        `when`(turn.getDiceRoll()).thenReturn(FixedDice(4, 3))
        `when`(board.findBandit()).thenReturn(null)
        `when`(player.selectResourceCards(cards.map { it.resource }, 4, Admin.SELECT_CARDS_ROLLED_7)).thenReturn(
                (1..4).map { Brick })
        turn.rollDice()
        assertEquals(turn.state, TurnState.RolledDice)
        assertEquals(player.resourceCards(), (1..5).map { ResourceCard(Brick) })
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
        hex.node(NodeNumber(3)).city = City(otherPlayer.color !!)
        otherPlayer.giveCards(listOf(ResourceCard(Brick)))
        `when`(board.getHex(hex.coords)).thenReturn(hex)
        turn.moveBandit(hex.coords)
        assertTrue(otherPlayer.resourceCards().isEmpty())
    }

    @Test
    fun placeRoad() {
        turn.state = TurnState.RolledDice
        player.giveCards(listOf(ResourceCard(Brick), ResourceCard(Wood)))
        val edgeCoord = EdgeCoordinate(0, 0, 3)
        val edge = Edge()
        `when`(board.getValidRoadSpots(player.color !!)).thenReturn(listOf(edge))
        `when`(board.getEdge(edgeCoord)).thenReturn(edge)
        turn.placeRoad(edgeCoord)
        verify(board).placeRoad(Road(player.color !!), edgeCoord)
        assertTrue(player.resourceCards().isEmpty())
    }

    @Test
    fun placeSettlement() {
        turn.state = TurnState.RolledDice
        player.giveCards(listOf(Brick, Wood, Sheep, Wheat).map(::ResourceCard))
        val coord = NodeCoordinate(0, 0, 4)
        val node = Node()
        `when`(board.getValidSettlementSpots(player.color !!)).thenReturn(listOf(node))
        `when`(board.getNode(coord)).thenReturn(node)
        turn.placeSettlement(coord)
        verify(board).placeCity(Settlement(player.color !!), coord)
        assertTrue(player.resourceCards().isEmpty())
    }

    @Test
    fun placeCity() {
        turn.state = TurnState.RolledDice
        player.giveCards(listOf(Wheat, Wheat, Ore, Ore, Ore).map(::ResourceCard))
        val coord = NodeCoordinate(0, 0, 4)
        val node = Node()
        node.city = Settlement(player.color !!)
        `when`(board.getValidCitySpots(player.color !!)).thenReturn(listOf(node))
        `when`(board.getNode(coord)).thenReturn(node)
        turn.placeCity(coord)
        verify(board).placeCity(City(player.color !!), coord)
        assertTrue(player.resourceCards().isEmpty())
    }

    @Test
    fun done() {
        turn.state = TurnState.RolledDice
        turn.done()
    }

    @Test
    fun forceDone() {
        turn.forceDone()
    }

    @Test
    fun playDevelopmentCard() {
        turn.state = TurnState.RolledDice
        val card = VictoryPointCard()
        player.giveCards(listOf(card))
        turn.playDevelopmentCard(card)
        verify(player).playedDevCard(card)
    }

    @Test
    fun acceptQuote() {
        turn.state = TurnState.RolledDice
        val have = listOf(Wheat)
        val want = listOf(Brick)
        player.giveCards(have.map(::ResourceCard))
        otherPlayer.giveCards(want.map(::ResourceCard))
        val quote = Quote(otherPlayer.ref(), Wheat, 1, Brick, 1)
        `when`(admin.getQuotes(player, want, have)).thenReturn(listOf(quote))
        turn.getQuotes(want, have)
        turn.acceptQuote(quote)
        assertEquals(player.resourceCards(), listOf(ResourceCard(Brick)))
        assertEquals(otherPlayer.resourceCards(), listOf(ResourceCard(Wheat)))
    }

}