package org.jjk3.player

import org.jjk3.core.Hex
import org.jjk3.core.Resource
import org.jjk3.core.ResourceCard
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerTest {

    @Test
    fun del_cards() {
        val player = MockPlayer("1")
        player.addCards(listOf(Resource.Brick, Resource.Brick, Resource.Sheep).map { ResourceCard(it) })
        assertEquals(player.cards, listOf(Resource.Brick, Resource.Brick, Resource.Sheep).map(::ResourceCard))
        player.takeCards(listOf(Resource.Brick, Resource.Sheep).map(::ResourceCard), 1)
        assertEquals(player.cards, listOf(ResourceCard(Resource.Brick)))
    }

}

class MockPlayer(id: String) : Player(id, id) {
    override fun moveBandit(old_hex: Hex): Hex {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun selectResourceCards(cards: List<Resource>, count: Int, reason: Int): List<Resource> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun selectPlayer(players: List<PlayerReference>, reason: Int): PlayerReference {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getUserQuotes(player_reference: PlayerReference, wantList: List<Resource>,
                               giveList: List<Resource>): List<Quote> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
