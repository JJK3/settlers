package core

import com.google.common.util.concurrent.AtomicLongMap
import org.apache.log4j.Logger
import java.util.*
import java.util.concurrent.*


class TrustedPlayer(
        admin: Admin,
        val original_player: Player,
        cities: Int = 4,
        settlements: Int = 5,
        roads: Int = 15,
        played_dev_cards: List<Card> = emptyList())
    : Player(original_player.first_name, original_player.last_name, admin, cities, settlements, roads) {

    private var my_board: Board? = null
    override fun get_user_quotes(player_info: PlayerInfo, wantList: List<Resource>,
                                 giveList: List<Resource>): List<Quote> =
            original_player.get_user_quotes(player_info, wantList, giveList)

    override fun move_bandit(old_hex: Hex): Hex =
            original_player.move_bandit(old_hex)

    override fun select_resource_cards(cards: List<Resource>, count: Int, reason: Int): List<Resource> =
            original_player.select_resource_cards(cards, count, reason)

    override fun select_player(players: List<PlayerInfo>, reason: Int): PlayerInfo =
            original_player.select_player(players, reason)

    override var color: String
        get() = super.color
        set(value) {
            original_player.color = value
            _color = value
        }
    override var board: Board?
        get() = super.board
        set(value) {
            original_player.board = value
            my_board = value
        }

    override fun add_cards(cards_to_add: List<Card>) {
        original_player.add_cards(cards_to_add)
        super.add_cards(cards_to_add)
    }

    override fun del_cards(cards_to_add: List<Card>, i: Int) {
        original_player.del_cards(cards_to_add, i)
        super.del_cards(cards_to_add, i)
    }

    override fun take_turn(turn: Turn, is_setup: Boolean) {
        original_player.take_turn(turn, is_setup)
    }

    override fun addPiecesLeft(pieceKlass: Class<out BoardPiece>, amount: Int) {
        original_player.addPiecesLeft(pieceKlass, amount)
        super.addPiecesLeft(pieceKlass, amount)
        /*if (original_player.get_pieces_left(pieceKlass) != original_player.get_pieces_left(pieceKlass)){
            throw  IllegalStateException("original:"+original_player.get_pieces_left(pieceKlass))
        }*/
    }

    override fun give_free_roads(num_roads: Int): Unit {
        original_player.give_free_roads(num_roads)
        super.give_free_roads(num_roads)
    }

    override fun remove_free_roads(num_roads: Int): Unit {
        original_player.remove_free_roads(num_roads)
        super.remove_free_roads(num_roads)
    }

    override fun played_dev_card(card: DevelopmentCard): Unit {
        original_player.played_dev_card(card)
        super.played_dev_card(card)
    }

    override fun chat_msg(player: PlayerInfo?, msg: String): Unit {
        original_player.chat_msg(player, msg)
        super.chat_msg(player, msg)
    }

    override fun offer_quote(quote: Quote) {
        original_player.offer_quote(quote)
        super.offer_quote(quote)
    }

    override fun add_extra_victory_points(points: Int): Int {
        original_player.add_extra_victory_points(points)
        return super.add_extra_victory_points(points)
    }

    override fun player_moved_bandit(player_info: PlayerInfo, hex: Hex) {
        original_player.player_moved_bandit(player_info, hex)
        super.player_moved_bandit(player_info, hex)
    }

    override fun game_start() {
        original_player.game_start()
        super.game_start()
    }

    override fun game_end(winner: PlayerInfo, points: Int) {
        original_player.game_end(winner, points)
        super.game_end(winner, points)
    }

    override fun update_board(b: Board) {
        original_player.update_board(b)
        super.update_board(b)
    }

    override fun placed_road(player_info: PlayerInfo, x: Int, y: Int, edge: Int) {
        original_player.placed_road(player_info, x, y, edge)
        super.placed_road(player_info, x, y, edge)
    }

    override fun placed_settlement(player_info: PlayerInfo, x: Int, y: Int, node: Int) {
        original_player.placed_settlement(player_info, x, y, node)
        super.placed_settlement(player_info, x, y, node)
    }

    override fun placed_city(player_info: PlayerInfo, x: Int, y: Int, node: Int) {
        original_player.placed_city(player_info, x, y, node)
        super.placed_city(player_info, x, y, node)
    }

    /*   override fun register_listener(listener: PlayerListener) {
    }*/

    //TODO: need to go through ALL player methods

}

enum class GameState(val id: Int, val desc: String, val is_terminal_state: Boolean) {

