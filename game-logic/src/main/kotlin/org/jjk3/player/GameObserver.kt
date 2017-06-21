package org.jjk3.player

import org.jjk3.board.*
import org.jjk3.gameplay.DiceRoll
import org.jjk3.gameplay.Turn

/**
 * Observer interface to watch the game.
 * Every player must implement this so they can monitor game events.
 */
interface GameObserver {

    /**
     * This is called by the admin when anyone rolls the dice
     * @param player the acting player
     * @param roll A tuple of the numbers that were rolled
     */
    fun playerRolled(player: PlayerReference, roll: DiceRoll): Unit {
    }

    /**
     * This is called by the admin whenever a player steals cards from another player
     * @param theif the player who took the cards
     * @param victim the player who lost cards
     * @param num_cards the randomNumber of cards stolen
     */
    fun playerStoleCard(theif: PlayerReference, victim: PlayerReference, num_cards: Int): Unit {
    }

    /**
     * Notify the observer that the game has begun
     */
    fun gameStart(maxScore: Int): Unit {
    }

    /**
     * Inform the observer that the game has finished.
     * @param winner the player who won
     * @param points the randomNumber of points they won ,.
     */
    fun gameEnd(winner: PlayerReference, points: Int): Unit {
    }

    /**
     * Inform this observer that a player has joined the game.
     * @param player the player that joined
     */
    fun playerJoined(player: PlayerReference) {
    }

    /**
     * Inform this observer that it is the given player's turn
     * @param player The player who's turn it is
     * @param turnClass
     */
    fun getTurn(player: PlayerReference, turnClass: Class<Turn>) {
    }

    /**
     *  Update this observer's version of the org.jjk3.board
     * @param board the  version of the org.jjk3.board
     */
    fun updateBoard(board: Board) {
    }

    /**
     * This is called by the admin anytime another player moves the bandit
     * @param player the player that moved the bandit
     * @param hex the hex that the bandit is now on.
     */
    fun playerMovedBandit(player: PlayerReference, hex: HexCoordinate) {
    }

    /**
     * Notify this observer that a road was placed
     * @param player The player that placed the road
     * @param x
     * @param y
     * @param edge
     */
    fun placedRoad(player: PlayerReference, edgeCoordinate: EdgeCoordinate) {
    }

    /**
     * Notify this observer that a settlement was placed
     * @param player The player that placed the settlement
     * @param x
     * @param y
     * @param node
     */
    fun placedSettlement(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
    }

    /**
     * Notify this observer that a city was placed
     * @param player The player that placed the city
     * @param x
     * @param y
     * @param node
     */
    fun placedCity(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
    }

    fun playerHasLongestRoad(player: PlayerReference) {}
    fun playerHasLargestArmy(player: PlayerReference) {}

}
