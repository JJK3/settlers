package org.jjk3.player

import org.jjk3.core.*
import org.jjk3.core.Resource.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerTest {

    lateinit var player: Player
    @Before
    fun setup() {
        player = MockPlayer()
        player.color = "red"
    }

    @Test
    fun del_cards() {
        player.giveCards(listOf(Brick, Brick, Sheep).map { ResourceCard(it) })
        assertEquals(player.cards, listOf(Brick, Brick, Sheep).map(::ResourceCard))
        player.takeCards(listOf(Brick, Sheep).map(::ResourceCard), Turn.ReasonToTakeCards.Other)
        assertEquals(player.cards, listOf(ResourceCard(Brick)))
    }

    @Test
    fun countDevelopmentCards() {
        player.giveCards(listOf(ResourceCard(Brick), ResourceCard(Wheat), SoldierCard()))
        assertEquals(player.countDevelopmentCards(), 1)
    }

    @Test
    fun countResources() {
        player.giveCards(listOf(ResourceCard(Brick), ResourceCard(Brick), ResourceCard(Wheat), SoldierCard()))
        assertEquals(player.countResources(Brick), 2)
        assertEquals(player.countResources(Wheat), 1)
    }

    @Test
    fun resourceCards() {
        player.giveCards(listOf(ResourceCard(Brick), ResourceCard(Brick), ResourceCard(Wheat), SoldierCard()))
        assertEquals(player.resourceCards().size, 3)
    }

    @Test
    fun countCards() {
        player.giveCards(listOf(SoldierCard(), SoldierCard(), ResourceMonopolyCard()))
        assertEquals(player.countCards(SoldierCard::class.java), 2)
        assertEquals(player.countCards(ResourceMonopolyCard::class.java), 1)
    }

    @Test
    fun playDevelopmentCard() {
        player.playedDevCard(VictoryPointCard())
        assertEquals(player.getExtraVictoryPoints(), 1)
    }

    @Test
    fun canAfford() {
        player.giveCards(listOf(ResourceCard(Resource.Brick), ResourceCard(Resource.Wood)))
        assertTrue(player.canAfford(listOf(Road(player.color !!))))
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

}

open class MockPlayer() : Player() {
    constructor(color: String) : this() {
        this.color = color
    }


    override fun moveBandit(oldLocation: HexCoordinate): HexCoordinate {
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
