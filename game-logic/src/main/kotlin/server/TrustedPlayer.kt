package server

import org.jjk3.core.*
import org.jjk3.player.Player
import org.jjk3.player.PlayerReference
import org.jjk3.player.Quote
import org.jjk3.player.Turn


class TrustedPlayer(val original_player: Player) : Player() {

    override fun getUserQuotes(player_reference: PlayerReference, wantList: List<Resource>,
                               giveList: List<Resource>): List<Quote> =
            original_player.getUserQuotes(player_reference, wantList, giveList)

    override fun moveBandit(old_hex: Hex): Hex = original_player.moveBandit(old_hex)
    override fun selectResourceCards(cards: List<Resource>, count: Int, reason: Int): List<Resource> =
            original_player.selectResourceCards(cards, count, reason)

    override fun selectPlayer(players: List<PlayerReference>, reason: Int): PlayerReference =
            original_player.selectPlayer(players, reason)

    override var color: String? = null
        get() = super.color
        set(value) {
            original_player.color = value
            field = value
        }

    override fun giveCards(cardsToAdd: List<Card>) {
        original_player.giveCards(cardsToAdd)
        super.giveCards(cardsToAdd)
    }

    override fun takeCards(cards_to_add: List<Card>, i: Turn.ReasonToTakeCards) {
        original_player.takeCards(cards_to_add, i)
        super.takeCards(cards_to_add, i)
    }

    override fun takeTurn(turn: Turn) {
        original_player.takeTurn(turn)
    }

    override fun giveFreeRoads(num_roads: Int): Int {
        original_player.giveFreeRoads(num_roads)
        return super.giveFreeRoads(num_roads)
    }

    override fun removeFreeRoads(num_roads: Int): Int {
        original_player.removeFreeRoads(num_roads)
        return super.removeFreeRoads(num_roads)
    }

    override fun playedDevCard(card: DevelopmentCard): Unit {
        original_player.playedDevCard(card)
        super.playedDevCard(card)
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

    override fun updateBoard(b: Board) {
        original_player.updateBoard(b)
        super.updateBoard(b)
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
