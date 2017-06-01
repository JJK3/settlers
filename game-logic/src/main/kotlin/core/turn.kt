package core

import org.apache.log4j.Logger

enum class TurnState(val desc: String, val is_terminal_state: Boolean) {

    Created("New Turn", false),
    Active("The turn was given to the user, and is considered active", false),
    RolledDice("The dice were rolled", false),
    Done("Turn is done", true),
    DoneWithError("Turn ended with an error", true);

    override fun toString(): String {
        return "$name(desc='$desc', is_terminal_state=$is_terminal_state)"
    }
}

interface TurnStateListener {
    fun state_changed(_state: TurnState)
}

/** A module that can have a turn state */
open class HasTurnState {
    var state: TurnState = TurnState.Created
    var state_listeners = emptyList<TurnStateListener>()
    fun isDone() = state.is_terminal_state
    fun has_rolled(): Boolean {
        // "CURRENT STATE: #{self.state} : #{TurnState::ROLLED_DICE} : #{self.state == TurnState::ROLLED_DICE}"
        return state == TurnState.RolledDice
    }

    fun set_state(s: TurnState) {
        state = s
        state_listeners.forEach { it.state_changed(s) }
    }

    fun register_listener(listener: TurnStateListener) {
        state_listeners += listener
    }
}

open class Turn(val admin: Admin, var player: Player, val board: Board) : HasTurnState() {
    companion object {
        val DELETE_REASON_SETTLEMENT = 1
        val DELETE_REASON_CITY = 2
        val DELETE_REASON_ROAD = 3
        val DELETE_REASON_ROLLED_7 = 4
        val DELETE_REASON_STOLE = 5
        val DELETE_REASON_TRADE = 6
        val DELETE_REASON_OTHER = 7
    }

    var log = Logger.getLogger(javaClass)
    open val road_constraint = true
    var all_quotes = emptyList<Quote>()
    open val is_setup = false
    var rule_error: Exception? = null
    open fun roll_dice(): Pair<Int, Int> {
        assert_not_done()
        var result = admin.roll_dice()
        set_state(TurnState.RolledDice)
        return result
    }

    /** The list of action cards currently in play. i.e. SoldierCards etc. */
    fun active_cards() = player.get_played_dev_cards().filter { !it.is_done }

    open fun buy_development_card(): DevelopmentCard {
        check_state("Development card bought")
        val card = admin.board.card_pile.get_card
        pay_for(card)
        player.add_cards(listOf(card))
        return card
    }

    fun set_player(p: TrustedPlayer) {
        this.player = p
    }

    /** The user has broken a rule.  They going to be kicked out */
    fun break_rule(msg: String) {
        val error = RuleException(msg)
        this.rule_error = error
        set_state(TurnState.DoneWithError)
        throw error
    }

    /**
     *  Gets a list of quotes from the bank and other users
     * Optionally takes a block that iterates through each quote as they come
     * (List(CardType), List(CardType)) -> List(Quote)
     * This is from the current player's point of view.  He wants the want list and will give the giveList
     */
    fun get_quotes(wantList: List<Resource>, giveList: List<Resource>): List<Quote> {
        validate_quote_lists(wantList, giveList)
        check_state("get_quotes")
        val quotes = admin.get_quotes(player, wantList, giveList)
        all_quotes = (all_quotes + quotes).distinct()
        return quotes
    }

    /**
     * returns a list of Quote objects from the bank
     * (CardType, CardType) -> List(Quote)
     */
    fun get_quotes_from_bank(wantList: List<Resource>, giveList: List<Resource>): List<Quote> {
        validate_quote_lists(wantList, giveList)
        check_state("get_quotes_from_bank")
        val quotes = admin.get_quotes_from_bank(player, wantList, giveList)
        all_quotes = (all_quotes + quotes).distinct()
        return quotes
    }

    /**
     * Move the bandit to a  tile.
     * This is called by the admin and the soldier card
     */
    fun move_bandit(_tile: Hex) {
        //TODO: implement rule checking here so people can't move the
        //bandit whenever they want.
        board.move_bandit(_tile)

        admin.observers.forEach {
            it.player_moved_bandit(player.info(), _tile)
        }

        //Take a card from a player
        //the colors of the cities touching the  tile
        val touching_colors = _tile.nodes_with_cities().map { it.city!!.color }.distinct()
        val touching_players = touching_colors.map { get_player(it).info() }.filterNot { it == player.info() }.toList()

        var player_to_take_from: PlayerInfo? = null
        if (!touching_players.isEmpty()) {
            if (touching_players.size == 1) {
                player_to_take_from = touching_players.first()
            } else {
                player_to_take_from = player.select_player(touching_players, 1)
                if (player_to_take_from == null)
                    break_rule("You must select a player")
            }

            take_random_card(player_to_take_from)
            admin.observers.forEach { it.player_stole_card(player.info(), player_to_take_from!!, 1) }
        }
    }

