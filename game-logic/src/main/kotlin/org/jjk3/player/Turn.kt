package org.jjk3.player

import org.apache.log4j.Logger
import org.jjk3.core.*
import org.jjk3.player.Turn.ReasonToTakeCards.*
import kotlin.IllegalArgumentException

open class Turn(val admin: Admin, val player: Player, val board: Board) : HasTurnState() {

    enum class ReasonToTakeCards {
        PurchasedSettlement,
        PurchasedCity,
        PurchasedRoad,
        Rolled7,
        MovedBandit,
        Trade,
        Other
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
    fun activeCards() = player.playedDevCards.filter { ! it.isDone }

    open fun buyDevelopmentCard(): DevelopmentCard {
        assertState(TurnState.RolledDice)
        val (newDevCards, card) = admin.board.developmentCards.removeRandom()
        admin.board.developmentCards = newDevCards
        payFor(card)
        player.giveCards(listOf(card))
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
        //TODO: implement rule checking here so people can't move the bandit whenever they want.
        board.moveBandit(_tile)

        admin.observers.forEach {
            it.playerMovedBandit(player.ref(), _tile)
        }

        // Take a card from a player the colors of the cities touching the tile
        val touchingColors = _tile.nodes_with_cities().map { it.city !!.color }.distinct()
        val touchingPlayers = touchingColors.map {
            getPlayer(it) !!.ref()
        }.filterNot { it == player.ref() }.toList()

        if (! touchingPlayers.isEmpty()) {
            val playerToTakeFrom: PlayerReference =
                    if (touchingPlayers.size == 1) {
                        touchingPlayers.first()
                    } else {
                        player.selectPlayer(touchingPlayers, 1)
                    }
            takeRandomCard(playerToTakeFrom)
            admin.observers.forEach { it.playerStoleCard(player.ref(), playerToTakeFrom, 1) }
        }
    }

    fun receivedQuote(quote: Quote): Unit {
        allQuotes += quote
    }

    open fun placeRoad(edgeCoordinate: EdgeCoordinate) {
        assertCanPlaceRoad(edgeCoordinate)
        // If a player uses a roadBuilding card, then his purchasedRoads > 0
        // they shouldn't pay for the road in this case.
        val road: Road =
                if (player.freeRoads() > 0) {
                    player.removeFreeRoads(1)
                    board.getPiecesForSale(player.color!!).takeRoad()
                } else {
                    purchaseRoad()
                }
        board.placeRoad(road, edgeCoordinate)
        admin.observers.forEach { it.placedRoad(player.ref(), edgeCoordinate) }
        admin.checkForWinner()
    }

    private fun assertCanPlaceRoad(edgeCoordinate: EdgeCoordinate) {
        assertGameIsNotDone()
        assertState(TurnState.RolledDice)
        val edge = board.getEdge(edgeCoordinate)
        if (! board.getValidRoadSpots(player.color!!).contains(edge)) {
            breakRule("Invalid Road Placement $edgeCoordinate")
        }
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
        if (! board.getValidSettlementSpots(player.color!!).contains(node)) {
            breakRule("Invalid Settlement Placement $coord")
        }
    }

    open fun placeCity(coord: NodeCoordinate): Node {
        assertCanPlaceCity(coord)
        val node = board.getNode(coord)
        val city = purchaseCity()
        board.getPiecesForSale(player.color!!).putBack(node.city as Settlement)
        board.placeCity(city, coord)
        admin.observers.forEach { it.placedCity(player.ref(), coord) }
        admin.checkForWinner()
        return node
    }

    private fun assertCanPlaceCity(coord: NodeCoordinate) {
        assertGameIsNotDone()
        assertState(TurnState.RolledDice)
        val node = board.getNode(coord)
        assertRule(node.hasCity(), "Invalid City Placement.  There is no settlement at $coord")
        assertRule(node.city?.color == player.color, "Invalid City Placement. " +
                "Settlement has wrong color at $coord. expected: ${player.color} was:${node.city?.color}")
        assertRule(node.city is Settlement, "A city must be placed on top of a Settlement, not a ${node.city}")
    }

    protected fun assertGameIsNotDone() {
        if (admin.isGameDone()) {
            breakRule("Game is already over")
        }
    }

