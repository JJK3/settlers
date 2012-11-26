package core

import org.apache.log4j._
import UtilList._
import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.Future
import java.util.ArrayList
import org.scalatest.tools.RunningState
import java.util.Observer

/** This error denotes that something occurred against the rules. */
class RuleException(msg: String) extends Exception(msg)

/** This is a safety wrapper class where any exceptions that occur just get logged */
class SafeWrapper(val obj: Any, val log: Logger) {
    /*    def respond_to?(method)={
      obj.respond_to?(method)
    }   
    def method_missing(method, *args)={
      begin
        obj.send(method, *args)
      rescue
        log.error("//{$!}\n     //{$!.backtrace.join("\n     ")}")
      }
      */
}

class TrustedPlayer(
    admin: Admin,
    val original_player: Player,
    log: Logger,
    color: String,
    cities: Int = 4,
    settlements: Int = 5,
    roads: Int = 15,
    played_dev_cards: List[Card] = Nil,
    board: Board = null)
    extends Player(original_player.first_name, original_player.last_name, admin, log, cities, settlements, roads, board) {

    def get_user_quotes(player_info: PlayerInfo, wantList: List[Resource], giveList: List[Resource]): List[Quote] =
        original_player.get_user_quotes(player_info, wantList, giveList)

    def move_bandit(old_hex: Hex): Hex =
        original_player.move_bandit(old_hex)

    def select_resource_cards(cards: List[Resource], count: Int, reason: Int): List[Resource] =
        original_player.select_resource_cards(cards, count, reason)

    def select_player(players: List[PlayerInfo], reason: Int): PlayerInfo =
        original_player.select_player(players, reason)

    override def color_=(color: String) {
        original_player.color = color
        _color = color
    }

    override def board_=(b: Board) {
        original_player.board = b
        _board = b
    }

    override def add_cards(cards_to_add: List[Card]) = {
        original_player.add_cards(cards_to_add)
        super.add_cards(cards_to_add)
    }

    override def del_cards(cards_to_add: List[Card], i: Int) = {
        original_player.del_cards(cards_to_add, i)
        super.del_cards(cards_to_add, i)
    }

    override def take_turn(turn: Turn, is_setup: Boolean) = {
        original_player.take_turn(turn, is_setup)
    }

    override def addPiecesLeft(pieceKlass: Class[_ <: BoardPiece], amount: Int) {
        original_player.addPiecesLeft(pieceKlass, amount);
        super.addPiecesLeft(pieceKlass, amount);
        /*if (original_player.get_pieces_left(pieceKlass) != original_player.get_pieces_left(pieceKlass)){
            throw new IllegalStateException("original:"+original_player.get_pieces_left(pieceKlass))
        }*/
    }

    override def give_free_roads(num_roads: Int): Unit = {
        original_player.give_free_roads(num_roads);
        super.give_free_roads(num_roads);
    }

    override def remove_free_roads(num_roads: Int): Unit = {
        original_player.remove_free_roads(num_roads);
        super.remove_free_roads(num_roads);

    }

    override def played_dev_card(card: DevelopmentCard): Unit = {
        original_player.played_dev_card(card);
        super.played_dev_card(card);

    }

    override def chat_msg(player: PlayerInfo, msg: String): Unit = {
        original_player.chat_msg(player, msg);
        super.chat_msg(player, msg);
    }

    override def offer_quote(quote: Quote) = {
        original_player.offer_quote(quote);
        super.offer_quote(quote);
    }

    override def add_extra_victory_points(points: Int) = {
        original_player.add_extra_victory_points(points);
        super.add_extra_victory_points(points);
    }

    override def player_moved_bandit(player_info: PlayerInfo, new_hex: Hex) = {
        original_player.player_moved_bandit(player_info, new_hex);
        super.player_moved_bandit(player_info, new_hex);
    }

    override def game_start = {
        original_player.game_start;
        super.game_start;
    }

    override def game_end(winner: PlayerInfo, points: Int) {
        original_player.game_end(winner, points);
        super.game_end(winner, points);
    }

    override def update_board(b: Board) {
        original_player.update_board(b);
        super.update_board(b);
    }
    override def placed_road(player_info: PlayerInfo, x: Int, y: Int, edge: Int) {
        original_player.placed_road(player_info, x, y, edge);
        super.placed_road(player_info, x, y, edge);
    }

    override def placed_settlement(player_info: PlayerInfo, x: Int, y: Int, node: Int) {
        original_player.placed_settlement(player_info, x, y, node);
        super.placed_settlement(player_info, x, y, node);
    }

    override def placed_city(player_info: PlayerInfo, x: Int, y: Int, node: Int) {
        original_player.placed_city(player_info, x, y, node);
        super.placed_city(player_info, x, y, node);
    }

    /*   override def register_listener(listener: PlayerListener) {
    }*/

    //TODO: need to go through ALL player methods 

}

