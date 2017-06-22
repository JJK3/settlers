package org.jjk3.gameplay

import com.google.common.util.concurrent.AtomicLongMap
import org.apache.log4j.Logger
import org.jjk3.board.*
import org.jjk3.player.GameObserver
import org.jjk3.player.HasColorPreference
import org.jjk3.player.Player
import org.jjk3.player.PlayerReference
import java.util.concurrent.Future

/**
 * Game Admin
 * This object oversees the game play and enforces the rules.
 */
open class Admin(
        open val board: Board,
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
    open var players: List<Player> = emptyList()
    open var observers: List<GameObserver> = emptyList()
    var available_colors: List<String> = listOf("blue", "red", "white", "orange", "green", "brown")
    var previous_player_with_largest_army: PlayerReference? = null
    var previous_player_with_longest_road: PlayerReference? = null
    private var current_turn_obj: Turn? = null
    var times_skipped = AtomicLongMap.create<String>()

    var kicked_out: List<Player> = emptyList()
    var game_future: Future<Map<PlayerReference, Int>>? = null
    /** All futures used in this admin. */
    var all_futures: List<Future<Any?>> = emptyList()

    fun currentTurn(): Turn? = current_turn_obj
    protected fun send_observer_msg(o: (GameObserver) -> Unit) {
        observers.forEach { o.invoke(it) }
    }

    fun kickOut(player: Player, error: Throwable) {
        // log.warn("TODO: Implement kicking out. " + error, error)
        kicked_out += player
    }

    /** Get a player based on a color.  Returns null if not found. */
    open fun getPlayer(color: String): Player? = players.find { it.color == color }

    /** Register a player or players with this game. */
    open fun register(registrant: Player): Unit {
        if (! isGameInProgress()) {
            registrant.updateBoard(board)
            if (isGameWaiting()) {
                if (registrant is HasColorPreference) {
                    val preferred_color = registrant.preferredColor
                    if (preferred_color != null && available_colors.contains(preferred_color)) {
                        registrant.color = preferred_color
                        available_colors -= preferred_color
                    }
                }
                if (registrant.color == null) {
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
            val piecesForSale = board.getPiecesForSale(color !!)
            val settlementSpots = board.getValidSettlementSpots(color !!)
            if (Math.min(settlementSpots.size, piecesForSale.settlements.size()) > 0) {
                return false
            }
            val citySpots = board.getValidCitySpots(color !!).size
            if (Math.min(citySpots, piecesForSale.cities.size()) > 0) {
                return false
            }
            val roadSpots = board.getValidRoadSpots(color !!).size
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
        getPlayer(player.color !!)?.let { p ->
            if (board.hasLongestRoad(p.color !!)) {
                score += 2
            }
            who_has_largest_army()?.let { largestArmy ->
                if (largestArmy.color == p.color) {
                    score += 2
                }
            }
            board.allNodes().forEach { n ->
                if (n.hasCity() && n.city !!.color == p.color) {
                    score += n.city !!.points
                }
            }
        }
        return score + player.getExtraVictoryPoints()
    }

    fun getScores() = players.map { p: Player -> Pair(p.ref(), getScore(p)) }.toMap()
    fun countResourceCards(playerReference: PlayerReference): Int {
        val player: Player = getPlayer(playerReference.color !!) ?: throw  IllegalArgumentException(
                "Could not find player , color:${playerReference.color} in $players")
        return Resource.values().map { player.countResources(it) }.sum()
    }

    fun countAllResourceCards() = players.map { p -> listOf(p.ref(), countResourceCards(p.ref())) }
    fun countDevelopmentCards(playerReference: PlayerReference) = getPlayer(
            playerReference.color !!)?.countDevelopmentCards()

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
    fun whoHasLongestRoad(): PlayerReference? = players.find { p -> board.hasLongestRoad(p.color !!) }?.ref()

    private fun checkForLongestRoad() {
        whoHasLongestRoad()?.let { p ->
            if (p != previous_player_with_longest_road) {
                send_observer_msg { it.playerHasLongestRoad(p) }
                previous_player_with_longest_road = p
            }
        }
    }

    open fun validateQuote(quote: Quote): Unit {
        if (quote.bidder != null) {
            val player = getPlayer(quote.bidder.color !!) !!
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
    open fun getQuotes(player: Player, wantList: Set<Resource>, giveList: Set<Resource>): Set<Quote> {
        var result = getQuotesFromBank(player, wantList, giveList)

        //Add user quotes
        otherPlayers(player.ref()).forEach { p: Player ->
            val userQuotes = p.getUserQuotes(p.ref(), wantList, giveList)
            userQuotes.forEach { quote: Quote ->
                try {
                    if (quote.bidder != p.ref()) {
                        throw RuleException(
                                "Player is offering a quote where the bidder is not himself. Player:$p")
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
    fun getQuotesFromBank(player: Player, wantList: Set<Resource>, giveList: Set<Resource>): Set<Quote> {
        //start with the bank's 4:1 offer
        var result: Set<Quote> = emptySet()

        wantList.forEach { w: Resource ->
            giveList.forEach { g: Resource ->
                result += Quote(null, g, 4, w, 1)
                board.getPorts(player.color !!).forEach { p: Port ->
                    if ((p.kind != null && p.kind == g) || p.kind == null) {
                        val quote = Quote(null, g, p.rate, w, 1)
                        if (! result.contains(quote)) {
                            result += quote
                        }
                    }
                }
            }
        }
        result = result.filterNot { q -> player.countResources(q.receiveType) < q.receiveNum }.toSet()

        //Remove any redundant quotes
        //i.e. if result has a 2:1 quote, we don't need a 4:1 quote
        return result.filterNot { q -> result.any { it.isBetterQuote(q) } }.toSet()
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
        if (! isGameDone()) {
            log.debug("**Giving $turn to $player")
            current_turn_obj = turn

            try {
                //send_observer_msg { it.getTurn(player.ref, turn.getClass) }
                //Give the turn to the player
                turn.state = TurnState.Active
                observers.forEach { it.getTurn(player.ref(), turn.javaClass) }
                player.takeTurn(turn)
                if (! turn.isDone()) {
                    log.warn("Turn SHOULD BE DONE.  it's:${turn.state}    $player   $turn")
                }
                Util.while_with_timeout(turn_timeout_seconds * 1000L) {
                    ! turn.state.isTerminal
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
            while (! isGameDone()) {
                roundCount += 1
                for (p in players) {
                    if (! checkForWinner()) {
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
                if (! isGameDone()) {
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

