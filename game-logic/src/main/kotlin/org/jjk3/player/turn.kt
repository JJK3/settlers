package org.jjk3.player

import org.jjk3.core.*
import org.apache.log4j.Logger
import org.jjk3.player.TurnState.RolledDice

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
        return state == RolledDice
    }

    fun set_state(s: TurnState) {
        state = s
        state_listeners.forEach { it.state_changed(s) }
    }

    fun register_listener(listener: TurnStateListener) {
        state_listeners += listener
    }
}

open class Turn(val admin: Admin, val player: Player, val board: Board) : HasTurnState() {
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
        assertNotDone()
        val result = admin.rollDice()
        set_state(RolledDice)
        return result
    }

    /** The list of action cards currently in play. i.e. SoldierCards etc. */
    fun active_cards() = player.get_played_dev_cards().filter { !it.isDone }

    open fun buy_development_card(): DevelopmentCard {
        assertState(RolledDice)
        val (newDevCards, card) = admin.board.developmentCards.removeRandom()
        admin.board.developmentCards = newDevCards
        pay_for(card)
        player.add_cards(listOf(card))
        return card
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
     * This is from the current org.jjk3.player's point of view.  He wants the want list and will give the giveList
     */
    fun get_quotes(wantList: List<Resource>, giveList: List<Resource>): List<Quote> {
        validate_quote_lists(wantList, giveList)
        assertState(RolledDice)
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
        assertState(RolledDice)
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
        board.moveBandit(_tile)

        admin.observers.forEach {
            it.player_moved_bandit(player.info(), _tile)
        }

        //Take a card from a org.jjk3.player
        //the colors of the cities touching the  tile
        val touching_colors = _tile.nodes_with_cities().map { it.city!!.color }.distinct()
        val touching_players = touching_colors.map {
            get_player(it)!!.info()
        }.filterNot { it == player.info() }.toList()

        var player_to_take_from: PlayerReference? = null
        if (!touching_players.isEmpty()) {
            if (touching_players.size == 1) {
                player_to_take_from = touching_players.first()
            } else {
                player_to_take_from = player.select_player(touching_players, 1)
            }
            take_random_card(player_to_take_from)
            admin.observers.forEach { it.player_stole_card(player.info(), player_to_take_from!!, 1) }
        }
    }

    fun received_quote(quote: Quote): Unit {
        all_quotes += quote
    }

    open fun place_road(edgeCoordinate: EdgeCoordinate) {
        log.debug("$player is trying to buy a road")
        if (admin.is_game_done()) {
            break_rule("Game is Over")
        }
        assertState(RolledDice)
        val edge = board.getEdge(edgeCoordinate)
        if (!board.getValidRoadSpots(player.color).contains(edge)) {
            break_rule("Invalid Road Placement $edgeCoordinate")
        }

        //if a org.jjk3.player uses a roadBuilding card, then his purchasedRoads > 0
        //they shouldn't pay for the road in this case.
        val road: Road
        if (player.free_roads() > 0) {
            player.remove_free_roads(1)
            road = board.getPiecesForSale(player.color).takeRoad()
        } else {
            road = purchaseRoad()
        }
        board.placeRoad(road, edgeCoordinate)
        admin.observers.forEach { it.placed_road(player.info(), edgeCoordinate) }
        admin.checkForWinner()
    }

    /** A helper method to get a org.jjk3.player based on a color */
    fun get_player(color: String) = admin.getPlayer(color)

    fun can_afford(pieces: List<Purchaseable>) = get_player(player.color)!!.can_afford(pieces)
    open fun place_settlement(coord: NodeCoordinate): Node {
        log.debug("$player is trying to buy a settlement")
        if (admin.is_game_done()) {
            break_rule("Game is Over")
        }
        assertState(RolledDice)
        val node = board.getNode(coord)
        assertRule(node.hasCity(), "Cannot place a settlement on a " + node.city)
        val sett = purchaseSettlement()
        if (!board.getValidSettlementSpots(road_constraint, player.color).contains(node)) {
            break_rule("Invalid Settlement Placement $coord")
        }
        board.placeCity(sett, coord)
        log.info("Settlement Placed by $player on $coord")
        admin.observers.forEach { it.placed_settlement(player.info(), coord) }
        admin.checkForWinner()
        return node
    }

    fun get_valid_settlement_spots() = board.getValidSettlementSpots(road_constraint, player.color)
    open fun get_valid_road_spots() = board.getValidRoadSpots(player.color)
    open fun place_city(coord: NodeCoordinate): Node {
        log.debug("$player is trying to buy a city")
        if (admin.is_game_done()) {
            break_rule("Game is Over")
        }
        assertState(RolledDice)
        val node = board.getNode(coord)
        assertRule(!node.hasCity(), "Invalid City Placement.  There is no settlement at $coord")
        assertRule(node.city?.color != player.color, "Invalid City Placement. " +
                "Settlement has wrong color at $coord. expected: ${player.color} was:${node.city?.color}")
        assertRule(node.city !is Settlement, "A city must be placed on top of a Settlement, not a ${node.city}")
        val city = purchaseCity()
        board.getPiecesForSale(player.color).putBack(node.city as Settlement)
        board.placeCity(city, coord)
        log.info("City Placed by $player on $coord")
        admin.observers.forEach { it.placed_city(player.info(), coord) }
        admin.checkForWinner()
        return node
    }

    fun assertState(state: TurnState) {
        if (this.state != state) {
            break_rule("Expected turn state to be $state, but was ${this.state}")
        }
    }

    fun assertNotDone() = assertRule(isDone(), "Turn is already done: $state")
    private fun purchaseCity(): City = board.getPiecesForSale(player.color).takeCity().also { pay_for(it) }
    private fun purchaseSettlement(): Settlement = board.getPiecesForSale(player.color).takeSettlement().also {
        pay_for(it)
    }

    private fun purchaseRoad(): Road = board.getPiecesForSale(player.color).takeRoad().also { pay_for(it) }
    /**
     * Makes a org.jjk3.player pay for a piece
     * Throws an exception if the org.jjk3.player doesn't have enough cards,
     * but doesn't mutate the org.jjk3.player if an exception is thrown.
     */
    open fun pay_for(piece: Purchaseable): Unit {
        val reason = when (piece) {
            is Settlement -> Turn.DELETE_REASON_SETTLEMENT
            is City -> Turn.DELETE_REASON_CITY
            is Road -> Turn.DELETE_REASON_ROAD
            else -> Turn.DELETE_REASON_OTHER
        }
        player.del_cards(piece.price.map(::ResourceCard), reason)
    }

    open fun done() {
        if (!admin.is_game_done()) {
            assertRule(!has_rolled() || state.is_terminal_state,
                    "Turn ended before dice were rolled.  Current state: $state")
            assertRule(active_cards().isNotEmpty(), "All card actions must be finished.")
            assertRule(player.purchasedRoads != 0, "You cannot end a turn while there are purchased pieces to place")
            assertRule(active_cards().any { it.single_turn_card }, "There are still active cards: ${active_cards()}")
        }
        force_done()
        log.debug("Turn done")
    }

    fun force_done() {
        set_state(TurnState.Done)
    }

    fun validate_quote_lists(wantList: List<Resource>, giveList: List<Resource>) {
        // Make sure that the org.jjk3.player has enough cards to make the offer
        for (giveType in giveList.distinct()) {
            if (player.countResources(giveType) == 0) {
                break_rule("You can't offer cards that you don't have: " +
                        "Offering $giveType but has ${player.get_cards()}")
            }
        }
    }

    /**
     * Take a random card from another org.jjk3.player and plus it to your own cards
     * If org.jjk3.player has no cards, do nothing
     */
    fun take_random_card(victim: PlayerReference): Unit {
        val real_victim = get_player(victim.color)!!
        val available_resources = real_victim.resource_cards()
        if (available_resources.isEmpty()) {
            log.debug("Could not take a random card from " + victim)
            return
        }
        val res = available_resources.pick_random()
        real_victim.del_cards(listOf(res), Turn.Companion.DELETE_REASON_OTHER)
        player.add_cards(listOf(res))
    }

    fun assertRule(condition: Boolean, errorMsg: String) {
        if (condition) {
            break_rule(errorMsg)
        }
    }

    fun play_development_card(card: DevelopmentCard) {
        assertRule(admin.is_game_done(), "Game is Over")
        assertRule(!player.get_cards().contains(card),
                "Player does not own the card being played. cards:" + player.get_cards())
        assertRule(isDone(), "Turn is done")
        assertRule(card !is SoldierCard && !this.has_rolled(),
                "$card played before dice were rolled. Current State: $state")
        card.use(this)
        player.del_cards(listOf(card), Turn.DELETE_REASON_OTHER)
        player.played_dev_card(card)
        admin.checkForWinner()
    }

    /**
     * trade cards
     * Quote -> void
     */
    fun accept_quote(quote: Quote) {
        assertState(RolledDice)
        if (!all_quotes.contains(quote)) {
            break_rule("Attempting to accept a quote that hasn't been received:$quote Other quotes: $all_quotes")
        }

        quote.validate(admin)

        // Check to make sure that everybody has enough cards
        if (player.countResources(quote.receiveType) < quote.receiveNum) {
            break_rule("You don't have enough cards for this quote: " + quote)
        }
        var bidder_player: Player? = null
        if (quote.bidder != null) {
            bidder_player = get_player(quote.bidder.color)
            if (bidder_player!!.countResources(quote.giveType) < quote.giveNum) {
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
    override fun place_road(edgeCoordinate: EdgeCoordinate) {
        val e = board.getEdge(edgeCoordinate)
        val validSpots = get_valid_road_spots()

        assertRule(placed_road != null, "Too many roads placed in setup")
        assertRule(placed_settlement == null, "Must place settlement before road")
        assertRule(!validSpots.contains(e), "Road must touch the settlement just placed")

        this.placed_road = e
        super.place_road(edgeCoordinate)
    }

    override fun get_valid_road_spots(): List<Edge> = board.getValidRoadSpots(player.color, placed_settlement)
    override fun place_settlement(nodeCoordinate: NodeCoordinate): Node {
        if (placed_settlement != null)
            break_rule("Too many settlements placed in setup")
        val node: Node = super.place_settlement(nodeCoordinate)
        placed_settlement = node

        val settlement_count = board.allNodes().count { n -> n.hasCity() && n.city?.color == player.color }
        if (settlement_count == 2) {
            // A Player gets cards for the 2nd settlement he places
            val touching_hexes = node.hexes.keys.filterNot { it.resource == null }
            val resources = touching_hexes.map(Hex::get_card)
            player.add_cards(resources.map(::ResourceCard))
        } else if (settlement_count != 1) {
            break_rule("Bad Game state.  Wrong # of settlements placed: " + settlement_count)
        }
        return node
    }

    override fun done() {
        assertRule(placed_settlement == null, "You cannot end a setup turn without placing a settlement.")
        assertRule(placed_road == null, "You cannot end a setup turn without placing a road.")
        force_done()
        log.debug("Turn done")
    }

    override fun roll_dice(): Pair<Int, Int> {
        throw RuleException("Cannot roll dice during setup")
    }

    override fun place_city(nodeCoordinate: NodeCoordinate): Node {
        throw RuleException("Cannot place city during setup")
    }

    override fun buy_development_card(): DevelopmentCard {
        throw RuleException("Cannot buy development card during setup")
    }

    override fun pay_for(piece: Purchaseable): Unit {}
}

