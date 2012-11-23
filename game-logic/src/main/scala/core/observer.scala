import scala.collection.Map
import scala.collection.mutable.HashMap
import org.apache.log4j._

package core {

    /**
     * Observer interface to watch the game.
     * Every player must implement this so they can monitor game events.
     */
    trait GameObserver {

        /**
         * This is called by the admin anytime any player recieves cards.
         * @param player the player that recieved the cards
         * @param cards a list of Card Classes
         */
        def player_received_cards(player: PlayerInfo, cards: List[Card]): Unit = {}

        /**
         * This is called by the admin when anyone rolls the dice
         * @param player the acting player
         * @param roll A tuple of the numbers that were rolled
         */
        def player_rolled(player: PlayerInfo, roll: (Int, Int)): Unit = {}

        /**
         * This is called by the admin whenever a player steals cards from another player
         * @param theif the player who took the cards
         * @param victim the player who lost cards
         * @param num_cards the number of cards stolen
         */
        def player_stole_card(theif: PlayerInfo, victim: PlayerInfo, num_cards: Int): Unit = {}

        /**
         * Notify the observer that the game has begun
         */
        def game_start: Unit = {}

        /**
         * Inform the observer that the game has finished.
         * @param winner the player who won
         * @param points the number of points they won with.
         */
        def game_end(winner: PlayerInfo, points: Int): Unit = {}

        /**
         * Inform this observer that a player has joined the game.
         * @param player the player that joined
         */
        def player_joined(player: PlayerInfo) = {}

        /**
         * Inform this observer that it is the given player's turn
         * @param player The player who's turn it is
         * @param turn_class
         */
        def get_turn(player: PlayerInfo, turn_class: Class[_]) = {}

        /**
         *  Update this observer's version of the board
         * @param board the new version of the board
         */
        def update_board(board: Any) = {}

        /**
         * This is called by the admin anytime another player moves the bandit
         * @param player the player that moved the bandit
         * @param new_hex the hex that the bandit is now on.
         */
        def player_moved_bandit(player: PlayerInfo, new_hex: Hex) = {}

        /**
         * Notify this observer that a road was placed
         * @param player The player that placed the road
         * @param x
         * @param y
         * @param edge
         */
        def placed_road(player: PlayerInfo, x: Int, y: Int, edge: Int) = {}

        /**
         * Notify this observer that a settlement was placed
         * @param player The player that placed the settlement
         * @param x
         * @param y
         * @param node
         */
        def placed_settlement(player: PlayerInfo, x: Int, y: Int, node: Int) = {}

        /**
         * Notify this observer that a city was placed
         * @param player The player that placed the city
         * @param x
         * @param y
         * @param node
         */
        def placed_city(player: PlayerInfo, x: Int, y: Int, node: Int) = {}

        def player_has_longest_road(player: PlayerInfo) = {}

        def player_has_largest_army(player: PlayerInfo) = {}

    }
}