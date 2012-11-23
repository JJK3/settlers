package core

import org.apache.log4j._
import UtilList._

class TurnState(val desc: String, val is_terminal_state: Boolean, extra_data: Any = null) {
    override def toString() = {
        var extra = ""
        if (extra_data != null)
            extra = "extra=\"" + extra_data + "\""
        "<" + this.getClass().getName() + " desc=\"" + desc + "\" " + extra + "/>"
    }
}

object TurnState {
    val Created = new TurnState("New Turn", false)
    val Active = new TurnState("The turn was given to the user, and is considered active", false)
    val RolledDice = new TurnState("The dice were rolled", false)
    val Done = new TurnState("Turn is done", true)
    val DoneWithError = new TurnState("Turn ended with an error", true)
    val KnownStates: List[TurnState] = List(Created, Active, RolledDice, Done, DoneWithError)
}

trait TurnStateListener {
    def state_changed(new_state: TurnState);
}

/** A module that can have a turn state */
trait TurnStatable {
    var state: TurnState = TurnState.Created
    var state_listeners = List[TurnStateListener]()

    def isDone() = state.is_terminal_state

    def has_rolled(): Boolean = {
        // "CURRENT STATE: #{self.state} : #{TurnState::ROLLED_DICE} : #{self.state == TurnState::ROLLED_DICE}"
        state == TurnState.RolledDice
    }

    def set_state(s: TurnState) = {
        state = s
        state_listeners.foreach { _.state_changed(s) }
    }

    def register_listener(listener: TurnStateListener) = {
        state_listeners = state_listeners :+ listener
    }
}

object Turn {
    val DELETE_REASON_SETTLEMENT = 1
    val DELETE_REASON_CITY = 2
    val DELETE_REASON_ROAD = 3
    val DELETE_REASON_ROLLED_7 = 4
    val DELETE_REASON_STOLE = 5
    val DELETE_REASON_TRADE = 6
    val DELETE_REASON_OTHER = 7
}

class Turn(val admin: Admin, var player: Player, val board: Board, val log: Logger) extends TurnStatable {
    val road_constraint = true
    var all_quotes: List[Quote] = Nil
    val is_setup = false
    var rule_error: Exception = null

    def roll_dice: (Int, Int) = {
        assert_not_done
        var result = admin.roll_dice
        set_state(TurnState.RolledDice)
        result
    }

    /** The list of action cards currently in play. i.e. SoldierCards etc. */
    def active_cards = player.get_played_dev_cards.remove { _.is_done }

    def buy_development_card: DevelopmentCard = {
        check_state("Development card bought")
        pay_for(new DevelopmentCard())
        val card = admin.board.card_pile.get_card
        player.add_cards(List(card))
        return card
    }

    def set_player(p: TrustedPlayer) = this.player = p

    /** The user has broken a rule.  They going to be kicked out */
    def break_rule(msg: String) = {
        val error = new RuleException(msg)
        this.rule_error = error
        //log.error(error, error)  Don't log, we throw the error
        set_state(new TurnState("Turn ended with an error", true, msg))
        throw error
    }

    /**
     *  Gets a list of quotes from the bank and other users
     * Optionally takes a block that iterates through each quote as they come
     * (List(CardType), List(CardType)) -> List(Quote)
     * This is from the current player's point of view.  He wants the want list and will give the giveList
     */
    def get_quotes(wantList: List[Resource], giveList: List[Resource]): List[Quote] = {
        validate_quote_lists(wantList, giveList)
        check_state("get_quotes")
        val quotes = admin.get_quotes(player, wantList, giveList)
        all_quotes = (all_quotes ++ quotes).removeDuplicates
        return quotes
    }

    /**
     * returns a list of Quote objects from the bank
     * (CardType, CardType) -> List(Quote)
     */
    def get_quotes_from_bank(wantList: List[Resource], giveList: List[Resource]): List[Quote] = {
        validate_quote_lists(wantList, giveList)
        check_state("get_quotes_from_bank")
        val quotes = admin.get_quotes_from_bank(player, wantList, giveList)
        all_quotes = (all_quotes ++ quotes).removeDuplicates
        return quotes
    }

