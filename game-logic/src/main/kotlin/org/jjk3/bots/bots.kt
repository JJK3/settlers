package org.jjk3.bots

import org.apache.log4j.Logger
import org.jjk3.board.*
import org.jjk3.gameplay.Admin
import org.jjk3.gameplay.Quote
import org.jjk3.gameplay.SetupTurn
import org.jjk3.gameplay.Turn
import org.jjk3.gameplay.actions.*
import org.jjk3.gameplay.actions.setup.PlaceSettlementAndRoad
import org.jjk3.player.Player
import org.jjk3.player.PlayerReference


/**
 * This removes elements from an array ,out making the array uniq.
 * i.e. [1,1,1,2].difference_,out_uniq([1]) = [1,1,2]
 */
fun <T> List<T>.diff_without_unique(other: List<T>): List<T> {
    var result = this
    other.forEach { obj: T ->
        val i = result.indexOf(obj)
        if (i > - 1)
            result = result.remove(i)
    }
    return result
}

//only abstract since i haven't finished this yet
abstract class Bot(val admin: Admin) : Player() {

    companion object {
        val log: Logger = Logger.getLogger(Bot::class.java)
    }

    var delay = 0;
    var chatter = false
}

interface SetupTurnStrategy {
    fun handleSetupTurn(turn: SetupTurn)
}

interface MoveBanditStrategy {
    fun move_bandit(oldLocation: HexCoordinate): HexCoordinate
    fun select_player(players: List<PlayerReference>, reason: Int): PlayerReference
}

class HighestProbablitySetup(val player: Player) : SetupTurnStrategy {
    override fun handleSetupTurn(turn: SetupTurn) {
        val spots = player.board() !!.getValidSettlementSpots()
        if (spots.isEmpty()) {
            throw IllegalStateException("Could not find any settlement spots")
        }
        val spot = spots.first()
        val settlementLocation = spot.coords()
        val copy = player.board() !!.copy()
        copy.placeCity(Settlement(player.color !!), settlementLocation)
        val roadSpots = copy.getValidRoadSpots(player.color !!, settlementLocation)
        if (roadSpots.isEmpty()) {
            throw IllegalStateException("Could not find any road spots")
        }
        turn.play(PlaceSettlementAndRoad(settlementLocation, roadSpots.first().coords()))
        turn.done()
    }
}

/**
 * Part of a bot that takes cards from random players ,out bias.
 * It also places the bandit on anyone but itself.
 */
class TakeCardsFromAnyone(val player: Player) : MoveBanditStrategy {
    /**
     * #Tell this player to move the bandit
     * [oldLocation] the hex where the bandit currently sits
     * return a  hex
     */
    override fun move_bandit(oldLocation: HexCoordinate): HexCoordinate {
        val preferred = player.board() !!.tiles.values.find { t ->
            val has_me = t.nodes.any { n -> n.hasCity() && n.city !!.color == player.color }
            val has_other = t.nodes.any { n -> n.hasCity() && n.city !!.color != player.color }
            ! t.hasBandit && ! has_me && has_other
        }
        //admin.chat_msg(self, "gimme a card!") if chatter
        if (preferred != null) {
            return preferred.coords
        }
        val preferred2 = player.board() !!.tiles.values.find { t ->
            val has_me = t.nodes.any { n -> n.hasCity() && n.city !!.color == player.color }
            ! t.hasBandit && ! has_me
        }
        if (preferred2 != null) {
            return preferred2.coords
        }
        return player.board() !!.tiles.values.find { ! it.hasBandit } !!.coords
    }

    /** Ask the player to choose a player among the given list */
    override fun select_player(players: List<PlayerReference>, reason: Int): PlayerReference {
        return players.find { it.color != player.color } ?:
                throw  IllegalStateException("I'm being forced to select myself")
    }
}

/** An AI player that just chooses moves at random */
class RandomPlayer(admin: Admin) : Bot(admin) {

