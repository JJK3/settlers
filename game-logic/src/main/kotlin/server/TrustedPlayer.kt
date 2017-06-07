package server

import org.jjk3.core.*
import org.jjk3.player.*


class TrustedPlayer(
        val original_player: Player,
        cities: Int = 4,
        settlements: Int = 5,
        roads: Int = 15)
    : Player(original_player.first_name, original_player.last_name, cities, settlements, roads) {

    private var my_board: Board? = null
    override fun get_user_quotes(player_reference: PlayerReference, wantList: List<Resource>,
                                 giveList: List<Resource>): List<Quote> =
            original_player.get_user_quotes(player_reference, wantList, giveList)

    override fun move_bandit(old_hex: Hex): Hex = original_player.move_bandit(old_hex)
    override fun select_resource_cards(cards: List<Resource>, count: Int, reason: Int): List<Resource> =
            original_player.select_resource_cards(cards, count, reason)

    override fun select_player(players: List<PlayerReference>, reason: Int): PlayerReference =
            original_player.select_player(players, reason)

    override var color: String
        get() = super.color
        set(value) {
            original_player.color = value
            _color = value
        }
    override var board: Board?
        get() = super.board
        set(value) {
            original_player.board = value
            my_board = value
        }

    override fun add_cards(cards_to_add: List<Card>) {
        original_player.add_cards(cards_to_add)
        super.add_cards(cards_to_add)
    }

    override fun del_cards(cards_to_add: List<Card>, i: Int) {
        original_player.del_cards(cards_to_add, i)
        super.del_cards(cards_to_add, i)
    }

    override fun take_turn(turn: Turn, is_setup: Boolean) {
        original_player.take_turn(turn, is_setup)
    }

    override fun give_free_roads(num_roads: Int): Unit {
        original_player.give_free_roads(num_roads)
        super.give_free_roads(num_roads)
    }

    override fun remove_free_roads(num_roads: Int): Unit {
        original_player.remove_free_roads(num_roads)
        super.remove_free_roads(num_roads)
    }

    override fun played_dev_card(card: DevelopmentCard): Unit {
        original_player.played_dev_card(card)
        super.played_dev_card(card)
    }

    override fun offer_quote(quote: Quote) {
        original_player.offer_quote(quote)
        super.offer_quote(quote)
    }

    override fun player_moved_bandit(player_reference: PlayerReference, hex: Hex) {
        original_player.player_moved_bandit(player_reference, hex)
        super.player_moved_bandit(player_reference, hex)
    }

    override fun game_start() {
        original_player.game_start()
        super.game_start()
    }

    override fun game_end(winner: PlayerReference, points: Int) {
        original_player.game_end(winner, points)
        super.game_end(winner, points)
    }

    override fun update_board(b: Board) {
        original_player.update_board(b)
        super.update_board(b)
    }

    override fun placed_road(player: PlayerReference, edgeCoordinate: EdgeCoordinate) {
        original_player.placed_road(player, edgeCoordinate)
        super.placed_road(player, edgeCoordinate)
    }

    override fun placed_settlement(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        original_player.placed_settlement(player, nodeCoordinate)
        super.placed_settlement(player, nodeCoordinate)
    }

    override fun placed_city(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        original_player.placed_city(player, nodeCoordinate)
        super.placed_city(player, nodeCoordinate)
    }

    /*   override fun register_listener(listener: PlayerListener) {
    }*/

    //TODO: need to go through ALL org.jjk3.player methods

}
