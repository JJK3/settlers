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
        player.add_cards(listOf(Resource.Brick, Resource.Brick, Resource.Sheep).map { ResourceCard(it) })
        assertEquals(player.get_cards(), listOf(Resource.Brick, Resource.Brick, Resource.Sheep).map(::ResourceCard))
        player.del_cards(listOf(Resource.Brick, Resource.Sheep).map(::ResourceCard), 1)
        assertEquals(player.get_cards(), listOf(ResourceCard(Resource.Brick)))
    }

}

class MockPlayer(id: String) : Player(id, id) {
    override fun move_bandit(old_hex: Hex): Hex {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun select_resource_cards(cards: List<Resource>, count: Int, reason: Int): List<Resource> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun select_player(players: List<PlayerReference>, reason: Int): PlayerReference {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun get_user_quotes(player_reference: PlayerReference, wantList: List<Resource>,
                                 giveList: List<Resource>): List<Quote> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