    val moveBanditStrategy = TakeCardsFromAnyone(this)
    override fun moveBandit(oldLocation: HexCoordinate): HexCoordinate = moveBanditStrategy.move_bandit(oldLocation)
    override fun selectPlayer(players: List<PlayerReference>,
                              reason: Int): PlayerReference = moveBanditStrategy.select_player(
            players, reason)

    val setupTurnStrategy = HighestProbablitySetup(this)
    override fun takeTurn(turn: Turn) {
        try {
            when (turn) {
                is SetupTurn -> setupTurnStrategy.handleSetupTurn(turn)
                else -> handleNormalTurn(turn)
            }
        } finally {
            updateBoard(board() !!)
            turn.forceDone()
        }
    }

    fun handleNormalTurn(turn: Turn) {
        if (! turn.hasRolled()) {
            turn.play(RollDiceAction())
        }
        val piecesForSale = board() !!.getPiecesForSale(color !!)
        val city = City(color !!)
        if (canAfford(listOf(city)) && piecesForSale.cities.size() > 0) {
            var spots = board() !!.getValidCitySpots(color !!)
            if (spots.isNotEmpty()) {
                spots = spots.sortedBy { DiceProbabilities.getProbability(it) }
                turn.play(PlaceCityAction(spots.last().coords()))
                //turn.taint
                /*if (chatter)
                    admin.chat_msg(this, "We built this city...on wheat && ore...")*/
                log.info("${this}: Bought City")
            }
        }

        if (! admin.isGameDone() && canAfford(listOf(Settlement(color !!))) && piecesForSale.settlements.size() > 0) {
            val spots = board() !!.getValidSettlementSpots(color !!)
            if (spots.isNotEmpty()) {
                //spots.sort!{|a,b| a.getProbability <=> b.getProbability}
                turn.play(PlaceSettlementAction(spots.last().coords()))
                //turn.taint
                log.info("${this}: Bought Settlement")
            }
        }

        if (! admin.isGameDone() && canAfford(listOf(Road(color !!))) && piecesForSale.roads.size() > 0) {
            val spots = board() !!.getValidRoadSpots(color !!)
            val longest_road = board() !!.hasLongestRoad(color !!)
            if (board() !!.getValidSettlementSpots(color !!).size < 4) {
                if (spots.isNotEmpty()) {
                    turn.play(PlaceRoadAction(spots.first().coords()))
                }
                //turn.taint
                if (! longest_road && board() !!.hasLongestRoad(color !!)) {
                    log.info("${this}: Got longest road")
                }
            }
        }
        if (! admin.isGameDone()) {
            Resource.values().forEach { r ->
                if (countResources(r) < 3) {
                    val giveCards = resourceCards().distinct().filter { countResources(it.resource) > 3 }
                    if (! giveCards.isEmpty()) {
                        val quotes = turn.play(RequestQuotesAction(setOf(r), giveCards.map { it.resource }.toSet()))
                        if (quotes.isNotEmpty()) {
                            val q = quotes.sortedBy { it.receiveNum }.first()
                            turn.play(AcceptQuoteAction(q))
                        }
                    }
                }
            }
        }
    }

    /** This bot will offer trades if it has more than 4 of 1 kind of card. */
    override fun getUserQuotes(playerRef: PlayerReference, wantList: Set<Resource>,
                               giveList: Set<Resource>): Set<Quote> {
        var result: Set<Quote> = emptySet()
        wantList.forEach { w ->
            giveList.forEach { g ->
                if (countResources(g) < 4 && countResources(w) > 4)
                    result += Quote(this.ref(), g, 1, w, 1)
            }
        }
        return result
    }

    /**
     * Ask the player to select some cards from a list.
     * This is used when a player must discard or resource
     * monopoly or year of plenty
     */
    override fun selectResourceCards(cards: List<Resource>, count: Int, reason: Int): List<Resource> {
        var selection: List<Resource> = emptyList()
        var list_copy = cards
        (1..count).forEach {
            val (item, _list) = list_copy.remove_random()
            list_copy = _list
            selection += item
        }
        return selection
    }
}

/**
 * A Bot that sets a goal for a single piece to purchase
 * It then tries to trade and obtain cards to buy that piece.
 * It's one step smarter than RandomPlayer
 */
