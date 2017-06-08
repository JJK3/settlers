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

    override fun takeCards(cards_to_add: List<Card>, i: Int) {
        original_player.takeCards(cards_to_add, i)
        super.takeCards(cards_to_add, i)
    }

    override fun take_turn(turn: Turn) {
        original_player.take_turn(turn)
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

    override fun playerMovedBandit(player_reference: PlayerReference, hex: Hex) {
        original_player.playerMovedBandit(player_reference, hex)
        super.playerMovedBandit(player_reference, hex)
    }

    override fun gameStart(maxScore: Int) {
        original_player.gameStart(maxScore)
        super.gameStart(maxScore)
    }

    override fun gameEnd(winner: PlayerReference, points: Int) {
        original_player.gameEnd(winner, points)
        super.gameEnd(winner, points)
    }

    override fun update_board(b: Board) {
        original_player.update_board(b)
        super.update_board(b)
    }

    override fun placedRoad(player: PlayerReference, edgeCoordinate: EdgeCoordinate) {
        original_player.placedRoad(player, edgeCoordinate)
        super.placedRoad(player, edgeCoordinate)
    }

    override fun placedSettlement(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        original_player.placedSettlement(player, nodeCoordinate)
        super.placedSettlement(player, nodeCoordinate)
    }

    override fun placedCity(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        original_player.placedCity(player, nodeCoordinate)
        super.placedCity(player, nodeCoordinate)
    }

    /*   override fun registerListener(listener: PlayerListener) {
    }*/

    //TODO: need to go through ALL player methods

}
