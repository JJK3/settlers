package org.jjk3.player

import org.jjk3.core.Hex
import org.jjk3.core.Resource
import org.jjk3.core.ResourceCard
import org.jjk3.core.Road
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerTest {

    lateinit var player: Player
    @Before
    fun setup() {
        player = MockPlayer("1")
    }

    @Test
    fun del_cards() {
        player.giveCards(listOf(Resource.Brick, Resource.Brick, Resource.Sheep).map { ResourceCard(it) })
        assertEquals(player.cards, listOf(Resource.Brick, Resource.Brick, Resource.Sheep).map(::ResourceCard))
        player.takeCards(listOf(Resource.Brick, Resource.Sheep).map(::ResourceCard), Turn.ReasonToTakeCards.Other)
        assertEquals(player.cards, listOf(ResourceCard(Resource.Brick)))
    }

    @Test
    fun getPurchasedRoads() {
    }

    @Test
    fun setPurchasedRoads() {
    }

    @Test
    fun getPreferredColor() {
    }

    @Test
    fun setPreferredColor() {
    }

    @Test
    fun getPlayedDevCards() {
    }

    @Test
    fun setPlayedDevCards() {
    }

    @Test
    fun getCurrentTurn() {
    }

    @Test
    fun setCurrentTurn() {
    }

    @Test
    fun giveFreeRoads() {
    }

    @Test
    fun removeFreeRoads() {
    }

    @Test
    fun freeRoads() {
    }

    @Test
    fun playedDevCard() {
    }

    @Test
    fun count_dev_cards() {
    }

    @Test
    fun countResources() {
    }

    @Test
    fun offerQuote() {
    }

    @Test
    fun getExtraVictoryPoints() {
    }

    @Test
    fun canAfford() {
        player.giveCards(listOf(ResourceCard(Resource.Brick), ResourceCard(Resource.Wood)))
        assertTrue(player.canAfford(listOf(Road(player.color))))
    }

    @Test
    fun giveCards() {
        val cards = listOf(ResourceCard(Resource.Brick), ResourceCard(Resource.Wood))
        player.giveCards(cards)
        assertEquals(player.cards, cards)
    }

    @Test
    fun takeCards() {
        val cards = listOf(ResourceCard(Resource.Brick), ResourceCard(Resource.Wood))
        player.giveCards(cards)
        player.takeCards(listOf(ResourceCard(Resource.Brick)), Turn.ReasonToTakeCards.Other)
        assertEquals(player.cards, listOf(ResourceCard(Resource.Wood)))
    }

    @Test
    fun takeTurn() {
    }

    @Test
    fun getUserQuotes() {
    }

    @Test
    fun moveBandit() {
    }

    @Test
    fun selectResourceCards() {
    }

    @Test
    fun selectPlayer() {
    }

    @Test
    fun playerMovedBandit() {
    }

    @Test
    fun gameStart() {
    }

    @Test
    fun gameEnd() {
    }

    @Test
    fun get_board() {
    }

    @Test
    fun updateBoard() {
    }

    @Test
    fun placedRoad() {
    }

    @Test
    fun placedSettlement() {
    }

    @Test
    fun placedCity() {
    }

    @Test
    fun resourceCards() {
    }

    @Test
    fun getCities() {
    }

    @Test
    fun getSettlements() {
    }

    @Test
    fun getRoads() {
    }

    @Test
    fun updateBoard1() {
    }

    @Test
    fun playerReceivedCards() {
    }

    @Test
    fun playerRolled() {
    }

    @Test
    fun playerStoleCard() {
    }

    @Test
    fun playerJoined() {
    }

    @Test
    fun getTurn() {
    }

    @Test
    fun playerHasLongestRoad() {
    }

    @Test
    fun playerHasLargestArmy() {
    }

}

class MockPlayer(id: String) : Player(id, id) {
    override fun moveBandit(old_hex: Hex): Hex {
        TODO("not implemented")
    }

    override fun selectResourceCards(cards: List<Resource>, count: Int, reason: Int): List<Resource> {
        TODO("not implemented")
    }

    override fun selectPlayer(players: List<PlayerReference>, reason: Int): PlayerReference {
        TODO("not implemented")
    }

    override fun getUserQuotes(player_reference: PlayerReference, wantList: List<Resource>,
                               giveList: List<Resource>): List<Quote> {
        TODO("not implemented")
    }

}