class GameState(val id: Int, val name: String, val desc: String, val is_terminal_state: Boolean) {
    /*  def get_by_id(id)
    KNOWN_STATES[id]
  end
	 */
    override def equals(that: Any) = that match {
        case other: GameState => other.id == id
        case _ => false
    }

    override def toString = "<state id=\"" + id + "\" name=\"" + name + "\" />"

}
object GameState {
    val Waiting = new GameState(0, "Waiting", "Waiting for players to join", false)
    val Running = new GameState(1, "Running", "Game is running", false)
    val Finished = new GameState(2, "Finished", "Game is over", true)
    val Stalemate = new GameState(3, "Stalemate", "Game ended in a stalemate", true)
}

trait UsesGameState {
    private var game_state: GameState = GameState.Waiting
    private val state_mutex = new Mutex()

    var log: Logger

    def set_state(s: GameState) = {
        state_mutex.synchronize { () =>
            log.info("Game State is changing to " + s)
            game_state = s
        }
    }

    def state(): GameState = {
        state_mutex.synchronize { () =>
            game_state
        }
    }

    def is_game_done() = { state.is_terminal_state }

    def is_game_waiting() = { state == GameState.Waiting }

    def is_game_in_progress() = { state == GameState.Running }

    /** Assert a state */
    def assert_state(expected_state: GameState, msg: String = "") = {
        if (state != expected_state)
            throw new IllegalStateException("Assertion Error:  Excpected turn state:" + expected_state + " Found:" + state + ". " + msg)
    }

    /** Assert a state */
    def assert_state(expected_states: List[GameState], msg: String) = {
        if (expected_states.contains(state))
            throw new IllegalStateException("Assertion Error:  Excpected turn state:" + expected_states + " Found:" + state + ". " + msg)
    }

    def assert_not_state(not_expected_state: GameState, msg: String = "") = {
        if (state == not_expected_state)
            throw new IllegalStateException("Assertion Error:  Turn state was expected to not be :" + not_expected_state + " Found:" + state + ". " + msg)
    }
}

