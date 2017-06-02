package player

import com.google.common.util.concurrent.AtomicLongMap
import core.*
import org.apache.log4j.Logger
import java.util.*
import java.util.concurrent.Future


enum class GameState(val id: Int, val desc: String, val is_terminal_state: Boolean) {

    Waiting(0, "Waiting for players to join", false),
    Running(1, "Game is running", false),
    Finished(2, "Game is over", true),
    Stalemate(3, "Game ended in a stalemate", true);

    override fun toString() = "<state id=\"$id\" name=\"$name\" />"

}

abstract class UsesGameState {
    private var game_state: GameState = GameState.Waiting
    private val state_mutex = Object()

    private var log: Logger = Logger.getLogger(javaClass)

    var state: GameState
        get() {
            return synchronized(state_mutex) {
                game_state
            }
        }
        set(value: GameState) {
            synchronized(state_mutex) {
                log.info("Game State is changing to " + value)
                game_state = value
            }
        }

    fun is_game_done(): Boolean = state.is_terminal_state
    fun is_game_waiting(): Boolean = state == GameState.Waiting
    fun is_game_in_progress(): Boolean = state == GameState.Running
    /** Assert a state */
    fun assert_state(expected_state: GameState, msg: String = "") {
        if (state != expected_state)
            throw  IllegalStateException("Assertion Error: Expected turn state:$expected_state Found:$state. $msg")
    }

    /** Assert a state */
    fun assert_state(expected_states: List<GameState>, msg: String) {
        if (expected_states.contains(state))
            throw  IllegalStateException("Assertion Error: Expected turn state:$expected_states Found:$state. $msg")
    }

    fun assert_not_state(not_expected_state: GameState, msg: String = "") {
        if (state == not_expected_state)
            throw  IllegalStateException(
                    "Assertion Error: Turn state was expected to not be :$not_expected_state Found:$state. $msg")
    }
}

/**
 * Game Admin
 * This object oversees the game play and enforces the rules.
 */