    /**
     * Move the bandit to a new tile.
     * This is called by the admin and the soldier card
     */
    def move_bandit(new_tile: Hex) = {
        if (new_tile == null) throw new IllegalArgumentException("new_tile cannot be null")
        //TODO: implement rule checking here so people can't move the 
        //bandit whenever they want.
        board.move_bandit(new_tile)

        admin.observers.foreach {
            _.player_moved_bandit(player.info, new_tile)
        }

        //Take a card from a player
        //the colors of the cities touching the new tile
        val touching_colors = new_tile.nodes_with_cities().map { _.city.color }.distinct
        val touching_players = touching_colors.map { get_player(_).info }.filterNot(_ == player.info).toList

        var player_to_take_from: PlayerInfo = null
        if (!touching_players.isEmpty) {
            if (touching_players.size == 1) {
                player_to_take_from = touching_players.first
            } else {
                player_to_take_from = player.select_player(touching_players, 1)
                if (player_to_take_from == null)
                    break_rule("You must select a player")
            }

            take_random_card(player_to_take_from)
            admin.observers.foreach { _.player_stole_card(player.info, player_to_take_from, 1) }
        }
    }

    def received_quote(quote: Quote): Unit = all_quotes ::= quote

    def place_road(x: Int, y: Int, edgeNum: Int) = {
        log.debug(player + " is trying to buy a road")
        if (admin.is_game_done()) break_rule("Game is Over")
        check_state("Road placed")
        val tile = board.getTile(x, y)
        if (tile == null || edgeNum < 0 || edgeNum > 5) {
            break_rule("Invalid edge: " + x + " " + y + " " + edgeNum)
        }
        val edge = tile.edges(edgeNum)
        if (!board.get_valid_road_spots(player.color).contains(edge)) {
            break_rule("Invalid Road Placement " + x + " " + y + " " + edgeNum)
        }

        //if a player uses a roadBuilding card, then his purchasedRoads > 0
        //they shouldn't pay for the road in this case.
        var should_pay = true
        if (player.free_roads > 0) {
            should_pay = false
            player.remove_free_roads(1)
        }
        val road: Road = purchase(classOf[Road], should_pay)
        board.place_road(road, x, y, edgeNum)
        admin.observers.foreach { _.placed_road(player.info, x, y, edgeNum) }
        admin.check_for_winner
    }

    /** A helper method to get a player based on a color */
    def get_player(color: String) = admin.get_player(color)

    def can_afford(pieces: List[Purchaseable]) = get_player(player.color).can_afford(pieces)

    def place_settlement(x: Int, y: Int, nodeNum: Int): Node = {
        log.debug(player + " is trying to buy a settlement")
        if (admin.is_game_done) break_rule("Game is Over")
        check_state("Settlement placed")
        val node = validate_node(x, y, nodeNum)
        if (node.has_city) break_rule("Cannot place a settlement on a "+node.city)
        val sett = purchase(classOf[Settlement])
        val spots = board.get_valid_settlement_spots(road_constraint, player.color)
        if (!board.get_valid_settlement_spots(road_constraint, player.color).contains(node)) {
            break_rule("Invalid Settlement Placement " + x + " " + y + " " + nodeNum)
        }
        board.place_city(sett, x, y, nodeNum)
        log.info("Settlement Placed by " + player + " on " + x + "," + y + "," + nodeNum)
        admin.observers.foreach { _.placed_settlement(player.info, x, y, nodeNum) }
        admin.check_for_winner
        node
    }

    def get_valid_settlement_spots = board.get_valid_settlement_spots(road_constraint, player.color)

    def get_valid_road_spots = board.get_valid_road_spots(player.color)

    def place_city(x: Int, y: Int, nodeNum: Int): Node = {
        log.debug(player + " is trying to buy a city")
        if (admin.is_game_done) {
            break_rule("Game is Over")
        }
        check_state("City placed")
        val node = validate_node(x, y, nodeNum)

        if (node.has_city) {
            if (node.city.color == player.color) {
                if (!node.city.isInstanceOf[Settlement]) break_rule("A city must be placed on top of a Settlement, not a #{node.city}")
                val city = purchase(classOf[City])
                player.addPiecesLeft(classOf[Settlement], 1) //Put the settlement back in the 'bag'

                board.place_city(city, x, y, nodeNum)
                log.info("City Placed by " + player + " on " + x + "," + y + "," + nodeNum)
                admin.observers.foreach { _.placed_city(player.info, x, y, nodeNum) }
                admin.check_for_winner
            } else {
                break_rule("Invalid City Placement.  Settlement has wrong color at " + x + " " + y + " " + nodeNum + ". " + player + " expected:" + player.color + " was:" + node.city.color)
            }
        } else {
            break_rule("Invalid City Placement.  There is no settlement at " + x + " " + y + " " + nodeNum)
        }
        node
    }