object Admin {
    val SELECT_CARDS_ROLLED_7 = 1;
    val SELECT_CARDS_YEAR_OF_PLENTY = 2;
    val SELECT_CARDS_RES_MONOPOLY = 3;

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
    extends UsesGameState {

    override var log: Logger = Logger.getLogger(classOf[Admin])
    var players: List[TrustedPlayer] = Nil
    var observers: List[GameObserver] = Nil
    var available_colors: List[String] = List("blue", "red", "white", "orange", "green", "brown")
    var previous_player_with_largest_army: PlayerInfo = null
    var previous_player_with_longest_road: PlayerInfo = null
    protected var current_turn_obj: Turn = null
    var times_skipped = Map[String, Int]().withDefaultValue(0)
    val setup_turn_class = classOf[SetupTurn]
    val normal_turn_class = classOf[Turn]
    var kicked_out: List[Player] = Nil
    var dice_handler: DiceHandler = new StandardDiceHandler(this)
    var game_future: Future[Map[PlayerInfo, Int]] = null
    var all_futures: List[Future[Any]] = Nil /*Keep track of all futures used in this admin. */

    def get_price(pieceClass: Purchaseable): List[Resource] = {
        pieceClass match {
            case i: Settlement => List(WheatType, BrickType, WoodType, SheepType)
            case i: City => List(OreType, OreType, OreType, WheatType, WheatType)
            case i: Road => List(WoodType, BrickType)
            case i: DevelopmentCard => List(OreType, SheepType, WheatType)
        }
    }

    /** Returns a list of the two dice rolls */
    def roll_dice: (Int, Int) = {
        try {
            val (die1, die2) = dice_handler.get_dice
            return roll_set_dice((die1.roll, die2.roll))
        } catch {
            case e: Exception =>
                log.error(e, e);
                kick_out(current_turn.player, e)
                throw e
        }
    }

    def current_turn: Turn = current_turn_obj

    private def send_observer_msg(o: GameObserver => Unit) = {
        observers.iterate_threaded(true, 5 * 60, false) { o.apply(_) }
        //observers.foreach{ o.apply(_) }
    }

    def kick_out(player: Player, error: Throwable) = {
        // log.warn("TODO: Implement kicking out. " + error, error)
        kicked_out = kicked_out :+ player
    }

    /** Make the dice roll a specific value */
    def roll_set_dice(roll: (Int, Int)) = {
        if (current_turn == null) throw new Exception("roll_dice called without a current_turn")
        val acting_player = current_turn.player
        log.debug(acting_player + " rolled " + roll)
        var sum = roll._1 + roll._2
        send_observer_msg { _.player_rolled(acting_player.info, roll) }
        //      dice_hist[sum] += 1 //increase the dice histogram
        if (sum == 7) {
            dice_handler.handle_roll_7(roll)
        } else {
            //Give cards to all the players
            players.foreach { player =>
                var cards: List[Card] = board.get_cards(sum, player.color);
                if (cards.size > 0) {
                    player.add_cards(cards)
                    send_observer_msg { _.player_received_cards(player.info, cards) }
                }
            }
        }
        dice_handler.dice_finished(roll)
        roll
    }

    /** Get a player based on a color.  Returns null if not found. */
    def get_player(color: String): TrustedPlayer = players.find { _.color == color }.getOrElse(null)

    /** Register a player or players with this game. */
    def register(registrants: Player*): Unit = {
        registrants.foreach { initial_player =>
            //Players can only register before the game starts or if there are bots playing.
            var taking_over_bot: Player = null
            val can_join = !is_game_in_progress || this.players.exists { _.original_player.isInstanceOf[Bot] }
            if (can_join) {
                //Wrap the player in a trusted version of a player.  This makes sure that there is a local copy keeping track of points, pieces, etc.
                val registrant = new TrustedPlayer(this, initial_player, log, null, 4, 5, 15, Nil, board)
                //if (initial_player.isInstanceOf[ProxyObject]){
                //	initial_player.json_connection.player = registrant
                //}           

                var preferred_color = initial_player.preferred_color //Only ask the player once

                //Assign a color
                if (is_game_waiting()) {
                    if (preferred_color != null && available_colors.contains(preferred_color)) {
                        registrant.color = preferred_color
                        available_colors -= preferred_color
                    } else {
                        //instead of being random, users should have a choice here
                        val (chosen_color, new_available_colors) = available_colors.remove_random
                        available_colors = new_available_colors
                        registrant.color = chosen_color
                    }
                } else {
                    val bots = players.filter { _.original_player.isInstanceOf[Bot] }
                    val bot_colors = bots.map { _.color }
                    if (bot_colors.contains(preferred_color)) {
                        taking_over_bot = get_player(preferred_color)
                        registrant.color = preferred_color
                    } else {
                        var color = bot_colors.pick_random
                        registrant.color = color
                        taking_over_bot = get_player(color)
                    }
                }
                //tell the player how many pieces they have
                /*registrant.copy_pieces_left.foreach { (entry) =>
                    initial_player.addPiecesLeft(entry._1, entry._2)
                }*/
                registrant.update_board(board)

                if (taking_over_bot != null) {
                    //TODO replace_player(taking_over_bot, registrant)
                    players.foreach { p => registrant.player_joined(p.info) } //Tell the new player about the other players
                } else {
                    players.foreach { p => registrant.player_joined(p.info) } //Tell the new player about the other players
                    register_observer(registrant)
                    players = players :+ registrant
                    //tell the new player about all the other players
                    send_observer_msg(_.player_joined(registrant.info))
                }
                log.info("Player joined: " + initial_player)
                if (players.size == max_players) {
                    //game_mutex.synchronize { () =>
                    val future = start_game
                    //}
                    return
                }
            } else {
                log.info("Player cannot join the game at this time")
            }
        }
    }

    /** This is called by the client to easily add bots */
    def add_bot(name: String, last_name: String = "") = {
        if (!is_game_in_progress) {
            var bot: Bot = null; //SinglePurchasePlayer.new(name, last_name, self, log)
            bot.delay = 2
            bot.pic = "http://jakrabbit.org/images/robot.jpg"
            register(bot)
        }
    }

    /** Register an IObserver to watch this game. */
    def register_observer(observer: GameObserver) = {
        //      observers := new SafeWrapper(observer, log)
        observers = observer :: observers
    }

    def check_for_stalemate(): Boolean = {
        log.debug("Checking for stalemate")
        trusted_players.foreach { player =>
            var valid_spots = 0
            var color = player.color
            valid_spots += Math.min(board.get_valid_settlement_spots(true, color).size, player.piecesLeft(classOf[Settlement]))
            if (valid_spots > 0)
                return false
            if (valid_spots == 0)
                valid_spots += Math.min(board.get_valid_city_spots(color).size, player.piecesLeft(classOf[City]))
            if (valid_spots > 0) return false
            if (valid_spots == 0)
                valid_spots += Math.min(board.get_valid_road_spots(color).size, player.piecesLeft(classOf[Road]))
            if (valid_spots > 0) return false
        }
        log.error("Game ended in a stalemate.")
        set_state(GameState.Stalemate)
        return true
    }

    def get_players: List[Player] = players.map { _.original_player }
    def get_player_infos: List[PlayerInfo] = players.map { _.original_player.info }

    def trusted_players = { players }

    /** Get the score of the given player */
    def get_score(player: Player) = {
        var p = get_player(player.color)
        var score = 0
        if (board.has_longest_road(p.color))
            score += 2
        var largest_army = who_has_largest_army
        if (largest_army != null && largest_army.color == p.color)
            score += 2
        board.all_nodes.foreach { n =>
            if (n.has_city && n.city.color == p.color) {
                score += n.city.points
            }
        }
        score + player.get_extra_victory_points
    }

    def get_scores = {
        get_players.map { p: Player => (p.info, get_score(p)) }.toMap
    }

    def count_resource_cards(playerInfo: PlayerInfo) = {
        var player = get_player(playerInfo.color)
        if (player == null)
            throw new IllegalArgumentException("Could not find player with color:" + playerInfo.color + " in " + players)
        var count = 0
        HexType.RESOURCE_TYPES.foreach { count += player.get_cards(_) }
        count
    }

    def count_all_resource_cards() = players.map { p => List(p.info, count_resource_cards(p.info)) }
    def count_dev_cards(playerInfo: PlayerInfo) = get_player(playerInfo.color).count_dev_cards

    /** Finds the player with the largest army, or nil if no one has it. */
    def who_has_largest_army(): PlayerInfo = {
        //The largest number of soldier cards
        val highest_count = trusted_players.map { count_soliders(_) }.max
        val who_has_the_most = players.filter { count_soliders(_) == highest_count }
        var result: PlayerInfo = null
        if (who_has_the_most.size == 1 && highest_count >= 3) {
            result = who_has_the_most.first.info
        }
        if (result != previous_player_with_largest_army) {
            send_observer_msg { _.player_has_largest_army(result) }
            previous_player_with_largest_army = result
        }
        return result
    }

    /** how many soldiers has this player played? */
    def count_soliders(player: TrustedPlayer): Int = {
        player.get_played_dev_cards.count { _.isInstanceOf[SoldierCard] }
    }

    /** Check to see if someone one.  If so, end the game */
    def check_for_winner(): Boolean = {
        //      game_mutex.synchronize{
        if (!is_game_done) {
            val winner = has_winner()
            if (winner != null) {
                val points = get_score(winner)
                log.info("Game finished. winner: " + winner + " points:" + points)
                send_observer_msg { _.game_end(winner.info, points) }
                if (current_turn != null)
                    current_turn.force_done
                set_state(GameState.Finished)
                true
            }
        }
        false
        //      }
    }

    /**
     * Does this game have a winner yet.
     * If so, return the player that one, nil otherwise.
     */
    def has_winner(): Player = {
        trusted_players.map{_.original_player}.find { get_score(_) >= max_points }.getOrElse(null)
    }

    /**
     * An iterator for all other players
     * [player] the player to exclude
     */
    def other_players(player: PlayerInfo): List[Player] = players.filter { _.color != player.color }

    /**
     * Send a chat message to all users
     * [player] is who wrote the message
     */
    def chat_msg(player: Player, msg: String) = {
        val real_player = get_player(player.color)
        players.foreach { _.chat_msg(real_player.info, msg) }
    }

    /** sends a message to all players from the admin */
    def admin_msg(msg: String) = players.foreach { _.chat_msg(null, msg) }

    /** Get information on who is winning, who has longest road and who has largest army */
    def get_player_stats(): Map[String, Any] = {
        val leading_score = players.map { get_score(_) }.max
        val leaders = players.filter { get_score(_) == leading_score }.map { _.info }
        val la = who_has_largest_army
        return Map("leaders" -> leaders,
            "largest_army" -> la,
            "longest_road" -> who_has_longest_road)
    }

    /** returns the player infos that have the longest road */
    def who_has_longest_road(): PlayerInfo = {
        var resultList = players.filter { p => board.has_longest_road(p.color) }.map { _.info }
        var result: PlayerInfo = null;
        if (resultList.size == 1 && resultList.first != previous_player_with_longest_road) {
            result = resultList.first
            send_observer_msg { _.player_has_longest_road(result) }
            previous_player_with_longest_road = result
        }
        return result
    }

    /**
     * Gets a piece from a player to place
     * raises an error if the player does not have the piece
     */
    def get_player_piece[T <: BoardPiece](player: Player, pieceType: Class[T]): T = {
        if (player.piecesLeft(pieceType) == 0) {
            throw new RuleException("Player: " + player.full_name + " has no " + pieceType + "s left")
        }
        player.piecesLeft += pieceType -> (player.piecesLeft(pieceType) - 1)
        var constructor = pieceType.getConstructor(classOf[String])
        return constructor.newInstance(player.color);
    }

    def offer_quote(quote: Quote, player_info: PlayerInfo) = {
        val real_player = get_player(player_info.color)
        //if (player_info.color != player_info.json_connection.player.color){
        //  throw new RuleException("Attempting to mis-represent yourself.")      
        //}       
        if (quote.bidder != player_info) { //player_info.json_connection.player.info()
            throw new IllegalStateException
        }
        current_turn.received_quote(quote)
        current_turn.player.offer_quote(quote)
    }

    /*
    * Gets a List of quotes from the bank and other users
    * Optionally takes a block that iterates through each quote as they come
    * [player] The player asking for quotes
    */
    def get_quotes(player: Player, wantList: List[Resource], giveList: List[Resource]) = {
        //var result = ThreadSafeList.new(get_quotes_from_bank(player, wantList, giveList))
        var result = get_quotes_from_bank(player, wantList, giveList)

        //Add user quotes
        //Create a new thread for each player
        other_players(player.info).iterate_threaded(true) { p: Player =>
            val userQuotes = p.get_user_quotes(p.info, wantList, giveList)
            if (userQuotes == null) {
                log.error("Player#get_user_quotes returned null. player:" + p)
            } else {
                userQuotes.foreach { quote: Quote =>
                    try {
                        if (quote.bidder != p.info) {
                            throw new RuleException("Player is offering a quote where the bidder is not himself. Player:" + p)
                        }
                        quote.validate(this)
                        result = quote :: result
                    } catch {
                        case e: Exception => log.error("User " + p + " offered an invalid Quote. " + e, e)
                    }
                }
            }
        }
        result
    }

    /** Returns a List of Quote objects from the bank for a specific player */
    def get_quotes_from_bank(player: Player, wantList: List[Resource], giveList: List[Resource]): List[Quote] = {
        //start with the bank's 4:1 offer
        var result: List[Quote] = Nil

        wantList.foreach { w: Resource =>
            giveList.foreach { g: Resource =>
                result ::= new Quote(null, g, 4, w, 1)
                board.get_ports(player.color).foreach { p: Port =>
                    if ((p.kind != null && p.kind == g) || p.kind == null) {
                        val quote = new Quote(null, g, p.rate, w, 1)
                        if (!result.contains(quote)) {
                            result = quote :: result
                        }
                    }
                }
            }
        }
        result = result.remove { q => player.get_cards(q.receiveType) < q.receiveNum }.removeDuplicates

        //Remove any redundant quotes
        //i.e. if result has a 2:1 quote, we don't need a 4:1 quote
        return result.remove { q => result.exists { _.isBetterQuote(q) } }
    }

    def give_turn_by_class(turn_class: Class[_ <: Turn], player: Player, synchronize: Boolean = true) = {
        val turn = turn_class.getConstructor(classOf[Admin], classOf[Player], classOf[Board], classOf[Logger]).newInstance(this, player, board, log)
        give_turn(turn, player, synchronize)
    }

    /**
     * A helper method to give a turn to a player.
     * This method returns when the turn is finished.  It shouldn't throw any errors.
     * Any errors that occur during the turn should be handled here.
     */
    def give_turn(turn: Turn, player: Player, synchronize: Boolean = true) = {
        if (!is_game_done) { //We need to check for the winner before we give the next player a turn
            //if (synchronize)
            //   turn_mutex.lock 
            log.debug("**Giving " + turn + " to " + player)
            current_turn_obj = turn
            try {
                //Give the player some time to make his/her move
                //timeout(turn_timeout) {
                var wrapper = new Callable[Any]() {
                    override def call(): Any = {
                        try {
                            //send_observer_msg { _.get_turn(player.info, turn.getClass) }
                            //Give the turn to the player
                            current_turn.state = TurnState.Active
                            log.debug("player is taking turn: " + player)
                            player.take_turn(current_turn, current_turn.is_setup)
                            if (!current_turn.isDone()) {
                                log.warn("Turn SHOULD BE DONE.  it's:" + current_turn.state + "    " + player + "   " + current_turn)
                                player match {
                                    case p: TrustedPlayer => log.warn("Player is trusted:" + p.original_player)
                                }
                            }
                            Util.while_with_timeout(turn_timeout_seconds * 1000) { () =>
                                !current_turn.state.is_terminal_state
                            }
                            log.debug("Turn finished with state:" + current_turn.state)
                        } catch {
                            case e =>
                                log.error("Error. CurrentTurnState=" + current_turn.state + " : " + e, e)
                                throw e
                        }
                    }

                    override def toString(): String = {
                        "Turn thread"
                    }
                }
                val future = SettlersExecutor.executor.submit(wrapper)
                all_futures = (future +: all_futures)
                future.get(turn_timeout_seconds, TimeUnit.SECONDS)
                if (current_turn.rule_error != null) {
                    throw current_turn.rule_error
                }
            } catch {
                case err: TimeoutException =>
                    times_skipped += player.color -> (times_skipped(player.color) + 1)
                    log.error("Player's Turn Timed-out. turn:" + turn + " Time skipped:" + times_skipped(player.color) + " player:" + player, err)
                    admin_msg(player.full_name() + " took too long.  A bot is filling in.")
                    if (times_skipped(player.color) == 3) {
                        kick_out(player, new IllegalStateException("Your turn was skipped too many times"))
                    } else {
                        // TOOD:Finish the bots Make a temporary bot to take over for a turn.
                        /*            	val tmpBot = SinglePurchasePlayer.copy(player, "Tempbot","The Robot", self)
            	val actingBot = new TrustedPlayer(this, new ActingBot(tmpBot, player), log, player.color, player.piecesLeft(City), player.piecesLeft(Settlement), player.piecesLeft(Road), player.cards, player.get_played_dev_cards)
            	current_turn.player = actingBot
            	actingBot.take_turn(current_turn, current_turn.is_setup)
            	tmpBot = nil
            	actingBot = nil
            	*/
                        kick_out(player, err) //for now
                    }
                case err: Exception =>
                    try {
                        log.error(err, err) //until i implement the kick out
                        kick_out(player, err)
                    } catch {
                        case e => log.error(e, e)
                    }
            }

            //Force the turn to be done
            current_turn.force_done
            log.debug("**give_turn() finished")
            //turn_mutex.unlock if synchronize
        }
    }

    /**
     * Starts the game thread with the registered players.
     * Note: This method returns immediately.
     */
    def start_game(): Future[Map[PlayerInfo, Int]] = {
        if (players.size <= 1) return null //you need 2 or more players to start a game
        if (is_game_in_progress || is_game_done) return null //if the game is already started or finished, this is a noop
        this.set_state(GameState.Running)
        val callable: Callable[Map[PlayerInfo, Int]] = new Callable[Map[PlayerInfo, Int]] {
            def call() = {
                try {
                    log.info("Starting Game")
                    //log.info("Using Board: //{board.name}")
                    log.info(" with " + players.size + " players")
                    log.info(" max score:" + max_points)
                    send_observer_msg { _.game_start }

                    var roundCount = 0
                    //setup
                    for (p <- players) {
                        give_turn_by_class(setup_turn_class, p)
                    }
                    for (p <- players.reverse) {
                        give_turn_by_class(setup_turn_class, p)
                    }
                    while (!is_game_done) {
                        roundCount += 1
                        for (p <- players) {
                            if (!check_for_winner) {
                                give_turn_by_class(normal_turn_class, p)
                            }
                        }
                        log.info("Round " + roundCount + " finished.  Highest Score:" + players.map { p => get_score(p) }.max)
                        if (roundCount > 2000) {
                            log.error("Too many rounds have occurred: " + roundCount + ". Effective Stalemate")
                            set_state(GameState.Stalemate)
                        }
                        if (!is_game_done)
                            check_for_stalemate
                    }
                    log.warn("game is done " + state)
                } catch {
                    case e: Exception => log.error("Game Ending Exception: " + e, e)
                }
                log.info("Game Thread done.")
                get_scores
            }
        }
        game_future = SettlersExecutor.executor.submit(callable)
        //gameThread.abort_on_exception = true
        return game_future
    }

    def shutdown() = {
        if (game_future != null)
            game_future.cancel(true);
        all_futures.foreach { f =>
            f.cancel(true)
        }
    }
}

/*    include UsesGameState
 
    //An error occured on this player's turn.  KICK 'EM OUT! and replace them with a bot.
    def kick_out(player, reason_or_error)={
      //player should always be a trusted player
      raise AdminError.new("Server error.  kick_out was not called with a trusted player object") unless player.is_a?(TrustedPlayer)
      if (reason_or_error.is_a?(Exception))
        log.error("REPLACING PLAYER WITH BOT: //{player}. //{reason_or_error}")
        log.error("//{reason_or_error}\n     //{reason_or_error.backtrace.join("\n     ")}")
      else
        log.error("REPLACING PLAYER WITH BOT: //{player}. //{reason_or_error}")
      }       
      kicked_out << player
      bot_player = SinglePurchasePlayer.copy(player, "Robo", "", self)
      trusted_bot = TrustedPlayer.new(self, bot_player, log, player.color, player.piecesLeft(City), player.piecesLeft(Settlement), player.piecesLeft(Road), player.cards, player.get_played_dev_cards) 
  
      replace_player(player, trusted_bot)
      begin
        timeout(2) do
          player.chat_msg(nil, "Error Occured: Now you've been kicked out")
        }       rescue => err 
        puts $!
      }     }   
    private


    def replace_player(old_player, new_player)={
      log.warn("Replacing player old://{old_player} new://{new_player}")
      log.debug("Current Turn Player: //{current_turn.player}.  Old Player: //{old_player}")
      //at this point, the new player should already have all of the old's cards
  
      other_players(old_player) do |p|
        p.player_replaced_by(old_player.info, new_player.info)
      }   
      players[players.index(old_player)] = new_player
      observers[observers.index(old_player)] = new_player if observers.include?(old_player)
      current_turn.rule_error = nil
      
      //if it was the old player's turn
      if (current_turn.player == old_player)
        current_turn.player = new_player
        log.warn("**Giving Turn to REPLACEMENT PLAYER: //{new_player}")
        give_turn(current_turn, new_player, false)
      }     }     
   


  //This type of error indicates that the admin is in a bad state and that something internally went wrong.
  //This error is fatal and should NEVER happen.
  class AdminError < Exception
    
  }
*/

/** Represents a single die */
abstract class Die {
    def roll: Int
}
object NormalDie extends Die {
    val r: Random = new Random()