open class Admin(
        val board: Board,
        val max_players: Int,
        val max_points: Int = 10,
        val turn_timeout_seconds: Int = 240,
        val game_timeout: Int = 1800)
    : UsesGameState() {

    companion object {
        val SELECT_CARDS_ROLLED_7 = 1
        val SELECT_CARDS_YEAR_OF_PLENTY = 2
        val SELECT_CARDS_RES_MONOPOLY = 3
    }

    val log: Logger = Logger.getLogger(javaClass)
    val MAXIMUM_ROUNDS = 2000
    var players: List<Player> = emptyList()
    var observers: List<GameObserver> = emptyList()
    var available_colors: List<String> = listOf("blue", "red", "white", "orange", "green", "brown")
    var previous_player_with_largest_army: PlayerReference? = null
    var previous_player_with_longest_road: PlayerReference? = null
    private var current_turn_obj: Turn? = null
    var times_skipped = AtomicLongMap.create<String>()

    var kicked_out: List<Player> = emptyList()
    var dice_handler: DiceHandler = StandardDiceHandler(this)
    var game_future: Future<Map<PlayerReference, Int>>? = null
    var all_futures: List<Future<Any?>> = emptyList() /*Keep track of all futures used in this admin. */
    fun getPrice(pieceClass: Purchaseable): List<Resource> = when (pieceClass) {
        is Settlement -> listOf(Resource.Wheat, Resource.Brick, Resource.Wood, Resource.Sheep)
        is City -> listOf(Resource.Ore, Resource.Ore, Resource.Ore, Resource.Wheat, Resource.Wheat)
        is Road -> listOf(Resource.Wood, Resource.Brick)
        is DevelopmentCard -> listOf(Resource.Ore, Resource.Sheep, Resource.Wheat)
        else -> emptyList()
    }

    /** Returns a list of the two dice rolls */
    fun rollDice(): Pair<Int, Int> {
        try {
            val (die1, die2) = dice_handler.get_dice()
            return roll_set_dice(Pair(die1.roll(), die2.roll()))
        } catch (e: Exception) {
            log.error(e, e)
            kickOut(currentTurn()!!.player, e)
            throw e
        }
    }

    fun currentTurn(): Turn? = current_turn_obj
    protected fun send_observer_msg(o: (GameObserver) -> Unit) {
        observers.forEach { o.invoke(it) }
    }

    fun kickOut(player: Player, error: Throwable) {
        // log.warn("TODO: Implement kicking out. " + error, error)
        kicked_out += player
    }

    /** Make the dice roll a specific value */
    fun roll_set_dice(roll: Pair<Int, Int>): Pair<Int, Int> {
        if (currentTurn() == null) throw  Exception("rollDice called ,out a currentTurn")
        val acting_player = currentTurn()!!.player
        log.debug("$acting_player rolled " + roll)
        val sum = roll.first + roll.second
        send_observer_msg { it.player_rolled(acting_player.info(), roll) }
        if (sum == 7) {
            dice_handler.handle_roll_7(roll)
        } else {
            //Give cards to all the players
            players.forEach { player ->
                val cards: List<Card> = board.get_cards(sum, player.color).map { ResourceCard(it) }
                if (cards.isNotEmpty()) {
                    player.add_cards(cards)
                    send_observer_msg { it.player_received_cards(player.info(), cards) }
                }
            }
        }
        dice_handler.dice_finished(roll)
        return roll
    }

    /** Get a player based on a color.  Returns null if not found. */
    fun getPlayer(color: String): Player? = players.find { it.color == color }

    /** Register a player or players , this game. */
    open fun register(registrant: Player): Unit {
        if (!is_game_in_progress()) {
            registrant.board = board
            val preferred_color = registrant.preferred_color
            if (is_game_waiting()) {
                if (preferred_color != null && available_colors.contains(preferred_color)) {
                    registrant.color = preferred_color
                    available_colors -= preferred_color
                } else {
                    val (chosen_color, _available_colors) = available_colors.remove_random()
                    available_colors = _available_colors
                    registrant.color = chosen_color
                }
            }
            registrant.update_board(board)
            registerObserver(registrant)
            players += registrant
            send_observer_msg { it.player_joined(registrant.info()) }
            log.info("Player joined: " + registrant)
            if (players.size == max_players) {
                play_game()
            }
        } else {
            log.info("Player cannot join the game at this time")
        }
    }

    /** Register an IObserver to watch this game. */
    fun registerObserver(observer: GameObserver) {
        observers += observer
    }

    fun checkForStalemate(): Boolean {
        log.debug("Checking for stalemate")
        players.forEach { player ->
            val color = player.color
            val settlementSpots = board.get_valid_settlement_spots(true, color)
            if (Math.min(settlementSpots.size, player.countSettlementsLeft()) > 0) {
                return false
            }
            val citySpots = board.get_valid_city_spots(color).size
            if (Math.min(citySpots, player.countCitiesLeft()) > 0) {
                return false
            }
            val roadSpots = board.get_valid_road_spots(color).size
            if (Math.min(roadSpots, player.countRoadsLeft()) > 0) {
                return false
            }
        }
        log.error("Game ended in a stalemate.")
        state = GameState.Stalemate
        return true
    }

    /** Get the score of the given player */
    fun getScore(player: Player): Int {
        var score = 0
        getPlayer(player.color)?.let { p ->
            if (board.has_longest_road(p.color)) {
                score += 2
            }
            who_has_largest_army()?.let { largestArmy ->
                if (largestArmy.color == p.color) {
                    score += 2
                }
            }
            board.all_nodes().forEach { n ->
                if (n.has_city() && n.city!!.color == p.color) {
                    score += n.city!!.points
                }
            }
        }
        return score + player.get_extra_victory_points()
    }

    fun getScores() = players.map { p: Player -> Pair(p.info(), getScore(p)) }.toMap()
    fun countResourceCards(playerReference: PlayerReference): Int {
        val player: Player = getPlayer(playerReference.color) ?: throw  IllegalArgumentException(
                "Could not find player , color:${playerReference.color} in $players")
        return Resource.values().map { player.countResources(it) }.sum()
    }

    fun count_all_resource_cards() = players.map { p -> listOf(p.info(), countResourceCards(p.info())) }
    fun count_dev_cards(playerReference: PlayerReference) = getPlayer(playerReference.color)?.count_dev_cards()
    /** Finds the player , the largest army, or nil if no one has it. */
    fun who_has_largest_army(): PlayerReference? {
        //The largest number of soldier cards
        val highest_count = players.map { countSoliders(it) }.max() ?: 0
        val who_has_the_most = players.filter { countSoliders(it) == highest_count }
        var result: PlayerReference? = null
        if (who_has_the_most.size == 1 && highest_count >= 3) {
            result = who_has_the_most.first().info()
        }
        result?.let { p ->
            if (p != previous_player_with_largest_army) {
                send_observer_msg { it.player_has_largest_army(p) }
                previous_player_with_largest_army = p
            }
        }
        return result
    }

    fun countSoliders(player: Player): Int = player.get_played_dev_cards().count { it is SoldierCard }

    /** Check to see if someone one.  If so, end the game */
    fun checkForWinner(): Boolean {
        checkForLongestRoad()
        getWinner()?.let { winner ->
            send_observer_msg { it.game_end(winner.info(), getScore(winner)) }
            state = GameState.Finished
            return true
        }
        return false
    }

    /**
     * Does this game have a winner yet.
     * If so, return the player that one, nil otherwise.
     */
    fun getWinner(): Player? = players.find { getScore(it) >= max_points }

    /**
     * An iterator for all other players
     * [player] the player to exclude
     */
    fun other_players(player: PlayerReference): List<Player> = players.filter { it.color != player.color }

    /**
     * Send a chat message to all users
     * [player] is who wrote the message
     */
    fun chat_msg(player: Player, msg: String) {
        getPlayer(player.color)?.let { p ->
            players.forEach { it.chat_msg(p.info(), msg) }
        }
    }

    /** sends a message to all players from the admin */
    fun admin_msg(msg: String) = players.forEach { it.chat_msg(null, msg) }

    /** Get information on who is winning, who has longest road and who has largest army */
    fun get_player_stats(): Map<String, Any?> {
        val leading_score = players.map { getScore(it) }.max()
        val leaders = players.filter { getScore(it) == leading_score }.map { it.info() }
        val la = who_has_largest_army()
        return mapOf(Pair("leaders", leaders),
                Pair("largest_army", la),
                Pair("longest_road", who_has_longest_road()))
    }

    /** returns the player infos that have the longest road */
    fun who_has_longest_road(): PlayerReference? = players.find { p -> board.has_longest_road(p.color) }?.info()

    private fun checkForLongestRoad() {
        who_has_longest_road()?.let { p ->
            if (p != previous_player_with_longest_road) {
                send_observer_msg { it.player_has_longest_road(p) }
                previous_player_with_longest_road = p
            }
        }
    }

    /**
     * Gets a piece from a player to place
     * raises an error if the player does not have the piece
     */
    fun <T> get_player_piece(player: Player, pieceType: Class<T>): T {
        val piece = player.piecesLeft.filterIsInstance(pieceType).firstOrNull()
        return piece ?: throw  RuleException("Player: ${player.full_name()} has no ${pieceType}s left")
    }

    fun offer_quote(quote: Quote, player_reference: PlayerReference) {
        if (quote.bidder != player_reference) {
            throw IllegalStateException()
        }
        currentTurn()?.received_quote(quote)
        currentTurn()?.player?.offer_quote(quote)
    }

    /*
    * Gets a List of quotes from the bank and other users
    * Optionally takes a block that iterates through each quote as they come
    * [player] The player asking for quotes
    */
    fun get_quotes(player: Player, wantList: List<Resource>, giveList: List<Resource>): List<Quote> {
        //var result = ThreadSafeList.(get_quotes_from_bank(player, wantList, giveList))
        var result = get_quotes_from_bank(player, wantList, giveList)

        //Add user quotes
        other_players(player.info()).forEach { p: Player ->
            val userQuotes = p.get_user_quotes(p.info(), wantList, giveList)
            userQuotes.forEach { quote: Quote ->
                try {
                    if (quote.bidder != p.info()) {
                        throw  RuleException(
                                "Player is offering a quote where the bidder is not himself. Player:" + p)
                    }
                    quote.validate(this)
                    result += quote
                } catch(e: Exception) {
                    log.error("User $p offered an invalid Quote. $e", e)
                }
            }
        }
        return result
    }

    /** Returns a List of Quote objects from the bank for a specific player */
    fun get_quotes_from_bank(player: Player, wantList: List<Resource>, giveList: List<Resource>): List<Quote> {
        //start , the bank's 4:1 offer
        var result: List<Quote> = emptyList()

        wantList.forEach { w: Resource ->
            giveList.forEach { g: Resource ->
                result += Quote(null, g, 4, w, 1)
                board.get_ports(player.color).forEach { p: Port ->
                    if ((p.kind != null && p.kind == g) || p.kind == null) {
                        val quote = Quote(null, g, p.rate, w, 1)
                        if (!result.contains(quote)) {
                            result += quote
                        }
                    }
                }
            }
        }
        result = result.filterNot { q -> player.countResources(q.receiveType) < q.receiveNum }.distinct()

        //Remove any redundant quotes
        //i.e. if result has a 2:1 quote, we don't need a 4:1 quote
        return result.filterNot { q -> result.any { it.isBetterQuote(q) } }
    }

    fun giveSetupTurn(player: Player): Turn = give_turn(SetupTurn(this, player, board), player)
    fun giveNormalTurn(player: Player): Turn = give_turn(Turn(this, player, board), player)
    /**
     * A helper method to give a turn to a player.
     * This method returns when the turn is finished.  It shouldn't throw any errors.
     * Any errors that occur during the turn should be handled here.
     */
    open fun give_turn(turn: Turn, player: Player): Turn {
        if (!is_game_done()) { //We need to check for the winner before we give the next player a turn
            log.debug("**Giving $turn to $player")
            current_turn_obj = turn

            try {
                //send_observer_msg { it.get_turn(player.info, turn.getClass) }
                //Give the turn to the player
                turn.state = TurnState.Active
                log.debug("player is taking turn: " + player)
                player.take_turn(turn, turn.is_setup)
                if (!turn.isDone()) {
                    log.warn("Turn SHOULD BE DONE.  it's:${turn.state}    $player   $turn")
                }
                Util.while_with_timeout(turn_timeout_seconds * 1000L) {
                    !turn.state.is_terminal_state
                }
                log.debug("Turn finished , state:" + currentTurn()?.state)
            } catch(e: Exception) {
                log.error("Error. CurrentTurnState=" + currentTurn()?.state + " : " + e, e)
                throw e
            }
            turn.rule_error?.let { throw it }
            log.debug("**give_turn() finished")
        }
        return turn
    }

    /**
     * Starts the game thread , the registered players.
     * Note: This method returns immediately.
     */
    fun play_game(): Map<PlayerReference, Int> {
        if (players.size <= 1) {
            throw IllegalStateException("2 or more players are required to start a game")
        }
        if (is_game_in_progress() || is_game_done()) {
            throw IllegalStateException("Game is already in progress")
        }
        this.state = GameState.Running
        try {
            log.info("Starting Game")
            log.info(" , " + players.size + " players")
            log.info(" max score:" + max_points)
            send_observer_msg { it.game_start() }

            var roundCount = 0
            for (p in players + players.reversed()) {
                giveSetupTurn(p).force_done()
            }
            while (!is_game_done()) {
                roundCount += 1
                for (p in players) {
                    if (!checkForWinner()) {
                        giveNormalTurn(p).force_done()
                    }
                }
                log.info("Round $roundCount finished.  Highest Score:" + players.map { p ->
                    getScore(p)
                }.max())
                if (roundCount > MAXIMUM_ROUNDS) {
                    log.error("Too many rounds have occurred: $roundCount. Effective Stalemate")
                    state = GameState.Stalemate
                }
                if (!is_game_done()) {
                    checkForStalemate()
                }
            }
            log.warn("game is done " + state)
        } catch (e: Exception) {
            log.error("Game Ending Exception: " + e, e)
        }
        log.info("Game Thread done.")
        return getScores()
    }
}