    /** validate that the given node DOES exist */
    private def validate_node(x: Int, y: Int, nodeNum: Int): Node = {
        val tile = board.getTile(x, y)
        if (tile != null && nodeNum >= 0 && nodeNum <= 5) {
            return tile.nodes(nodeNum)
        }
        break_rule("Invalid node: " + x + " " + y + " " + nodeNum)
    }

    def assert_not_done = {
        assert_rules(Map(isDone -> ("Turn is already done: " + this.state)))
    }

    def check_state(s: String) = {
        assert_rules(Map(
            !has_rolled -> (s + " before dice were rolled.  Current state: " + this.state),
            (active_cards.size > 0) -> "All card actions must be finished."))
        assert_not_done
    }

    /**
     * Make this player pay for and account for 1 less piece.
     * This method will raise an exception if they can't actually buy the piece
     * [pieceKlass] the Class object of the piece
     * [should_pay] should the player pay for the given piece?
     *              This is safe because this method is private
     */
    def purchase[T <: BoardPiece](pieceKlass: Class[T], should_pay: Boolean = true): T = {
        //Check that the player has any pieces left
        if (player.piecesLeft(pieceKlass) == 0) {
            player match{
                case p:TrustedPlayer =>
                	log.info("original player thinks it has "+p.original_player.piecesLeft(pieceKlass) +" " +pieceKlass)
            }

            break_rule("player: " + player.full_name() + " has no " + pieceKlass + "s left")
        }
        player.addPiecesLeft(pieceKlass, -1)
        val piece = pieceKlass.getConstructor(classOf[String]).newInstance(player.color)

        //Now, try to pay for the piece
        try {
            if (should_pay) pay_for(piece)
        } catch {
            case e: RuleException =>
                player.addPiecesLeft(pieceKlass, 1) // Put the piece back
        }
        piece
    }

    /**
     * Makes a player pay for a piece
     * Throws an exception if the player doesn't have enough cards,
     * but doesn't mutate the player if an exception is thrown.
     */
    def pay_for(piece: Purchaseable): Unit = {
        if (piece == null) throw new IllegalArgumentException("piece cannot be null")

        val reason = if (piece.isInstanceOf[Settlement]) {
            Turn.DELETE_REASON_SETTLEMENT
        } else if (piece.isInstanceOf[City]) {
            Turn.DELETE_REASON_CITY
        } else if (piece.isInstanceOf[Road]) {
            Turn.DELETE_REASON_ROAD
        } else {
            Turn.DELETE_REASON_OTHER
        }
        player.del_cards(admin.get_price(piece), reason)
    }

    def done = {
        if (!admin.is_game_done()) { //If the game is already done, we don't care
            assert_rules(Map(
                (!has_rolled || state.is_terminal_state) -> ("Turn ended before dice were rolled.  Current state: " + state),
                (active_cards.size > 0) -> "All card actions must be finished.",
                (player.purchased_pieces != 0) -> "You cannot end a turn while there are purchased pieces to place",
                (active_cards.exists { _.single_turn_card }) ->
                    ("There are still active cards: " + active_cards)))
        }
        force_done
        log.debug("Turn done")
    }

    def force_done = {
        //done_stacktrace = caller
        set_state(TurnState.Done)
        //    @admin_thread.run
    }

    def validate_quote_lists(wantList: List[Resource], giveList: List[Resource]) = {
        if (wantList == null) throw new IllegalArgumentException("wantList cannot be null")
        if (giveList == null) throw new IllegalArgumentException("giveList cannot be null")

        // Make sure that the player has enough cards to make the offer
        for (val giveType <- giveList.removeDuplicates) {
            if (player.get_cards(giveType) == 0) {
                break_rule("You can't offer cards that you don't have: Offering " + giveType + " but has " + player.get_cards)
            }
        }
    }