    private fun purchaseCity() = board.getPiecesForSale(player.color!!).takeCity().also { payFor(it) }
    private fun purchaseSettlement() = board.getPiecesForSale(player.color!!).takeSettlement().also { payFor(it) }
    protected fun purchaseRoad() = board.getPiecesForSale(player.color!!).takeRoad().also { payFor(it) }
    /**
     * Makes a player pay for a piece
     * Throws an exception if the player doesn't have enough cards,
     * but doesn't mutate the player if an exception is thrown.
     */
    open fun payFor(piece: Purchaseable): Unit {
        val reason = when (piece) {
            is Settlement -> PurchasedSettlement
            is City -> PurchasedCity
            is Road -> PurchasedRoad
            else -> throw IllegalArgumentException("Unknown piece: $piece")
        }
        player.takeCards(piece.price.map(::ResourceCard), reason)
    }

    open fun done() {
        if (! admin.isGameDone()) {
            assertRule(hasRolled() && ! state.is_terminal_state,
                    "Turn ended before dice were rolled.  Current state: $state")
            assertRule(activeCards().isEmpty(), "All card actions must be finished.")
            assertRule(player.freeRoads() == 0, "You cannot end a turn while there are purchased pieces to place")
            assertRule(! activeCards().any { it.single_turn_card }, "There are still active cards: ${activeCards()}")
        }
        forceDone()
        log.debug("Turn done")
    }

    fun forceDone() {
        state = TurnState.Done
    }

    /** Make sure that the player has enough cards to make the offer */
    fun validateQuoteLists(wantList: List<Resource>, giveList: List<Resource>) {
        for (giveType in giveList.distinct()) {
            if (player.countResources(giveType) == 0) {
                breakRule("Offering $giveType but only has ${player.cards}")
            }
        }
    }

    /**
     * Take a random card from another player and plus it to your own cards
     * If player has no cards, do nothing
     */
    private fun takeRandomCard(victim: PlayerReference): Unit {
        val realVictim = getPlayer(victim.color!!) !!
        if (realVictim.resourceCards().isEmpty()) {
            log.debug("Could not take a random card from $victim")
            return
        }
        val res = realVictim.resourceCards().pick_random()
        realVictim.takeCards(listOf(res), MovedBandit)
        player.giveCards(listOf(res))
    }

    fun assertRule(condition: Boolean, errorMsg: String) {
        if (! condition) {
            breakRule(errorMsg)
        }
    }

    private fun assertCanPlayeDevelopmentCard(card: DevelopmentCard) {
        assertRule(! admin.isGameDone(), "Game is already over")
        assertRule(player.cards.contains(card),
                "Player does not own the card being played. cards:" + player.cards)
        assertRule(! isDone(), "Turn is done")
        assertRule(card is SoldierCard || this.hasRolled(),
                "$card played before dice were rolled. Current State: $state")
    }

    fun playDevelopmentCard(card: DevelopmentCard) {
        assertCanPlayeDevelopmentCard(card)
        card.use(this)
        player.takeCards(listOf(card), Other)
        player.playedDevCard(card)
        admin.checkForWinner()
    }

    fun acceptQuote(quote: Quote) {
        assertValidQuote(quote)
        val bidder = quote.bidder?.let { getPlayer(it.color!!) }
        val bidderName = bidder?.color ?: "The Bank"
        log.debug("$player is accepting a trade from $bidderName ${quote.giveNum} ${quote.giveType}" +
                " for ${quote.receiveNum} ${quote.receiveType}")

        val cardsToAdd = (1..quote.giveNum).map { ResourceCard(quote.giveType) }
        val cardsToLose = (1..quote.receiveNum).map { ResourceCard(quote.receiveType) }

        player.giveCards(cardsToAdd)
        player.takeCards(cardsToLose, Trade)
        if (bidder != null) {
            bidder.giveCards(cardsToLose)
            bidder.takeCards(cardsToAdd, Trade)
        }
    }

    private fun assertValidQuote(quote: Quote) {
        assertState(TurnState.RolledDice)
        if (! allQuotes.contains(quote)) {
            breakRule("Attempting to accept a quote that hasn't been received:$quote Other quotes: $allQuotes")
        }
        admin.validateQuote(quote)
        // Check to make sure that everybody has enough cards
        if (player.countResources(quote.receiveType) < quote.receiveNum) {
            breakRule("You don't have enough cards for this quote: " + quote)
        }
        val bidder = quote.bidder?.let { getPlayer(it.color!!) }
        bidder?.let {
            if (it.countResources(quote.giveType) < quote.giveNum) {
                breakRule("Bidder $it doesn't have enough cards for this quote: $quote")
            }
        }
    }

}