    fun received_quote(quote: Quote): Unit {
        all_quotes += quote
    }

    open fun place_road(x: Int, y: Int, edgeNum: Int) {
        log.debug("$player is trying to buy a road")
        if (admin.is_game_done()) break_rule("Game is Over")
        check_state("Road placed")
        val edge = board.getEdge(x, y, edgeNum)
        if (edge == null) {
            break_rule("Invalid edge: " + x + " " + y + " " + edgeNum)
        }
        if (!board.get_valid_road_spots(player.color).contains(edge)) {
            break_rule("Invalid Road Placement " + x + " " + y + " " + edgeNum)
        }

        //if a player uses a roadBuilding card, then his purchasedRoads > 0
        //they shouldn't pay for the road in this case.
        var should_pay = true
        if (player.free_roads() > 0) {
            should_pay = false
            player.remove_free_roads(1)
        }
        val road = Road(player.color)
        purchase(road, should_pay)
        board.place_road(road, x, y, edgeNum)
        admin.observers.forEach { it.placed_road(player.info(), x, y, edgeNum) }
        admin.check_for_winner()
    }

    /** A helper method to get a player based on a color */
    fun get_player(color: String) = admin.get_player(color)

    fun can_afford(pieces: List<Purchaseable>) = get_player(player.color).can_afford(pieces)
    open fun place_settlement(x: Int, y: Int, nodeNum: Int): Node {
        log.debug("$player is trying to buy a settlement")
        if (admin.is_game_done()) break_rule("Game is Over")
        check_state("Settlement placed")
        val node = validate_node(x, y, nodeNum)
        if (node.has_city()) break_rule("Cannot place a settlement on a " + node.city)
        val sett = Settlement(player.color)
        purchase(sett)
        val spots = board.get_valid_settlement_spots(road_constraint, player.color)
        if (!board.get_valid_settlement_spots(road_constraint, player.color).contains(node)) {
            break_rule("Invalid Settlement Placement " + x + " " + y + " " + nodeNum)
        }
        board.place_city(sett, x, y, nodeNum)
        log.info("Settlement Placed by " + player + " on " + x + "," + y + "," + nodeNum)
        admin.observers.forEach { it.placed_settlement(player.info(), x, y, nodeNum) }
        admin.check_for_winner()
        return node
    }

    fun get_valid_settlement_spots() = board.get_valid_settlement_spots(road_constraint, player.color)
    open fun get_valid_road_spots() = board.get_valid_road_spots(player.color)
    open fun place_city(x: Int, y: Int, nodeNum: Int): Node {
        log.debug("$player is trying to buy a city")
        if (admin.is_game_done()) {
            break_rule("Game is Over")
        }
        check_state("City placed")
        val node = validate_node(x, y, nodeNum)

        if (node.has_city()) {
            if (node.city?.color == player.color) {
                if (node.city !is Settlement) {
                    break_rule("A city must be placed on top of a Settlement, not a ${node.city}")
                }
                val city = City(player.color)
                purchase(city)
                player.addPiecesLeft(Settlement::class.java, 1) //Put the settlement back in the 'bag'
                board.place_city(city, x, y, nodeNum)
                log.info("City Placed by $player on $x,$y,$nodeNum")
                admin.observers.forEach { it.placed_city(player.info(), x, y, nodeNum) }
                admin.check_for_winner()
            } else {
                break_rule("Invalid City Placement.  Settlement has wrong color at $x $y $nodeNum. " +
                        "$player expected:${player.color} was:${node.city?.color}")
            }
        } else {
            break_rule("Invalid City Placement.  There is no settlement at $x $y $nodeNum")
        }
        return node
    }

    /** validate that the given node DOES exist */
    private fun validate_node(x: Int, y: Int, nodeNum: Int): Node {
        val node = board.getNode(x, y, nodeNum)
        if (node != null) {
            return node
        }
        throw RuleException("Invalid node: $x $y $nodeNum")
    }

    fun assert_not_done() = assert_rule(isDone(), "Turn is already done: ${state}")
    open fun check_state(s: String) {
        assert_rule(!has_rolled(), "$s before dice were rolled.  Current state: ${state}")
        assert_rule(active_cards().isNotEmpty(), "All card actions must be finished.")
        assert_not_done()
    }

