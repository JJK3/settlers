package org.jjk3.gameplay

import org.apache.log4j.Logger
import org.jjk3.gameplay.DiceRoll
import org.jjk3.board.EdgeCoordinate
import org.jjk3.board.HexCoordinate
import org.jjk3.board.NodeCoordinate
import org.jjk3.player.GameObserver
import org.jjk3.player.PlayerReference

class LoggingObserver() : GameObserver {
    val log = Logger.getLogger(LoggingObserver::class.java)
    override fun playerRolled(player: PlayerReference, roll: DiceRoll) {
        log.info("$player rolled $roll")
    }

    override fun playerStoleCard(theif: PlayerReference, victim: PlayerReference, num_cards: Int) {
        log.info("$theif stole $num_cards card(s) from $victim")
    }

    override fun gameStart(maxScore: Int) {
        log.info("Game is starting.  Maximum points: $maxScore")
    }

    override fun gameEnd(winner: PlayerReference, points: Int) {
        log.info("Game is finished.  $winner won with $points points!")
    }

    override fun playerJoined(player: PlayerReference) {
        log.info("$player joined the game")
    }

    override fun getTurn(player: PlayerReference, turnClass: Class<Turn>) {
        log.debug("$player is starting their turn")
    }

    override fun playerMovedBandit(player: PlayerReference, hex: HexCoordinate) {
        log.info("$player moved the bandit to $hex")
    }

    override fun placedRoad(player: PlayerReference, edgeCoordinate: EdgeCoordinate) {
        log.info("$player placed a road at ${edgeCoordinate}")
    }

    override fun placedSettlement(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        log.info("$player placed a settlement an $nodeCoordinate")
    }

    override fun placedCity(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        log.info("$player placed a city an $nodeCoordinate")
    }

    override fun playerHasLongestRoad(player: PlayerReference) {
        log.info("$player has longest road")
    }

    override fun playerHasLargestArmy(player: PlayerReference) {
        log.info("$player has largest army")
    }
}