/*    include UsesGameState

    //An error occured on this player's turn.  KICK 'EM OUT! and replace them , a bot.
    fun kickOut(player, reason_or_error)={
      //player should always be a trusted player
      raise AdminError.("Server error.  kickOut was not called , a trusted player object") unless player.is_a?(TrustedPlayer)
      if (reason_or_error.is_a?(Exception))
        log.error("REPLACING PLAYER WITH BOT: //{player}. //{reason_or_error}")
        log.error("//{reason_or_error}\n     //{reason_or_error.backtrace.join("\n     ")}")
      else
        log.error("REPLACING PLAYER WITH BOT: //{player}. //{reason_or_error}")
      }
      kicked_out << player
      bot_player = SinglePurchasePlayer.copy(player, "Robo", "", self)
      trusted_bot = TrustedPlayer.(self, bot_player, log, player.color, player.piecesLeft(City), player.piecesLeft(Settlement), player.piecesLeft(Road), player.cards, player.get_played_dev_cards)

      replace_player(player, trusted_bot)
      begin
        timeout(2) do
          player.chat_msg(nil, "Error Occured: Now you've been kicked out")
        }       rescue -> err
        puts $!
      }     }
    private


    fun replace_player(old_player, _player)={
      log.warn("Replacing player old://{old_player} ://{_player}")
      log.debug("Current Turn Player: //{currentTurn().player}.  Old Player: //{old_player}")
      //at this point, the  player should already have all of the old's cards

      other_players(old_player) do |p|
        p.player_replaced_by(old_player.info, _player.info)
      }
      players[players.index(old_player)] = _player
      observers[observers.index(old_player)] = _player if observers.include?(old_player)
      currentTurn().rule_error = nil

      //if it was the old player's turn
      if (currentTurn().player == old_player)
        currentTurn().player = _player
        log.warn("**Giving Turn to REPLACEMENT PLAYER: //{_player}")
        give_turn(currentTurn, _player, false)
      }     }



  //This type of error indicates that the admin is in a bad state and that something internally went wrong.
  //This error is fatal and should NEVER happen.
  class AdminError < Exception

  }
*/

