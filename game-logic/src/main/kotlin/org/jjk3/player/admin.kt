package org.jjk3.player

import com.google.common.util.concurrent.AtomicLongMap
import org.apache.log4j.Logger
import org.jjk3.core.*
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

    fun isGameDone(): Boolean = state.is_terminal_state
    fun isGameWaiting(): Boolean = state == GameState.Waiting
    fun isGameInProgress(): Boolean = state == GameState.Running
    /** Assert a state */
    fun assertState(expectedState: GameState, msg: String = "") {
        if (state != expectedState)
            throw  IllegalStateException("Assertion Error: Expected turn state:$expectedState Found:$state. $msg")
    }

    fun assert_not_state(notExpectedState: GameState, msg: String = "") {
        if (state == notExpectedState) {
            throw  IllegalStateException(
                    "Assertion Error: Turn state was expected to not be :$notExpectedState Found:$state. $msg")
        }
    }
}

class LoggingObserver() : GameObserver {
    val log = Logger.getLogger(LoggingObserver::class.java)
    override fun playerReceivedCards(player: PlayerReference, cards: List<Card>) {
    }

    override fun playerRolled(player: PlayerReference, roll: Pair<Int, Int>) {
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

    override fun playerMovedBandit(player: PlayerReference, hex: Hex) {
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
    /** All futures used in this admin. */
    var all_futures: List<Future<Any?>> = emptyList()

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
        if (currentTurn() == null) throw  Exception("rollDice called without a currentTurn")
        val acting_player = currentTurn()!!.player
        log.debug("$acting_player rolled " + roll)
        val sum = roll.first + roll.second
        send_observer_msg { it.playerRolled(acting_player.ref(), roll) }
        if (sum == 7) {
            dice_handler.handle_roll_7(roll)
        } else {
            //Give cards to all the players
            players.forEach { player ->
                val cards: List<Card> = board.getCards(sum, player.color!!).map { ResourceCard(it) }
                if (cards.isNotEmpty()) {
                    player.giveCards(cards)
                    send_observer_msg { it.playerReceivedCards(player.ref(), cards) }
                }
            }
        }
        dice_handler.dice_finished(roll)
        return roll
    }

    /** Get a player based on a color.  Returns null if not found. */
    fun getPlayer(color: String): Player? = players.find { it.color == color }

    /** Register a player or players with this game. */
    open fun register(registrant: Player): Unit {
        if (!isGameInProgress()) {
            registrant.updateBoard(board)
            if (isGameWaiting()) {
                if (registrant is HasColorPreference){
                    val preferred_color = registrant.preferredColor
                    if (preferred_color != null && available_colors.contains(preferred_color)) {
                        registrant.color = preferred_color
                        available_colors -= preferred_color
                    }
                }
                if (registrant.color == null){
                    val (chosen_color, _available_colors) = available_colors.remove_random()
                    available_colors = _available_colors
                    registrant.color = chosen_color
                }
            }
            registerObserver(registrant)
            players += registrant
            send_observer_msg { it.playerJoined(registrant.ref()) }
            if (players.size == max_players) {
                playGame()
            }
        } else {
            throw IllegalStateException("Player cannot join the game at this time")
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
            val piecesForSale = board.getPiecesForSale(color!!)
            val settlementSpots = board.getValidSettlementSpots(color!!)
            if (Math.min(settlementSpots.size, piecesForSale.settlements.size()) > 0) {
                return false
            }
            val citySpots = board.getValidCitySpots(color!!).size
            if (Math.min(citySpots, piecesForSale.cities.size()) > 0) {
                return false
            }
            val roadSpots = board.getValidRoadSpots(color!!).size
            if (Math.min(roadSpots, piecesForSale.roads.size()) > 0) {
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
        getPlayer(player.color!!)?.let { p ->
            if (board.hasLongestRoad(p.color!!)) {
                score += 2
            }
            who_has_largest_army()?.let { largestArmy ->
                if (largestArmy.color == p.color) {
                    score += 2
                }
            }
            board.allNodes().forEach { n ->
                if (n.hasCity() && n.city!!.color == p.color) {
                    score += n.city!!.points
                }
            }
        }
        return score + player.getExtraVictoryPoints()
    }

    fun getScores() = players.map { p: Player -> Pair(p.ref(), getScore(p)) }.toMap()
    fun countResourceCards(playerReference: PlayerReference): Int {
        val player: Player = getPlayer(playerReference.color!!) ?: throw  IllegalArgumentException(
                "Could not find player , color:${playerReference.color} in $players")
        return Resource.values().map { player.countResources(it) }.sum()
    }

    fun countAllResourceCards() = players.map { p -> listOf(p.ref(), countResourceCards(p.ref())) }
    fun countDevelopmentCards(playerReference: PlayerReference) = getPlayer(playerReference.color!!)?.countDevelopmentCards()
    /** Finds the player , the largest army, or nil if no one has it. */
    fun who_has_largest_army(): PlayerReference? {
        //The largest randomNumber of soldier cards
        val highest_count = players.map { countSoliders(it) }.max() ?: 0
        val who_has_the_most = players.filter { countSoliders(it) == highest_count }
        var result: PlayerReference? = null
        if (who_has_the_most.size == 1 && highest_count >= 3) {
            result = who_has_the_most.first().ref()
        }
        result?.let { p ->
            if (p != previous_player_with_largest_army) {
                send_observer_msg { it.playerHasLargestArmy(p) }
                previous_player_with_largest_army = p
            }
        }
        return result
    }

    fun countSoliders(player: Player): Int = player.playedDevCards.count { it is SoldierCard }
    /** Check to see if someone one.  If so, end the game */
    fun checkForWinner(): Boolean {
        checkForLongestRoad()
        getWinner()?.let { winner ->
            send_observer_msg { it.gameEnd(winner.ref(), getScore(winner)) }
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
    fun otherPlayers(player: PlayerReference): List<Player> = players.filter { it.color != player.color }

    /** Get information on who is winning, who has longest road and who has largest army */
    fun getPlayerStats(): Map<String, Any?> {
        val leading_score = players.map { getScore(it) }.max()
        val leaders = players.filter { getScore(it) == leading_score }.map(Player::ref)
        val la = who_has_largest_army()
        return mapOf(Pair("leaders", leaders),
                Pair("largest_army", la),
                Pair("longest_road", whoHasLongestRoad()))
    }

    /** returns the player infos that have the longest road */
    fun whoHasLongestRoad(): PlayerReference? = players.find { p -> board.hasLongestRoad(p.color!!) }?.ref()

    private fun checkForLongestRoad() {
        whoHasLongestRoad()?.let { p ->
            if (p != previous_player_with_longest_road) {
                send_observer_msg { it.playerHasLongestRoad(p) }
                previous_player_with_longest_road = p
            }
        }
    }

    fun validateQuote(quote: Quote): Unit {
        if (quote.bidder != null) {
            val player = getPlayer(quote.bidder.color!!)!!
            if (player.countResources(quote.giveType) < quote.giveNum) {
                throw IllegalStateException(
                        "Bidder $quote.bidder does not have enough resources for this quote:${this} " +
                                "Bidder cards:${player.cards}")
            }
        }
    }

    /*
    * Gets a List of quotes from the bank and other users
    * Optionally takes a block that iterates through each quote as they come
    * [player] The player asking for quotes
    */
    fun getQuotes(player: Player, wantList: List<Resource>, giveList: List<Resource>): List<Quote> {
        var result = getQuotesFromBank(player, wantList, giveList)

        //Add user quotes
        otherPlayers(player.ref()).forEach { p: Player ->
            val userQuotes = p.getUserQuotes(p.ref(), wantList, giveList)
            userQuotes.forEach { quote: Quote ->
                try {
                    if (quote.bidder != p.ref()) {
                        throw RuleException("Player is offering a quote where the bidder is not himself. Player:$p")
                    }
                    validateQuote(quote)
                    result += quote
                } catch(e: Exception) {
                    log.error("User $p offered an invalid Quote. $e", e)
                }
            }
        }
        return result
    }

    /** Returns a List of Quote objects from the bank for a specific player */
    fun getQuotesFromBank(player: Player, wantList: List<Resource>, giveList: List<Resource>): List<Quote> {
        //start with the bank's 4:1 offer
        var result: List<Quote> = emptyList()

        wantList.forEach { w: Resource ->
            giveList.forEach { g: Resource ->
                result += Quote(null, g, 4, w, 1)
                board.getPorts(player.color!!).forEach { p: Port ->
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

    fun giveSetupTurn(player: Player): Turn = giveTurn(SetupTurn(this, player, board), player)
    fun giveNormalTurn(player: Player): Turn = giveTurn(Turn(this, player, board), player)
    /**
     * A helper method to give a turn to a player.
     * This method returns when the turn is finished.  It shouldn't throw any errors.
     * Any errors that occur during the turn should be handled here.
     */
    open fun giveTurn(turn: Turn, player: Player): Turn {
        //We need to check for the winner before we give the next player a turn
        if (!isGameDone()) {
            log.debug("**Giving $turn to $player")
            current_turn_obj = turn

            try {
                //send_observer_msg { it.getTurn(player.ref, turn.getClass) }
                //Give the turn to the player
                turn.state = TurnState.Active
                observers.forEach { it.getTurn(player.ref(), turn.javaClass) }
                player.takeTurn(turn)
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
            log.debug("**giveTurn() finished")
        }
        return turn
    }

    /**
     * Starts the game thread , the registered players.
     * Note: This method returns immediately.
     */
    fun playGame(): Map<PlayerReference, Int> {
        if (players.size <= 1) {
            throw IllegalStateException("2 or more players are required to start a game")
        }
        if (isGameInProgress() || isGameDone()) {
            throw IllegalStateException("Game is already in progress")
        }
        this.state = GameState.Running
        try {
            send_observer_msg { it.gameStart(max_points) }

            var roundCount = 0
            for (p in players + players.reversed()) {
                giveSetupTurn(p).forceDone()
            }
            while (!isGameDone()) {
                roundCount += 1
                for (p in players) {
                    if (!checkForWinner()) {
                        giveNormalTurn(p).forceDone()
                    }
                }
                log.info("Round $roundCount finished.  Highest Score:" + players.map { p ->
                    getScore(p)
                }.max())
                if (roundCount > MAXIMUM_ROUNDS) {
                    log.error("Too many rounds have occurred: $roundCount. Effective Stalemate")
                    state = GameState.Stalemate
                }
                if (!isGameDone()) {
                    checkForStalemate()
                }
            }
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
      trusted_bot = TrustedPlayer.(self, bot_player, log, player.color, player.piecesLeft(City), player.piecesLeft(Settlement), player.piecesLeft(Road), player.cards, player.getPlayedDevCards)

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

      otherPlayers(old_player) do |p|
        p.player_replaced_by(old_player.ref, _player.ref)
      }
      players[players.index(old_player)] = _player
      observers[observers.index(old_player)] = _player if observers.include?(old_player)
      currentTurn().rule_error = nil

      //if it was the old player's turn
      if (currentTurn().player == old_player)
        currentTurn().player = _player
        log.warn("**Giving Turn to REPLACEMENT PLAYER: //{_player}")
        giveTurn(currentTurn, _player, false)
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
                if (p.resourceCards().size > 7) {
                    val how_many_cards_to_lose = p.resourceCards().size / 2
                    val chosen_cards = p.selectResourceCards(p.resourceCards().map { it.resource },
                            how_many_cards_to_lose,
                            Admin.SELECT_CARDS_ROLLED_7)
                    if (chosen_cards.size != how_many_cards_to_lose)
                        throw  RuleException(
                                "You did not select the right randomNumber of cards. expected:$how_many_cards_to_lose found:${chosen_cards.size}")
                    p.takeCards(chosen_cards.map(::ResourceCard), Turn.ReasonToTakeCards.Rolled7)
                }
            } catch (re: RuleException) {
                log.error("REPLACING PLAYER WITH BOT: " + p, re)
                admin.kickOut(p, re)
            }
        }

        //Then move the bandit
        log.info("Rolled a 7, move the bandit.")
        admin.board.tiles.values.find { it.has_bandit }?.let { current_bandit_hex ->
            val _hex = admin.currentTurn()!!.player.moveBandit(current_bandit_hex)
            admin.board.getHex(_hex.coords).let { local_hex ->
                admin.currentTurn()!!.moveBandit(local_hex)
            }
        }
    }

}