    /**
     * Take a random card from another player and add it to your own cards
     * If player has no cards, do nothing
     */
    def take_random_card(victim: PlayerInfo): Unit = {
        if (victim == null)
            throw new IllegalArgumentException("player cannot be null")
        val real_victim = get_player(victim.color)
        val available_resources = real_victim.resource_cards
        if (available_resources.isEmpty) {
            log.debug("Could not take a random card from " + victim)
            return
        }
        val res = available_resources.pick_random
        real_victim.del_cards(List(res), Turn.DELETE_REASON_OTHER)
        player.add_cards(List(res))
    }

    def assert_rules(m: Map[Boolean, String]) = {
        m.foreach { entry =>
            if (entry._1) {
                break_rule(entry._2)
            }
        }
    }

    def play_development_card(card: DevelopmentCard) = {
        assert_rules(Map(
            admin.is_game_done -> "Game is Over",
            (player.get_cards(card) == 0) -> ("Player does not own the card being played. cards:" + player.get_cards),
            isDone() -> "Turn is done",
            (!card.isInstanceOf[SoldierCard] && !this.has_rolled) ->
                (card.getClass() + " played before dice were rolled. Current State:" + state)))
        card.use(this)
        player.del_cards(List(card), Turn.DELETE_REASON_OTHER)
        player.played_dev_card(card)
        admin.check_for_winner
    }

    /**
     * trade cards
     * Quote -> void
     */
    def accept_quote(quote: Quote) = {
        check_state("accept quote")
        if (!all_quotes.contains(quote)) {
            break_rule("Attempting to accept a quote that hasn't been received:" + quote + " Other quotes: " + all_quotes)
        }

        quote.validate(admin)

        // Check to make sure that everybody has enough cards
        if (player.get_cards(quote.receiveType) < quote.receiveNum) {
            break_rule("You don't have enough cards for this quote: " + quote)
        }
        var bidder_player: TrustedPlayer = null
        if (quote.bidder != null) {
            bidder_player = get_player(quote.bidder.color)
            if (bidder_player.get_cards(quote.giveType) < quote.giveNum) {
                break_rule("Bidder " + bidder_player + " doesn't have enough cards for this quote: " + quote)
            }
        }

        //Make the actual trade
        val bidder_name = if (bidder_player != null) { bidder_player } else { "The Bank" }
        log.debug(player + " is accepting a trade from " + bidder_name + " " + quote.giveNum + " " + quote.giveType + " for " + quote.receiveNum + " " + quote.receiveType)
        player.add_cards(List.tabulate(quote.giveNum)(i => quote.giveType))
        player.del_cards(List.tabulate(quote.receiveNum)(i => quote.receiveType), Turn.DELETE_REASON_TRADE)
        if (bidder_player != null) {
            bidder_player.add_cards(List.tabulate(quote.receiveNum)(i => quote.receiveType))
            bidder_player.del_cards(List.tabulate(quote.giveNum)(i => quote.giveType), Turn.DELETE_REASON_TRADE)
        }
    }

}

class SetupTurn(admin: Admin, player: Player, board: Board, log: Logger) extends Turn(admin, player, board, log) {

    var placed_road: (Int, Int, Int) = null
    var placed_settlement: (Int, Int, Int) = null
    override val road_constraint = false
    override val is_setup = true

    override def place_road(x: Int, y: Int, edgeNum: Int) = {
        val e = board.getTile(x, y).edges(edgeNum)
        val validSpots = get_valid_road_spots

        assert_rules(Map(
            (placed_road != null) -> "Too many roads placed in setup",
            (placed_settlement == null) -> "Must place settlement before road",
            !validSpots.contains(e) -> "Road must touch the settlement just placed"))

        this.placed_road = (x, y, edgeNum)
        super.place_road(x, y, edgeNum)
    }

    override def get_valid_road_spots: List[Edge] = {
        var node: Node = null
        if (placed_settlement != null) {
            var s = placed_settlement
            node = board.getTile(s._1, s._2).nodes(s._3)
        }
        return board.get_valid_road_spots(player.color, node)
    }