class SinglePurchasePlayer(admin: Admin) : Bot(admin) {

    val moveBanditStrategy = TakeCardsFromAnyone(this)
    override fun moveBandit(oldLocation: HexCoordinate): HexCoordinate = moveBanditStrategy.move_bandit(oldLocation)
    override fun selectPlayer(players: List<PlayerReference>,
                              reason: Int): PlayerReference = moveBanditStrategy.select_player(
            players, reason)

    val setupTurnStrategy = HighestProbablitySetup(this)
    fun do_setup_turn(turn: SetupTurn) = setupTurnStrategy.handleSetupTurn(turn)
    var desired_piece: Purchaseable? = null
    var cardsNeeded: List<Resource> = emptyList()
    override fun takeTurn(turn: Turn) {
        super.takeTurn(turn)
        //if (delay > 0) Thread.sleep(delay)
        try {
            if (turn is SetupTurn) {
                do_setup_turn(turn)
            } else {
                if (! turn.hasRolled()) {
                    turn.play(RollDiceAction())
                }
                desired_piece = calculate_desired_piece(turn)

                if (desired_piece != null) {
                    cardsNeeded = calculate_cards_needed(desired_piece !!).distinct()
                    var break1 = false
                    (1..2).forEach {
                        //Limit this to 2 times
                        if (! break1) {
                            log.debug("Bot " + this + " is attempting to trade")
                            var cardsIDontNeed = resourceCards().map { it.resource }
                            if (desired_piece != null) {
                                val price = desired_piece !!.price
                                cardsIDontNeed = cardsIDontNeed.diff_without_unique(price)
                            }
                            cardsIDontNeed = cardsIDontNeed.diff_without_unique(cardsNeeded)

                            if (cardsNeeded.isNotEmpty() && cardsIDontNeed.size >= 2) {
                                val qs = turn.play(RequestQuotesAction(cardsNeeded.toSet(), cardsIDontNeed.toSet()))
                                if (qs.isNotEmpty()) {
                                    val q = qs.sortedBy { it.receiveNum }.first()
                                    if (countResources(q.receiveType) >= q.receiveNum) {
                                        turn.play(AcceptQuoteAction(q))
                                    }
                                } else {
                                    break1 = true
                                }
                            } else {
                                break1 = true
                            }
                        }
                    }

                    if (desired_piece != null && canAfford(listOf(desired_piece !!))) {
                        place_desired_piece()
                    }
                }
            }
        } catch(e: Exception) {
            log.error("BLAH color(${ref().color}) $e", e)
        } finally {
            //if turn.tainted?
            updateBoard(board() !!)
            turn.done()
        }
    }

    // Ask this bot for a trade
    // This bot will try to get cards it needs for its desired piece
    override fun getUserQuotes(playerRef: PlayerReference, wantList: Set<Resource>,
                               giveList: Set<Resource>): Set<Quote> {
        var result: Set<Quote> = emptySet()
        val iWant = giveList.intersect(cardsNeeded)
        if (! iWant.isEmpty()) {
            //They're offering something I need
            val iHaveToOffer = (resourceCards().map { it.resource }.intersect(wantList)) - cardsNeeded
            if (! iHaveToOffer.isEmpty()) {
                iWant.forEach { want ->
                    iHaveToOffer.forEach { have ->
                        result += Quote(this.ref(), want, 1, have, 1)
                    }
                }
            }
        }
        return result
    }

    // Ask the player to select some cards from a list.
    // This is used when a player must discard or resource monopoly or year of plenty
    override fun selectResourceCards(cards: List<Resource>, count: Int, reason: Int): List<Resource> {
        var selection: List<Resource> = emptyList()
        var cards_to_select_from = cards

        //First try to only get rid of cards that i don't need
        var remaining_cards = cards.diff_without_unique(cardsNeeded)
        while (remaining_cards.isNotEmpty() && selection.size < count) {
            val (chosen_card, _remaining_cards) = remaining_cards.remove_random()
            remaining_cards = _remaining_cards
            selection += chosen_card

            val initial_index = cards_to_select_from.indexOf(chosen_card)
            cards_to_select_from = cards_to_select_from.remove(initial_index)
        }

        //Then, if you still have to get rid of cards, pick at random
        remaining_cards = cards_to_select_from
        while (selection.size < count) {
            val (item, _remaining_cards) = remaining_cards.remove_random()
            remaining_cards = _remaining_cards
            selection += item
        }
        return selection
    }

