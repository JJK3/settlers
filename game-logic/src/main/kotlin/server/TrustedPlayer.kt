package server

import org.jjk3.board.*
import org.jjk3.gameplay.Quote
import org.jjk3.gameplay.Turn
import org.jjk3.player.Player
import org.jjk3.player.PlayerReference


class TrustedPlayer(val original: Player) : Player() {

    override fun getUserQuotes(playerRef: PlayerReference, wantList: Set<Resource>,
                               giveList: Set<Resource>): Set<Quote> =
            original.getUserQuotes(playerRef, wantList, giveList)

    override fun moveBandit(oldLocation: HexCoordinate): HexCoordinate = original.moveBandit(oldLocation)
    override fun selectResourceCards(cards: List<Resource>, count: Int, reason: Int): List<Resource> =
            original.selectResourceCards(cards, count, reason)

    override fun selectPlayer(players: List<PlayerReference>, reason: Int): PlayerReference =
            original.selectPlayer(players, reason)

    override var color: String? = null
        get() = super.color
        set(value) {
            original.color = value
            field = value
        }

    override fun giveCards(cardsToAdd: List<Card>) {
        original.giveCards(cardsToAdd)
        super.giveCards(cardsToAdd)
    }

    override fun takeCards(cards_to_add: List<Card>, i: Turn.ReasonToTakeCards) {
        original.takeCards(cards_to_add, i)
        super.takeCards(cards_to_add, i)
    }

    override fun takeTurn(turn: Turn) {
        original.takeTurn(turn)
    }

    override fun giveFreeRoads(num_roads: Int): Int {
        original.giveFreeRoads(num_roads)
        return super.giveFreeRoads(num_roads)
    }

    override fun removeFreeRoads(num_roads: Int): Int {
        original.removeFreeRoads(num_roads)
        return super.removeFreeRoads(num_roads)
    }

    override fun playedDevCard(card: DevelopmentCard): Unit {
        original.playedDevCard(card)
        super.playedDevCard(card)
    }

    override fun playerMovedBandit(player: PlayerReference, hex: HexCoordinate) {
        original.playerMovedBandit(player, hex)
        super.playerMovedBandit(player, hex)
    }

    override fun gameStart(maxScore: Int) {
        original.gameStart(maxScore)
        super.gameStart(maxScore)
    }

    override fun gameEnd(winner: PlayerReference, points: Int) {
        original.gameEnd(winner, points)
        super.gameEnd(winner, points)
    }

    override fun updateBoard(b: Board) {
        original.updateBoard(b)
        super.updateBoard(b)
    }

    override fun placedRoad(player: PlayerReference, edgeCoordinate: EdgeCoordinate) {
        original.placedRoad(player, edgeCoordinate)
        super.placedRoad(player, edgeCoordinate)
    }

    override fun placedSettlement(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        original.placedSettlement(player, nodeCoordinate)
        super.placedSettlement(player, nodeCoordinate)
    }

    override fun placedCity(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        original.placedCity(player, nodeCoordinate)
        super.placedCity(player, nodeCoordinate)
    }

    /*   override fun registerListener(listener: PlayerListener) {
    }*/

    //TODO: need to go through ALL player methods

}