    /**
     * Make this player pay for and account for 1 less piece.
     * This method will raise an exception if they can't actually buy the piece
     * [pieceKlass] the Class object of the piece
     * [should_pay] should the player pay for the given piece?
     *              This is safe because this method is private
     */
    fun purchase(piece: BoardPiece, should_pay: Boolean = true) {
        //Check that the player has any pieces left
        if (player.get_pieces_left(piece::class.java) == 0) {
            break_rule("player: ${player.full_name()} has no ${piece}s left")
        }
        player.removePiece(piece)

        //Now, try to pay for the piece
        if (should_pay) {
            pay_for(piece)
        }
    }

    /**
     * Makes a player pay for a piece
     * Throws an exception if the player doesn't have enough cards,
     * but doesn't mutate the player if an exception is thrown.
     */
    open fun pay_for(piece: Purchaseable): Unit {
        val reason = when (piece) {
            is Settlement ->
                Turn.DELETE_REASON_SETTLEMENT
            is City ->
                Turn.DELETE_REASON_CITY
            is Road ->
                Turn.DELETE_REASON_ROAD
            else ->
                Turn.DELETE_REASON_OTHER
        }
        player.del_cards(admin.get_price(piece).map { ResourceCard(it) }, reason)
    }

    open fun done() {
        if (!admin.is_game_done()) { //If the game is already done, we don't care
            assert_rule(!has_rolled() || state.is_terminal_state,
                    ("Turn ended before dice were rolled.  Current state: " + state))
            assert_rule(active_cards().isNotEmpty(), "All card actions must be finished.")
            assert_rule(player.purchased_pieces != 0, "You cannot end a turn while there are purchased pieces to place")
            assert_rule(active_cards().any { it.single_turn_card }, "There are still active cards: " + active_cards())
        }
        force_done()
        log.debug("Turn done")
    }

    fun force_done() {
        //done_stacktrace = caller
        set_state(TurnState.Done)
        //    @admin_thread.run
    }

    fun validate_quote_lists(wantList: List<Resource>, giveList: List<Resource>) {
        // Make sure that the player has enough cards to make the offer
        for (giveType in giveList.distinct()) {
            if (player.countResources(giveType) == 0) {
                break_rule("You can't offer cards that you don't have: " +
                        "Offering $giveType but has ${player.get_cards()}")
            }
        }
    }

    /**
     * Take a random card from another player and add it to your own cards
     * If player has no cards, do nothing
     */
    fun take_random_card(victim: PlayerInfo): Unit {
        val real_victim = get_player(victim.color)
        val available_resources = real_victim.resource_cards()
        if (available_resources.isEmpty()) {
            log.debug("Could not take a random card from " + victim)
            return
        }
        val res = available_resources.pick_random()
        real_victim.del_cards(listOf(res), Turn.DELETE_REASON_OTHER)
        player.add_cards(listOf(res))
    }

    fun assert_rule(condition: Boolean, errorMsg: String) {
        if (condition) {
            break_rule(errorMsg)
        }
    }

    fun play_development_card(card: DevelopmentCard) {
        assert_rule(admin.is_game_done(), "Game is Over")
        assert_rule(!player.get_cards().contains(card),
                "Player does not own the card being played. cards:" + player.get_cards())
        assert_rule(isDone(), "Turn is done")
        assert_rule(!(card is SoldierCard) && !this.has_rolled(),
                "$card played before dice were rolled. Current State: $state")
        card.use(this)
        player.del_cards(listOf(card), Turn.DELETE_REASON_OTHER)
        player.played_dev_card(card)
        admin.check_for_winner()
    }

    /**
     * trade cards
     * Quote -> void
     */
    fun accept_quote(quote: Quote) {
        check_state("accept quote")
        if (!all_quotes.contains(quote)) {
            break_rule("Attempting to accept a quote that hasn't been received:$quote Other quotes: $all_quotes")
        }

        quote.validate(admin)

        // Check to make sure that everybody has enough cards
        if (player.countResources(quote.receiveType) < quote.receiveNum) {
            break_rule("You don't have enough cards for this quote: " + quote)
        }
        var bidder_player: TrustedPlayer? = null
        if (quote.bidder != null) {
            bidder_player = get_player(quote.bidder.color)
            if (bidder_player.countResources(quote.giveType) < quote.giveNum) {
                break_rule("Bidder $bidder_player doesn't have enough cards for this quote: $quote")
            }
        }

        //Make the actual trade
        val bidder_name = if (bidder_player != null) {
            bidder_player
        } else {
            "The Bank"
        }
        log.debug("$player is accepting a trade from $bidder_name ${quote.giveNum} ${quote.giveType}" +
                " for ${quote.receiveNum} ${quote.receiveType}")



        player.add_cards((0..quote.giveNum).map { ResourceCard(quote.giveType) })
        player.del_cards((0..quote.receiveNum).map { ResourceCard(quote.receiveType) }, Turn.DELETE_REASON_TRADE)
        if (bidder_player != null) {
            bidder_player.add_cards((0..quote.receiveNum).map { ResourceCard(quote.receiveType) })
            bidder_player.del_cards((0..quote.giveNum).map { ResourceCard(quote.giveType) }, Turn.DELETE_REASON_TRADE)
        }
    }

}