    override def roll(): Int = {
        r.nextInt(5) + 1
    }
}

trait DiceHandler {
    def get_dice(): (Die, Die)
    def handle_roll_7(roll: (Int, Int))
    def dice_finished(roll: (Int, Int))
}

class StandardDiceHandler(val admin: Admin) extends DiceHandler {
    override def get_dice(): (Die, Die) = {
        return (NormalDie, NormalDie)
    }

    override def dice_finished(roll: (Int, Int)) = {

    }

    override def handle_roll_7(roll: (Int, Int)) = {
        //Each player must first get rid of half their cards if they more than 7
        admin.trusted_players.foreach { p =>
            try {
                if (p.count_resources > 7) {
                    val how_many_cards_to_lose = p.count_resources / 2
                    val chosen_cards = p.select_resource_cards(p.resource_cards, how_many_cards_to_lose, Admin.SELECT_CARDS_ROLLED_7)
                    if (chosen_cards == null)
                        throw new RuleException("select resource cards returned null")
                    if (chosen_cards.size != how_many_cards_to_lose)
                        throw new RuleException("You did not select the right number of cards. expected:"+how_many_cards_to_lose+" found:"+chosen_cards.size)
                    if (chosen_cards.exists { _ == null })
                        throw new RuleException("select resource cards contained null entries")
                    p.del_cards(chosen_cards, 4)
                }
            } catch {
                case re: RuleException =>
                    admin.log.error("REPLACING PLAYER WITH BOT: " + p, re)
                    admin.kick_out(p, re)
            }
        }

        //Then move the bandit
        admin.log.info("Rolled a 7, move the bandit.")
        val current_bandit_hex = admin.board.tiles.values.find { _.has_bandit }.getOrElse(null)
        val new_hex = admin.current_turn.player.move_bandit(current_bandit_hex)
        val local_hex = admin.board.getTile(new_hex.coords._1, new_hex.coords._2)
        admin.current_turn.move_bandit(local_hex)
    }

}