    override def place_settlement(x: Int, y: Int, nodeNum: Int): Node = {
        if (placed_settlement != null)
            break_rule("Too many settlements placed in setup")
        placed_settlement = (x, y, nodeNum)
        val node: Node = super.place_settlement(x, y, nodeNum)

        val settlement_count = board.all_nodes.count(n => n.has_city && n.city.color == player.color)
        if (settlement_count == 2) {
            // A Player gets cards for the 2nd settlement he places
            val touching_hexes = node.hexes.filterNot { _.card_type == DesertType }
            val resources = touching_hexes.map { _.get_card }
            if (!resources.isEmpty)
                player.add_cards(resources)
        } else if (settlement_count != 1) {
            break_rule("Bad Game state.  Wrong # of settlements placed: " + settlement_count)
        }
        node
    }

    override def done = {
        assert_rules(Map(
            (placed_settlement == null) -> "You cannot end a setup turn without placing a settlement.",
            (placed_road == null) -> "You cannot end a setup turn without placing a road."))
        force_done
        log.debug("Turn done")
    }

    override def roll_dice() = { break_rule("Cannot roll dice during setup") }
    override def place_city(x: Int, y: Int, nodeNum: Int): Node = { break_rule("Cannot place city during setup") }
    override def buy_development_card() = { break_rule("Cannot buy development card during setup") }
    override def check_state(s: String) = {}
    override def pay_for(piece: Purchaseable): Unit = {}
}

/**
 * Represents a quote for trading cards
 * This is basically the Bidder saying "I'll give you giveType for receiveType"
 * The Trader then accepts the quote afterwards
 */
class Quote(
    val bidder: PlayerInfo,
    val receiveType: Resource,
    val receiveNum: Int,
    val giveType: Resource,
    val giveNum: Int) {

    //if (bidder == null) throw new IllegalArgumentException("Bidder is null.")
    if (receiveType == null) throw new IllegalArgumentException("ReceiveType is null.")
    if (giveType == null) throw new IllegalArgumentException("GiveType is null.")
    if (receiveNum <= 0) throw new IllegalArgumentException("ReceiveNum is not >= 0. Actual:" + receiveNum)
    if (giveNum <= 0) throw new IllegalArgumentException("GiveNum is not >= 0. Actual:" + giveNum);

    override def toString() = {
        "[Quote " + receiveNum + " " + receiveType + " for " + giveNum + " " + giveType + " from " + bidder + "]"
    }

    override def equals(that: Any): Boolean = {
        that match {
            case q2: Quote =>
                ((q2.bidder == null && bidder == null) ||
                    (q2.bidder != null && bidder != null && q2.bidder.color == bidder.color)) &&
                    q2.receiveType.toString == receiveType.toString &&
                    q2.receiveNum == receiveNum &&
                    q2.giveType.toString() == giveType.toString() &&
                    q2.giveNum == giveNum
            case _ => false
        }
    }

    def validate(admin: Admin): Unit = {
        if (bidder == null) return //The bank always has enough cards
        val player = admin.get_player(bidder.color)
        if (player.get_cards(giveType) < giveNum) {
            throw new IllegalStateException("Bidder " + bidder + " does not have enough resources for this quote:" + this + "  Bidder cards:" + player.get_cards);
        }
    }

    /** Is another quote a better deal? (And also the same resources) */
    def isBetterQuote(other: Quote): Boolean = {
        other.receiveNum < this.receiveNum &&
            other.giveType == this.giveType &&
            other.receiveType == this.receiveType
    }
}

/*

  class WakeThreadListener
    def initialize(thread_to_wake)
      @thread_to_wake = thread_to_wake
   }
    
    def state_changed(new_state)
      @thread_to_wake.run if @thread_to_wake.alive? and new_state.is_terminal_state
   }
 }
  
  
  # The Main turn class.  This represents a normal turn.
  class Turn 
    include TurnStatable    
    attr_reader :admin, :is_setup, :player
    attr_accessor :rule_error

    
    def initialize(admin, player, board, log, admin_thread = Thread.current)
      @admin = admin
      @player = player
      @board = board
      @roadConstraint = true
      @allQuotes=[] #all the quotes received in this turn
      @is_setup = false
  
      @log = log
      @rule_error = nil
      @admin_thread = admin_thread
      init_turnstatable
   }
  
    private
    
*/