class SetupTurn(admin: Admin, player: Player, board: Board) : Turn(admin, player, board) {

    var placed_road: Edge? = null
    var placed_settlement: Node? = null
    override val road_constraint = false
    override val is_setup = true
    override fun place_road(x: Int, y: Int, edgeNum: Int) {
        val e = board.getEdge(x, y, edgeNum)
        val validSpots = get_valid_road_spots()

        assert_rule(placed_road != null, "Too many roads placed in setup")
        assert_rule(placed_settlement == null, "Must place settlement before road")
        assert_rule(!validSpots.contains(e), "Road must touch the settlement just placed")

        this.placed_road = e
        super.place_road(x, y, edgeNum)
    }

    override fun get_valid_road_spots(): List<Edge> = board.get_valid_road_spots(player.color, placed_settlement)
    override fun place_settlement(x: Int, y: Int, nodeNum: Int): Node {
        if (placed_settlement != null)
            break_rule("Too many settlements placed in setup")
        val node: Node = super.place_settlement(x, y, nodeNum)
        placed_settlement = node

        val settlement_count = board.all_nodes().count { n -> n.has_city() && n.city?.color == player.color }
        if (settlement_count == 2) {
            // A Player gets cards for the 2nd settlement he places
            val touching_hexes = node.hexes.keys.filterNot { it.resource == null }
            val resources = touching_hexes.map { it.get_card() }
            player.add_cards(resources.map { ResourceCard(it) })
        } else if (settlement_count != 1) {
            break_rule("Bad Game state.  Wrong # of settlements placed: " + settlement_count)
        }
        return node
    }

    override fun done() {
        assert_rule(placed_settlement == null, "You cannot end a setup turn ,out placing a settlement.")
        assert_rule(placed_road == null, "You cannot end a setup turn ,out placing a road.")
        force_done()
        log.debug("Turn done")
    }

    override fun roll_dice(): Pair<Int, Int> {
        throw RuleException("Cannot roll dice during setup")
    }

    override fun place_city(x: Int, y: Int, nodeNum: Int): Node {
        throw RuleException("Cannot place city during setup")
    }

    override fun buy_development_card(): DevelopmentCard {
        throw RuleException("Cannot buy development card during setup")
    }

    override fun check_state(s: String) {}
    override fun pay_for(piece: Purchaseable): Unit {}
}

/**
 * Represents a quote for trading cards
 * This is basically the Bidder saying "I'll give you giveType for receiveType"
 * The Trader then accepts the quote afterwards
 */
class Quote(
        val bidder: PlayerInfo?,
        val receiveType: Resource,
        val receiveNum: Int,
        val giveType: Resource,
        val giveNum: Int) {

    override fun toString() = "[Quote $receiveNum $receiveType for $giveNum $giveType from $bidder]"
    fun validate(admin: Admin): Unit {
        if (bidder != null) {
            val player = admin.get_player(bidder.color)
            if (player.countResources(giveType) < giveNum) {
                throw  IllegalStateException("Bidder $bidder does not have enough resources for this quote:${this} " +
                        "Bidder cards:${player.get_cards()}")
            }
        }
    }

    /** Is another quote a better deal? (And also the same resources) */
    fun isBetterQuote(other: Quote): Boolean = other.receiveNum < this.receiveNum &&
            other.giveType == this.giveType && other.receiveType == this.receiveType

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Quote

        if (bidder != other.bidder) return false
        if (receiveType != other.receiveType) return false
        if (receiveNum != other.receiveNum) return false
        if (giveType != other.giveType) return false
        if (giveNum != other.giveNum) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bidder?.hashCode() ?: 0
        result = 31 * result + receiveType.hashCode()
        result = 31 * result + receiveNum
        result = 31 * result + giveType.hashCode()
        result = 31 * result + giveNum
        return result
    }

}