    Waiting(0, "Waiting for players to join", false),
    Running(1, "Game is running", false),
    Finished(2, "Game is over", true),
    Stalemate(3, "Game ended in a stalemate", true);

    override fun toString() = "<state id=\"" + id + "\" name=\"" + name + "\" />"

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

object SettlersExecutor {
    val executor = Executors.newFixedThreadPool(50)
}

/**
 * Game Admin
 * This object oversees the game play and enforces the rules.
 */
class Admin(
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

    private var log: Logger = Logger.getLogger(javaClass)
    var players: List<TrustedPlayer> = emptyList()
    var observers: List<GameObserver> = emptyList()
    var available_colors: List<String> = listOf("blue", "red", "white", "orange", "green", "brown")
    var previous_player_with_largest_army: PlayerInfo? = null
    var previous_player_with_longest_road: PlayerInfo? = null
    private var current_turn_obj: Turn? = null
    var times_skipped = AtomicLongMap.create<String>()

    val setup_turn_class = SetupTurn::class.java
    val normal_turn_class = Turn::class.java

    var kicked_out: List<Player> = emptyList()
    var dice_handler: DiceHandler = StandardDiceHandler(this)
    var game_future: Future<Map<PlayerInfo, Int>>? = null
    var all_futures: List<Future<Any?>> = emptyList() /*Keep track of all futures used in this admin. */
    fun get_price(pieceClass: Purchaseable): List<Resource> = when (pieceClass) {
        is Settlement -> listOf(Resource.Wheat, Resource.Brick, Resource.Wood, Resource.Sheep)
        is City -> listOf(Resource.Ore, Resource.Ore, Resource.Ore, Resource.Wheat, Resource.Wheat)
        is Road -> listOf(Resource.Wood, Resource.Brick)
        is DevelopmentCard -> listOf(Resource.Ore, Resource.Sheep, Resource.Wheat)
        else -> emptyList()
    }

    /** Returns a list of the two dice rolls */
    fun roll_dice(): Pair<Int, Int> {
        try {
            val (die1, die2) = dice_handler.get_dice()
            return roll_set_dice(Pair(die1.roll(), die2.roll()))
        } catch (e: Exception) {
            log.error(e, e)
            kick_out(current_turn()!!.player, e)
            throw e
        }
    }

    fun current_turn(): Turn? = current_turn_obj
    private fun send_observer_msg(o: (GameObserver) -> Unit) {
        observers.forEach { o.invoke(it) }
    }

    fun kick_out(player: Player, error: Throwable) {
        // log.warn("TODO: Implement kicking out. " + error, error)
        kicked_out += player
    }

    /** Make the dice roll a specific value */
    fun roll_set_dice(roll: Pair<Int, Int>): Pair<Int, Int> {
        if (current_turn() == null) throw  Exception("roll_dice called ,out a current_turn")
        val acting_player = current_turn()!!.player
        log.debug("$acting_player rolled " + roll)
        var sum = roll.first + roll.second
        send_observer_msg { it.player_rolled(acting_player.info(), roll) }
        //      dice_hist[sum] += 1 //increase the dice histogram
        if (sum == 7) {
            dice_handler.handle_roll_7(roll)
        } else {
            //Give cards to all the players
            players.forEach { player ->
                var cards: List<Card> = board.get_cards(sum, player.color).map { ResourceCard(it) }
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
    fun get_player(color: String): TrustedPlayer? = players.find { it.color == color }

    /** Register a player or players , this game. */
    fun register(registrants: List<Player>): Unit {
        registrants.forEach { initial_player ->
            //Players can only register before the game starts or if there are bots playing.
            var taking_over_bot: Player? = null
            val can_join = !is_game_in_progress() || this.players.any { it.original_player is Bot }
            if (can_join) {
                //Wrap the player in a trusted version of a player.  This makes sure that there is a local copy keeping track of points, pieces, etc.
                val registrant = TrustedPlayer(this, initial_player, 4, 5, 15)
                //if (initial_player.isInstanceOf<ProxyObject>){
                //	initial_player.json_connection.player = registrant
                //}
                registrant.board = board
                var preferred_color = initial_player.preferred_color //Only ask the player once

                //Assign a color
                if (is_game_waiting()) {
                    if (preferred_color != null && available_colors.contains(preferred_color)) {
                        registrant.color = preferred_color
                        available_colors -= preferred_color
                    } else {
                        //instead of being random, users should have a choice here
                        val (chosen_color, _available_colors) = available_colors.remove_random()
                        available_colors = _available_colors
                        registrant.color = chosen_color
                    }
                } else {
                    val bots = players.filter { it.original_player is Bot }
                    val bot_colors = bots.map { it.color }
                    if (preferred_color != null && bot_colors.contains(preferred_color)) {
                        taking_over_bot = get_player(preferred_color)
                        registrant.color = preferred_color
                    } else {
                        var color = bot_colors.pick_random()
                        registrant.color = color
                        taking_over_bot = get_player(color)
                    }
                }
                //tell the player how many pieces they have
                /*registrant.copy_pieces_left.forEach { (entry) ->
                    initial_player.addPiecesLeft(entry._1, entry._2)
                }*/
                registrant.update_board(board)

                if (taking_over_bot != null) {
                    //TODO replace_player(taking_over_bot, registrant)
                    players.forEach { p ->
                        registrant.player_joined(p.info())
                    } //Tell the  player about the other players
                } else {
                    players.forEach { p ->
                        registrant.player_joined(p.info())
                    } //Tell the  player about the other players
                    register_observer(registrant)
                    players += registrant
                    //tell the  player about all the other players
                    send_observer_msg { it.player_joined(registrant.info()) }
                }
                log.info("Player joined: " + initial_player)
                if (players.size == max_players) {
                    //game_mutex.synchronize { () ->
                    val future = start_game()
                    //}
                    return
                }
            } else {
                log.info("Player cannot join the game at this time")
            }
        }
    }

    /** This is called by the client to easily add bots */
    fun add_bot(name: String, last_name: String = "") {
        if (!is_game_in_progress()) {
            val bot: Bot = SinglePurchasePlayer(name, last_name, this, board)
            bot.delay = 2
            bot.pic = "http://jakrabbit.org/images/robot.jpg"
            register(listOf(bot))
        }
    }

    /** Register an IObserver to watch this game. */
    fun register_observer(observer: GameObserver) {
        //      observers :=  SafeWrapper(observer, log)
        observers += observer
    }

    fun check_for_stalemate(): Boolean {
        log.debug("Checking for stalemate")
        trusted_players().forEach { player ->
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

    fun get_players(): List<Player> = players.map { it.original_player }
    fun get_player_infos(): List<PlayerInfo> = players.map { it.original_player.info() }
    fun trusted_players() = players
    /** Get the score of the given player */
    fun get_score(player: Player): Int {
        var score = 0
        get_player(player.color)?.let { p ->
            if (board.has_longest_road(p.color))
                score += 2
            val largest_army = who_has_largest_army()
            if (largest_army != null && largest_army.color == p.color)
                score += 2
            board.all_nodes().forEach { n ->
                if (n.has_city() && n.city!!.color == p.color) {
                    score += n.city!!.points
                }
            }
        }
        return score + player.get_extra_victory_points()
    }

    fun get_scores() = get_players().map { p: Player -> Pair(p.info(), get_score(p)) }.toMap()
    fun count_resource_cards(playerInfo: PlayerInfo): Int {
        val player: TrustedPlayer = get_player(playerInfo.color) ?: throw  IllegalArgumentException(
                "Could not find player , color:${playerInfo.color} in $players")
        return Resource.values().map { player.countResources(it) }.sum()
    }

    fun count_all_resource_cards() = players.map { p -> listOf(p.info(), count_resource_cards(p.info())) }
    fun count_dev_cards(playerInfo: PlayerInfo) = get_player(playerInfo.color)?.count_dev_cards()
    /** Finds the player , the largest army, or nil if no one has it. */
    fun who_has_largest_army(): PlayerInfo? {
        //The largest number of soldier cards
        val highest_count = trusted_players().map { count_soliders(it) }.max() ?: 0
        val who_has_the_most = players.filter { count_soliders(it) == highest_count }
        var result: PlayerInfo? = null
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

    /** how many soldiers has this player played? */
    fun count_soliders(player: TrustedPlayer): Int = player.get_played_dev_cards().count { it is SoldierCard }

    /** Check to see if someone one.  If so, end the game */
    fun check_for_winner(): Boolean {
        //      game_mutex.synchronize{
        if (!is_game_done()) {
            val winner = has_winner()
            if (winner != null) {
                val points = get_score(winner)
                log.info("Game finished. winner: $winner points:$points")
                send_observer_msg { it.game_end(winner.info(), points) }
                current_turn()?.force_done()
                state = GameState.Finished
                return true
            }
        }
        return false
        //      }
    }

    /**
     * Does this game have a winner yet.
     * If so, return the player that one, nil otherwise.
     */
    fun has_winner(): Player? = trusted_players().map { it.original_player }.find { get_score(it) >= max_points }

    /**
     * An iterator for all other players
     * [player] the player to exclude
     */
    fun other_players(player: PlayerInfo): List<Player> = players.filter { it.color != player.color }

    /**
     * Send a chat message to all users
     * [player] is who wrote the message
     */
    fun chat_msg(player: Player, msg: String) {
        get_player(player.color)?.let { p ->
            players.forEach { it.chat_msg(p.info(), msg) }
        }
    }

    /** sends a message to all players from the admin */
    fun admin_msg(msg: String) = players.forEach { it.chat_msg(null, msg) }

    /** Get information on who is winning, who has longest road and who has largest army */
    fun get_player_stats(): Map<String, Any?> {
        val leading_score = players.map { get_score(it) }.max()
        val leaders = players.filter { get_score(it) == leading_score }.map { it.info() }
        val la = who_has_largest_army()
        return mapOf(Pair("leaders", leaders),
                Pair("largest_army", la),
                Pair("longest_road", who_has_longest_road()))
    }

    /** returns the player infos that have the longest road */
    fun who_has_longest_road(): PlayerInfo? {
        val playerWithLongestRoad = players.find { p -> board.has_longest_road(p.color) }?.info()
        playerWithLongestRoad?.let { p ->
            if (p != previous_player_with_longest_road) {
                send_observer_msg { it.player_has_longest_road(p) }
                previous_player_with_longest_road = p
            }
        }
        return playerWithLongestRoad
    }

    /**
     * Gets a piece from a player to place
     * raises an error if the player does not have the piece
     */
    fun <T> get_player_piece(player: Player, pieceType: Class<T>): T {
        val piece = player.piecesLeft.filterIsInstance(pieceType).firstOrNull()
        return piece ?: throw  RuleException("Player: ${player.full_name()} has no ${pieceType}s left")
    }

    fun offer_quote(quote: Quote, player_info: PlayerInfo) {
        val real_player = get_player(player_info.color)
        //if (player_info.color != player_info.json_connection.player.color){
        //  throw  RuleException("Attempting to mis-represent yourself.")
        //}
        if (quote.bidder != player_info) { //player_info.json_connection.player.info()
            throw  IllegalStateException()
        }
        current_turn()?.received_quote(quote)
        current_turn()?.player?.offer_quote(quote)
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
        //Create a  thread for each player
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

    fun give_turn_by_class(turn_class: Class<out Turn>, player: Player, synchronize: Boolean = true) {
        val turn = turn_class.getConstructor(Admin::class.java, Player::class.java, Board::class.java).newInstance(
                this, player, board, log)
        give_turn(turn, player, synchronize)
    }

    /**
     * A helper method to give a turn to a player.
     * This method returns when the turn is finished.  It shouldn't throw any errors.
     * Any errors that occur during the turn should be handled here.
     */
    fun give_turn(turn: Turn, player: Player, synchronize: Boolean = true) {
        if (!is_game_done()) { //We need to check for the winner before we give the next player a turn
            //if (synchronize)
            //   turn_mutex.lock
            log.debug("**Giving $turn to $player")
            current_turn_obj = turn
            try {
                //Give the player some time to make his/her move
                //timeout(turn_timeout) {
                val wrapper = Callable<Any?> {
                    try {
                        //send_observer_msg { it.get_turn(player.info, turn.getClass) }
                        //Give the turn to the player
                        turn.state = TurnState.Active
                        log.debug("player is taking turn: " + player)
                        player.take_turn(turn, turn.is_setup)
                        if (!turn.isDone()) {
                            log.warn("Turn SHOULD BE DONE.  it's:${turn.state}    $player   $turn")
                            if (player is TrustedPlayer) {
                                log.warn("Player is trusted:" + player.original_player)
                            }
                        }
                        Util.while_with_timeout(turn_timeout_seconds * 1000L) {
                            !turn.state.is_terminal_state
                        }
                        log.debug("Turn finished , state:" + current_turn()?.state)
                    } catch(e: Exception) {
                        log.error("Error. CurrentTurnState=" + current_turn()?.state + " : " + e, e)
                        throw e
                    }
                }
                val future = SettlersExecutor.executor.submit(wrapper)
                all_futures += future
                future.get(turn_timeout_seconds.toLong(), TimeUnit.SECONDS)
                turn.rule_error?.let { throw it }
            } catch (err: TimeoutException) {
                val skipped = times_skipped.incrementAndGet(player.color)
                log.error("Player's Turn Timed-out. turn:$turn Time skipped:$skipped player:$player", err)
                admin_msg(player.full_name() + " took too long.  A bot is filling in.")
                if (skipped == 3L) {
                    kick_out(player, IllegalStateException("Your turn was skipped too many times"))
                } else {
                    // TOOD:Finish the bots Make a temporary bot to take over for a turn.
                    /*            	val tmpBot = SinglePurchasePlayer.copy(player, "Tempbot","The Robot", self)
            val actingBot =  TrustedPlayer(this,  ActingBot(tmpBot, player), log, player.color, player.piecesLeft(City), player.piecesLeft(Settlement), player.piecesLeft(Road), player.cards, player.get_played_dev_cards)
            current_turn().player = actingBot
            actingBot.take_turn(current_turn, current_turn().is_setup)
            tmpBot = nil
            actingBot = nil
            */
                    kick_out(player, err) //for now
                }
            } catch (err: Exception) {
                try {
                    log.error(err, err) //until i implement the kick out
                    kick_out(player, err)
                } catch (e: Exception) {
                    log.error(e, e)
                }
            }

            //Force the turn to be done
            current_turn()?.force_done()
            log.debug("**give_turn() finished")
            //turn_mutex.unlock if synchronize
        }
    }

    /**
     * Starts the game thread , the registered players.
     * Note: This method returns immediately.
     */
    fun start_game(): Future<Map<PlayerInfo, Int>>? {
        //you need 2 or more players to start a game
        if (players.size <= 1) {
            return null
        }

        //if the game is already started or finished, this is a noop
        if (is_game_in_progress() || is_game_done()) {
            return null
        }

        this.state = GameState.Running
        val callable: Callable<Map<PlayerInfo, Int>> = Callable {
            try {
                log.info("Starting Game")
                //log.info("Using Board: //{board.name}")
                log.info(" , " + players.size + " players")
                log.info(" max score:" + max_points)
                send_observer_msg { it.game_start() }

                var roundCount = 0
                //setup
                for (p in players) {
                    give_turn_by_class(setup_turn_class, p)
                }
                for (p in players.reversed()) {
                    give_turn_by_class(setup_turn_class, p)
                }
                while (!is_game_done()) {
                    roundCount += 1
                    for (p in players) {
                        if (!check_for_winner()) {
                            give_turn_by_class(normal_turn_class, p)
                        }
                    }
                    log.info("Round " + roundCount + " finished.  Highest Score:" + players.map { p ->
                        get_score(p)
                    }.max())
                    if (roundCount > 2000) {
                        log.error("Too many rounds have occurred: " + roundCount + ". Effective Stalemate")
                        state = GameState.Stalemate
                    }
                    if (!is_game_done()) {
                        check_for_stalemate()
                    }
                }
                log.warn("game is done " + state)
            } catch (e: Exception) {
                log.error("Game Ending Exception: " + e, e)
            }
            log.info("Game Thread done.")
            get_scores()
        }
        game_future = SettlersExecutor.executor.submit(callable)
        //gameThread.abort_on_exception = true
        return game_future
    }

    fun shutdown() {
        game_future?.cancel(true)
        all_futures.forEach { f ->
            f.cancel(true)
        }
    }
}

/*    include UsesGameState
 
    //An error occured on this player's turn.  KICK 'EM OUT! and replace them , a bot.
    fun kick_out(player, reason_or_error)={
      //player should always be a trusted player
      raise AdminError.("Server error.  kick_out was not called , a trusted player object") unless player.is_a?(TrustedPlayer)
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
      log.debug("Current Turn Player: //{current_turn().player}.  Old Player: //{old_player}")
      //at this point, the  player should already have all of the old's cards
  
      other_players(old_player) do |p|
        p.player_replaced_by(old_player.info, _player.info)
      }   
      players[players.index(old_player)] = _player
      observers[observers.index(old_player)] = _player if observers.include?(old_player)
      current_turn().rule_error = nil
      
      //if it was the old player's turn
      if (current_turn().player == old_player)
        current_turn().player = _player
        log.warn("**Giving Turn to REPLACEMENT PLAYER: //{_player}")
        give_turn(current_turn, _player, false)
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
        admin.trusted_players().forEach { p ->
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
                admin.kick_out(p, re)
            }
        }

        //Then move the bandit
        log.info("Rolled a 7, move the bandit.")
        admin.board.tiles.values.find { it.has_bandit }?.let { current_bandit_hex ->
            val _hex = admin.current_turn()!!.player.move_bandit(current_bandit_hex)
            admin.board.getTile(_hex.coords)?.let { local_hex ->
                admin.current_turn()!!.move_bandit(local_hex)
            }
        }
    }

}
