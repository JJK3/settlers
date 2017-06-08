package org.jjk3.player

import org.apache.log4j.Logger
import org.jjk3.core.*

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
    var allQuotes = emptyList<Quote>()
    open fun rollDice(): Pair<Int, Int> {
        assertState(TurnState.Active)
        val result = admin.rollDice()
        state = TurnState.RolledDice
        return result
    }

    /** The list of action cards currently in play. i.e. SoldierCards etc. */
    fun activeCards() = player.get_played_dev_cards().filter { !it.isDone }

    open fun buyDevelopmentCard(): DevelopmentCard {
        assertState(TurnState.RolledDice)
        val (newDevCards, card) = admin.board.developmentCards.removeRandom()
        admin.board.developmentCards = newDevCards
        payFor(card)
        player.add_cards(listOf(card))
        return card
    }

    /** The user has broken a rule.  They going to be kicked out */
    fun breakRule(msg: String) {
        val error = RuleException(msg)
        state = TurnState.DoneWithError
        throw error
    }

    /**
     *  Gets a list of quotes from the bank and other users
     * Optionally takes a block that iterates through each quote as they come
     * (List(CardType), List(CardType)) -> List(Quote)
     * This is from the current player's point of view.  He wants the want list and will give the giveList
     */
    fun getQuotes(wantList: List<Resource>, giveList: List<Resource>): List<Quote> {
        validateQuoteLists(wantList, giveList)
        assertState(TurnState.RolledDice)
        val quotes = admin.getQuotes(player, wantList, giveList)
        allQuotes = (allQuotes + quotes).distinct()
        return quotes
    }

    /**
     * returns a list of Quote objects from the bank
     * (CardType, CardType) -> List(Quote)
     */
    fun getQuotesFromBank(wantList: List<Resource>, giveList: List<Resource>): List<Quote> {
        validateQuoteLists(wantList, giveList)
        assertState(TurnState.RolledDice)
        val quotes = admin.getQuotesFromBank(player, wantList, giveList)
        allQuotes = (allQuotes + quotes).distinct()
        return quotes
    }

    /**
     * Move the bandit to a  tile.
     * This is called by the admin and the soldier card
     */
    fun moveBandit(_tile: Hex) {
        //TODO: implement rule checking here so people can't move the
        //bandit whenever they want.
        board.moveBandit(_tile)

        admin.observers.forEach {
            it.playerMovedBandit(player.ref(), _tile)
        }

        //Take a card from a player
        //the colors of the cities touching the  tile
        val touching_colors = _tile.nodes_with_cities().map { it.city!!.color }.distinct()
        val touching_players = touching_colors.map {
            getPlayer(it)!!.ref()
        }.filterNot { it == player.ref() }.toList()

        var player_to_take_from: PlayerReference? = null
        if (!touching_players.isEmpty()) {
            if (touching_players.size == 1) {
                player_to_take_from = touching_players.first()
            } else {
                player_to_take_from = player.select_player(touching_players, 1)
            }
            takeRandomCard(player_to_take_from)
            admin.observers.forEach { it.playerStoleCard(player.ref(), player_to_take_from!!, 1) }
        }
    }

    fun receivedQuote(quote: Quote): Unit {
        allQuotes += quote
    }

    open fun placeRoad(edgeCoordinate: EdgeCoordinate) {
        log.debug("$player is trying to buy a road")
        assertGameIsNotDone()
        assertState(TurnState.RolledDice)
        val edge = board.getEdge(edgeCoordinate)
        if (!board.getValidRoadSpots(player.color).contains(edge)) {
            breakRule("Invalid Road Placement $edgeCoordinate")
        }

        // If a player uses a roadBuilding card, then his purchasedRoads > 0
        // they shouldn't pay for the road in this case.
        val road: Road
        if (player.free_roads() > 0) {
            player.remove_free_roads(1)
            road = board.getPiecesForSale(player.color).takeRoad()
        } else {
            road = purchaseRoad()
        }
        board.placeRoad(road, edgeCoordinate)
        admin.observers.forEach { it.placedRoad(player.ref(), edgeCoordinate) }
        admin.checkForWinner()
    }

    /** A helper method to get a player based on a color */
    fun getPlayer(color: String) = admin.getPlayer(color)

    open fun placeSettlement(coord: NodeCoordinate): Node {
        assertCanPlaceSettlement(coord)
        val node = board.getNode(coord)
        val sett = purchaseSettlement()
        board.placeCity(sett, coord)
        admin.observers.forEach { it.placedSettlement(player.ref(), coord) }
        admin.checkForWinner()
        return node
    }

    open protected fun assertCanPlaceSettlement(coord: NodeCoordinate) {
        assertGameIsNotDone()
        assertState(TurnState.RolledDice)
        val node = board.getNode(coord)
        if (!board.getValidSettlementSpots(player.color).contains(node)) {
            breakRule("Invalid Settlement Placement $coord")
        }
    }

    open fun placeCity(coord: NodeCoordinate): Node {
        assertGameIsNotDone()
        assertState(TurnState.RolledDice)
        val node = board.getNode(coord)
        assertRule(node.hasCity(), "Invalid City Placement.  There is no settlement at $coord")
        assertRule(node.city?.color == player.color, "Invalid City Placement. " +
                "Settlement has wrong color at $coord. expected: ${player.color} was:${node.city?.color}")
        assertRule(node.city is Settlement, "A city must be placed on top of a Settlement, not a ${node.city}")
        val city = purchaseCity()
        board.getPiecesForSale(player.color).putBack(node.city as Settlement)
        board.placeCity(city, coord)
        admin.observers.forEach { it.placedCity(player.ref(), coord) }
        admin.checkForWinner()
        return node
    }

    protected fun assertGameIsNotDone() {
        if (admin.isGameDone()) {
            breakRule("Game is already over")
        }
    }

    private fun purchaseCity() = board.getPiecesForSale(player.color).takeCity().also { payFor(it) }
    private fun purchaseSettlement() = board.getPiecesForSale(player.color).takeSettlement().also { payFor(it) }
    protected fun purchaseRoad() = board.getPiecesForSale(player.color).takeRoad().also { payFor(it) }
    /**
     * Makes a player pay for a piece
     * Throws an exception if the player doesn't have enough cards,
     * but doesn't mutate the player if an exception is thrown.
     */
    open fun payFor(piece: Purchaseable): Unit {
        val reason = when (piece) {
            is Settlement -> DELETE_REASON_SETTLEMENT
            is City -> DELETE_REASON_CITY
            is Road -> DELETE_REASON_ROAD
            else -> DELETE_REASON_OTHER
        }
        player.takeCards(piece.price.map(::ResourceCard), reason)
    }

    open fun done() {
        if (!admin.isGameDone()) {
            assertRule(hasRolled() && !state.is_terminal_state,
                    "Turn ended before dice were rolled.  Current state: $state")
            assertRule(activeCards().isEmpty(), "All card actions must be finished.")
            assertRule(player.purchasedRoads == 0, "You cannot end a turn while there are purchased pieces to place")
            assertRule(!activeCards().any { it.single_turn_card }, "There are still active cards: ${activeCards()}")
        }
        forceDone()
        log.debug("Turn done")
    }

    fun forceDone() {
        state = TurnState.Done
    }

    fun validateQuoteLists(wantList: List<Resource>, giveList: List<Resource>) {
        // Make sure that the player has enough cards to make the offer
        for (giveType in giveList.distinct()) {
            if (player.countResources(giveType) == 0) {
                breakRule("You can't offer cards that you don't have: " +
                        "Offering $giveType but has ${player.get_cards()}")
            }
        }
    }

    /**
     * Take a random card from another player and plus it to your own cards
     * If player has no cards, do nothing
     */
    fun takeRandomCard(victim: PlayerReference): Unit {
        val real_victim = getPlayer(victim.color)!!
        val available_resources = real_victim.resource_cards()
        if (available_resources.isEmpty()) {
            log.debug("Could not take a random card from " + victim)
            return
        }
        val res = available_resources.pick_random()
        real_victim.takeCards(listOf(res), DELETE_REASON_OTHER)
        player.add_cards(listOf(res))
    }

    fun assertRule(condition: Boolean, errorMsg: String) {
        if (!condition) {
            breakRule(errorMsg)
        }
    }

    fun playDevelopmentCard(card: DevelopmentCard) {
        assertRule(!admin.isGameDone(), "Game is already over")
        assertRule(player.get_cards().contains(card),
                "Player does not own the card being played. cards:" + player.get_cards())
        assertRule(!isDone(), "Turn is done")
        assertRule(card is SoldierCard || this.hasRolled(),
                "$card played before dice were rolled. Current State: $state")
        card.use(this)
        player.takeCards(listOf(card), DELETE_REASON_OTHER)
        player.played_dev_card(card)
        admin.checkForWinner()
    }

    /**
     * trade cards
     * Quote -> void
     */
    fun acceptQuote(quote: Quote) {
        assertState(TurnState.RolledDice)
        if (!allQuotes.contains(quote)) {
            breakRule("Attempting to accept a quote that hasn't been received:$quote Other quotes: $allQuotes")
        }

        quote.validate(admin)

        // Check to make sure that everybody has enough cards
        if (player.countResources(quote.receiveType) < quote.receiveNum) {
            breakRule("You don't have enough cards for this quote: " + quote)
        }
        var bidder_player: Player? = null
        if (quote.bidder != null) {
            bidder_player = getPlayer(quote.bidder.color)
            if (bidder_player!!.countResources(quote.giveType) < quote.giveNum) {
                breakRule("Bidder $bidder_player doesn't have enough cards for this quote: $quote")
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
        player.takeCards((0..quote.receiveNum).map {
            ResourceCard(quote.receiveType)
        }, DELETE_REASON_TRADE)
        if (bidder_player != null) {
            bidder_player.add_cards((0..quote.receiveNum).map { ResourceCard(quote.receiveType) })
            bidder_player.takeCards((0..quote.giveNum).map {
                ResourceCard(quote.giveType)
            }, DELETE_REASON_TRADE)
        }
    }

}