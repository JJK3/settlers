package org.jjk3.player

import org.jjk3.core.Card
import org.jjk3.core.EdgeCoordinate
import org.jjk3.core.Hex
import org.jjk3.core.NodeCoordinate

/**
 * Observer interface to watch the game.
 * Every org.jjk3.player must implement this so they can monitor game events.
 */
interface GameObserver {

    /**
     * This is called by the admin anytime any org.jjk3.player recieves cards.
     * @param player the org.jjk3.player that recieved the cards
     * @param cards a list of Card Classes
     */
    fun player_received_cards(player: PlayerReference, cards: List<Card>): Unit {
    }

    /**
     * This is called by the admin when anyone rolls the dice
     * @param player the acting org.jjk3.player
     * @param roll A tuple of the numbers that were rolled
     */
    fun player_rolled(player: PlayerReference, roll: Pair<Int, Int>): Unit {
    }

    /**
     * This is called by the admin whenever a org.jjk3.player steals cards from another org.jjk3.player
     * @param theif the org.jjk3.player who took the cards
     * @param victim the org.jjk3.player who lost cards
     * @param num_cards the randomNumber of cards stolen
     */
    fun player_stole_card(theif: PlayerReference, victim: PlayerReference, num_cards: Int): Unit {
    }

    /**
     * Notify the observer that the game has begun
     */
    fun game_start(): Unit {
    }

    /**
     * Inform the observer that the game has finished.
     * @param winner the org.jjk3.player who won
     * @param points the randomNumber of points they won ,.
     */
    fun game_end(winner: PlayerReference, points: Int): Unit {
    }

    /**
     * Inform this observer that a org.jjk3.player has joined the game.
     * @param player the org.jjk3.player that joined
     */
    fun player_joined(player: PlayerReference) {
    }

    /**
     * Inform this observer that it is the given org.jjk3.player's turn
     * @param player The org.jjk3.player who's turn it is
     * @param turn_class
     */
    fun get_turn(player: PlayerReference, turn_class: Class<Turn>) {
    }

    /**
     *  Update this observer's version of the org.jjk3.board
     * @param board the  version of the org.jjk3.board
     */
    fun update_board(board: Any) {
    }

    /**
     * This is called by the admin anytime another org.jjk3.player moves the bandit
     * @param player the org.jjk3.player that moved the bandit
     * @param hex the hex that the bandit is now on.
     */
    fun player_moved_bandit(player: PlayerReference, hex: Hex) {
    }

    /**
     * Notify this observer that a road was placed
     * @param player The org.jjk3.player that placed the road
     * @param x
     * @param y
     * @param edge
     */
    fun placed_road(player: PlayerReference, edgeCoordinate: EdgeCoordinate) {
    }

    /**
     * Notify this observer that a settlement was placed
     * @param player The org.jjk3.player that placed the settlement
     * @param x
     * @param y
     * @param node
     */
    fun placed_settlement(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
    }

    /**
     * Notify this observer that a city was placed
     * @param player The org.jjk3.player that placed the city
     * @param x
     * @param y
     * @param node
     */
    fun placed_city(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
    }

    fun player_has_longest_road(player: PlayerReference) {}
    fun player_has_largest_army(player: PlayerReference) {}

}
