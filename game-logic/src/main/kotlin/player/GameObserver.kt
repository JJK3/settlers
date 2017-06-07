package player

import core.Card
import core.EdgeCoordinate
import core.Hex
import core.NodeCoordinate

/**
 * Observer interface to watch the game.
 * Every player must implement this so they can monitor game events.
 */
interface GameObserver {

    /**
     * This is called by the admin anytime any player recieves cards.
     * @param player the player that recieved the cards
     * @param cards a list of Card Classes
     */
    fun player_received_cards(player: PlayerReference, cards: List<Card>): Unit {
    }

    /**
     * This is called by the admin when anyone rolls the dice
     * @param player the acting player
     * @param roll A tuple of the numbers that were rolled
     */
    fun player_rolled(player: PlayerReference, roll: Pair<Int, Int>): Unit {
    }

    /**
     * This is called by the admin whenever a player steals cards from another player
     * @param theif the player who took the cards
     * @param victim the player who lost cards
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
     * @param winner the player who won
     * @param points the randomNumber of points they won ,.
     */
    fun game_end(winner: PlayerReference, points: Int): Unit {
    }

    /**
     * Inform this observer that a player has joined the game.
     * @param player the player that joined
     */
    fun player_joined(player: PlayerReference) {
    }

    /**
     * Inform this observer that it is the given player's turn
     * @param player The player who's turn it is
     * @param turn_class
     */
    fun get_turn(player: PlayerReference, turn_class: Class<Turn>) {
    }

    /**
     *  Update this observer's version of the board
     * @param board the  version of the board
     */
    fun update_board(board: Any) {
    }

    /**
     * This is called by the admin anytime another player moves the bandit
     * @param player the player that moved the bandit
     * @param hex the hex that the bandit is now on.
     */
    fun player_moved_bandit(player: PlayerReference, hex: Hex) {
    }

    /**
     * Notify this observer that a road was placed
     * @param player The player that placed the road
     * @param x
     * @param y
     * @param edge
     */
    fun placed_road(player: PlayerReference, edgeCoordinate: EdgeCoordinate) {
    }

    /**
     * Notify this observer that a settlement was placed
     * @param player The player that placed the settlement
     * @param x
     * @param y
     * @param node
     */
    fun placed_settlement(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
    }

    /**
     * Notify this observer that a city was placed
     * @param player The player that placed the city
     * @param x
     * @param y
     * @param node
     */
    fun placed_city(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
    }

    fun player_has_longest_road(player: PlayerReference) {}
    fun player_has_largest_army(player: PlayerReference) {}

}