    //calculate which piece to try for based on the current turn.
    private fun calculate_desired_piece(turn: Turn): Purchaseable? {
        val piecesForSale = board() !!.getPiecesForSale(color !!)
        if (piecesForSale.cities.size() > 0) {
            val spots = board() !!.getValidCitySpots(color !!)
            if (spots.isNotEmpty()) {
                return City(color !!)
            }
        }
        if (piecesForSale.settlements.size() > 0) {
            val spots = board() !!.getValidSettlementSpots(color !!)
            if (spots.isNotEmpty()) {
                return Settlement(color !!)
            }
        }
        if (piecesForSale.roads.size() > 0) {
            val spots = board() !!.getValidRoadSpots(color !!)
            if (spots.isNotEmpty()) {
                return Road(color !!)
            }
        }
        log.warn("$this can't figure out where to build anything. Pieces left:$piecesForSale")
        return null
    }

    // Calculate the cards that this player needs to get to purchase
    // the desired_piece
    // Class -> Array of Cards
    fun calculate_cards_needed(piece: Purchaseable): List<Resource> {
        return piece.price.diff_without_unique(resourceCards().map { it.resource })
    }

    // Place your desired piece
    // This method assumes that the player can afford the piece
    fun place_desired_piece() {
        when (desired_piece) {
            is Road -> {
                val spots = board() !!.getValidRoadSpots(color !!)

                // Organize the edges by the randomNumber of adjecent roads
                // and whether or not they have cities on them

                //spots = spots.sort_and_partition{|e| e.getAdjecentEdges.size}
                //spots.map!{|chunk| chunk.partition{|e| e.nodes.all?{|n| !n.city} }}
                //spots.flatten!

                if (spots.isEmpty()) throw  IllegalStateException("No Valid Spots")
                // Find a spot that will increase your longest road
                val firstEdge = board() !!.allEdges().find { e -> e.hasRoad() && e.road !!.color == color } !!

                val longest = board() !!.getLongestRoad(firstEdge).size
                var foundGoodSpot = false
                var break1 = false
                spots.forEach { spot ->
                    if (! break1) {
                        board() !!.placeRoad(Road(color !!), spot.coords())
                        val _longest = board() !!.getLongestRoad(firstEdge).size
                        board() !!.removeRoad(spot.coords())
                        if (_longest > longest) {
                            currentTurn !!.play(PlaceRoadAction(spot.coords()))
                            foundGoodSpot = true
                            break1 = true
                        }
                    }
                }
                //Then, try to pick an edge that has no cities on it.
                if (! foundGoodSpot) {
                    currentTurn !!.play(PlaceRoadAction(spots.first().coords()))
                }
            }
            is Settlement -> {
                val settlement_spots = board() !!.getValidSettlementSpots(color !!).sortedBy {
                    DiceProbabilities.getProbability(it)
                }
                if (settlement_spots.isEmpty()) {
                    throw IllegalStateException("No Valid Spots")
                }
                //spots = spots.sortBy{it.getProbability}
                currentTurn !!.play(PlaceSettlementAction(settlement_spots.last().coords()))
            }
            is City -> {
                val city_spots = board() !!.getValidCitySpots(color !!).sortedBy {
                    DiceProbabilities.getProbability(it)
                }
                if (city_spots.isEmpty()) {
                    throw IllegalStateException("No Valid Spots")
                }
                //city_spots.sort!{|a,b| a.getProbability <=> b.getProbability}
                /*if (chatter) admin.chat_msg(this, "We built this city...on wheat and ore...")*/
                currentTurn !!.play(PlaceCityAction(city_spots.last().coords()))
            }
            else -> throw IllegalStateException("Invalid desired_piece")
        }
        updateBoard(board() !!)
    }
}