/** Represents a single die */
abstract class Die {
    abstract fun roll(): Int
}

object NormalDie : Die() {
    val r: Random = Random()
    override fun roll(): Int = r.nextInt(5) + 1
}

interface DiceHandler {
    fun get_dice(): Pair<Die, Die>
    fun handle_roll_7(roll: Pair<Int, Int>)
    fun dice_finished(roll: Pair<Int, Int>)
}

class StandardDiceHandler(val admin: Admin) : DiceHandler {
    val log = Logger.getLogger(javaClass)
    override fun get_dice(): Pair<Die, Die> {
        return Pair(NormalDie, NormalDie)
    }

    override fun dice_finished(roll: Pair<Int, Int>) {

    }

    override fun handle_roll_7(roll: Pair<Int, Int>) {
        //Each player must first get rid of half their cards if they more than 7
        admin.players.forEach { p ->
            try {
                if (p.count_resources() > 7) {
                    val how_many_cards_to_lose = p.count_resources() / 2
                    val chosen_cards = p.select_resource_cards(p.resource_cards().map { it.resource },
                            how_many_cards_to_lose,
                            Admin.SELECT_CARDS_ROLLED_7)
                    if (chosen_cards.size != how_many_cards_to_lose)
                        throw  RuleException(
                                "You did not select the right number of cards. expected:" + how_many_cards_to_lose + " found:" + chosen_cards.size)
                    p.del_cards(chosen_cards.map { ResourceCard(it) }, 4)
                }
            } catch (re: RuleException) {
                log.error("REPLACING PLAYER WITH BOT: " + p, re)
                admin.kickOut(p, re)
            }
        }

        //Then move the bandit
        log.info("Rolled a 7, move the bandit.")
        admin.board.tiles.values.find { it.has_bandit }?.let { current_bandit_hex ->
            val _hex = admin.currentTurn()!!.player.move_bandit(current_bandit_hex)
            admin.board.getTile(_hex.coords)?.let { local_hex ->
                admin.currentTurn()!!.move_bandit(local_hex)
            }
        }
    }

}
