package org.jjk3.player

import org.jjk3.core.*

//only abstract since i haven't finished this yet
abstract class Bot(first_name: String,
                   last_name: String,
                   val admin: Admin,
                   cities: Int = 4,
                   settlements: Int = 5,
                   roads: Int = 15) : Player(first_name, last_name, cities, settlements, roads) {

    var delay = 0;
    var chatter = false
}

interface SetupTurnStrategy {
    fun do_setup_turn(turn: SetupTurn)
}

interface MoveBanditStrategy {
    fun move_bandit(old_hex: Hex): Hex
    fun select_player(players: List<PlayerReference>, reason: Int): PlayerReference
}

class HighestProbablitySetup(val player: Player) : SetupTurnStrategy {
    override fun do_setup_turn(turn: SetupTurn) {
        val spots = player.board!!.getValidSettlementSpots(false, player.color)
        if (spots.isEmpty())
            throw  IllegalStateException("Could not find any settlement spots")
        //spots.sort!{|a,b| a.getProbability <=> b.getProbability}
        val spot = spots.first()
        val has_placed_settlement = turn.placed_settlement != null

        val node = turn.placed_settlement ?: turn.place_settlement(spot.coords())

        /**
         * Wait for the settlement to actually be placed.
         * Really, this is a bit of a hack, since the org.jjk3.board SHOULD be updated before the method is done.
         */
        //timeout(10) do
        //while org.jjk3.board.getHex(sx,sy).nodes[sn].city == null
        //  sleep(0.1)
        // }
        /// }

        val settlementNode = player.board!!.getNode(node.coords())
        if (!settlementNode.hasCity()) {
            throw IllegalStateException("Node (${node.coords()}) Should have a Settlement, but it doesn't")
        }
        val road_spots = player.board!!.getValidRoadSpots(player.color, settlementNode)
        road_spots.firstOrNull()!!.let { turn.place_road(it.coords()) }
        turn.done()
    }
}

/**
 * Part of a bot that takes cards from random players ,out bias.
 * It also places the bandit on anyone but itself.
 */
class TakeCardsFromAnyone(val player: Player) : MoveBanditStrategy {
    /**
     * #Tell this org.jjk3.player to move the bandit
     * [old_hex] the hex where the bandit currently sits
     * return a  hex
     */
    override fun move_bandit(old_hex: Hex): Hex {
        val preferred = player.board!!.tiles.values.find { t ->
            val has_me = t.nodes.any { n -> n.hasCity() && n.city!!.color == player.color }
            val has_other = t.nodes.any { n -> n.hasCity() && n.city!!.color != player.color }
            !t.has_bandit && !has_me && has_other
        }
        //admin.chat_msg(self, "gimme a card!") if chatter
        if (preferred != null) {
            return preferred
        }
        val preferred2 = player.board!!.tiles.values.find { t ->
            val has_me = t.nodes.any { n -> n.hasCity() && n.city!!.color == player.color }
            !t.has_bandit && !has_me
        }
        if (preferred2 != null) {
            return preferred2
        }
        return player.board!!.tiles.values.find { !it.has_bandit }!!
    }

    /** Ask the org.jjk3.player to choose a org.jjk3.player among the given list */
    override fun select_player(players: List<PlayerReference>, reason: Int): PlayerReference {
        return players.find { it.color != player.color } ?:
                throw  IllegalStateException("I'm being forced to select myself")
    }
}

/** An AI org.jjk3.player that just chooses moves at random */
class RandomPlayer(first_name: String,
                   last_name: String,
                   admin: Admin,
                   cities: Int = 0,
                   settlements: Int = 0,
                   roads: Int = 0) : Bot(first_name, last_name, admin, cities, settlements, roads) {

    val moveBanditStrategy = TakeCardsFromAnyone(this)
    override fun move_bandit(old_hex: Hex): Hex = moveBanditStrategy.move_bandit(old_hex)
    override fun select_player(players: List<PlayerReference>,
                               reason: Int): PlayerReference = moveBanditStrategy.select_player(
            players, reason)

    val setupTurnStrategy = HighestProbablitySetup(this)
    fun do_setup_turn(turn: SetupTurn) = setupTurnStrategy.do_setup_turn(turn)
    override fun take_turn(turn: Turn, is_setup: Boolean) {
        //   super.take_turn(turn, is_setup)
        try {
            //  if (delay > 0) Thread.sleep(delay)
            if (is_setup) {
                do_setup_turn(turn as SetupTurn)
            } else {
                if (!turn.has_rolled()) {
                    turn.roll_dice()
                }
                val piecesForSale = board!!.getPiecesForSale(color)
                val city = City(color)
                if (can_afford(listOf(city)) && piecesForSale.cities.size()> 0) {
                    var spots = board!!.getValidCitySpots(color)
                    if (spots.isNotEmpty()) {
                        spots = spots.sortedBy { Dice.getProbability(it) }
                        turn.place_city(spots.last().coords())
                        //turn.taint
                        /*if (chatter)
                            admin.chat_msg(this, "We built this city...on wheat && ore...")*/
                        log.info("<BOT: " + full_name() + ": Bought City> ")
                    }
                }

                if (!admin.is_game_done() && can_afford(listOf(Settlement(color))) && piecesForSale.settlements.size() > 0) {
                    val spots = board!!.getValidSettlementSpots(true, color)
                    if (spots.isNotEmpty()) {
                        //spots.sort!{|a,b| a.getProbability <=> b.getProbability}
                        turn.place_settlement(spots.last().coords())
                        //turn.taint
                        log.info("<BOT: " + full_name() + ": Bought Settlement> ")
                    }
                }

                if (!admin.is_game_done() && can_afford(listOf(Road(color))) && piecesForSale.roads.size() > 0) {
                    val spots = board!!.getValidRoadSpots(color)
                    val longest_road = board!!.hasLongestRoad(color)
                    if (board!!.getValidSettlementSpots(true, color).size < 4) {
                        if (spots.isNotEmpty()) {
                            turn.place_road(spots.first().coords())
                        }
                        //turn.taint
                        if (!longest_road && board!!.hasLongestRoad(color)) {
                            log.info("<BOT " + full_name() + ": Got longest road> ")
                        }
                    }
                }
                if (!admin.is_game_done()) {
                    Resource.values().forEach { r ->
                        if (countResources(r) < 3) {
                            val giveCards = resource_cards().distinct().filter { countResources(it.resource) > 3 }
                            if (!giveCards.isEmpty()) {
                                val qs = turn.get_quotes(listOf(r), giveCards.map { it.resource })
                                if (qs.isNotEmpty()) {
                                    val q = qs.sortedBy { it.receiveNum }.first()
                                    turn.accept_quote(q)
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            //if turn.tainted?
            update_board(board!!)
            turn.force_done()
        }
    }

    /** This bot will offer trades if it has more than 4 of 1 kind of card. */
    override fun get_user_quotes(player_reference: PlayerReference, wantList: List<Resource>,
                                 giveList: List<Resource>): List<Quote> {
        var result: List<Quote> = emptyList()
        wantList.forEach { w ->
            giveList.forEach { g ->
                if (countResources(g) < 4 && countResources(w) > 4)
                    result += Quote(this.info(), g, 1, w, 1)
            }
        }
        return result
    }

    /**
     * Ask the org.jjk3.player to select some cards from a list.
     * This is used when a org.jjk3.player must discard or resource
     * monopoly or year of plenty
     */
    override fun select_resource_cards(cards: List<Resource>, count: Int, reason: Int): List<Resource> {
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
class SinglePurchasePlayer(first_name: String,
                           last_name: String,
                           admin: Admin,
                           cities: Int = 0,
                           settlements: Int = 0,
                           roads: Int = 0
) : Bot(first_name, last_name, admin, cities, settlements, roads) {

    val moveBanditStrategy = TakeCardsFromAnyone(this)
    override fun move_bandit(old_hex: Hex): Hex = moveBanditStrategy.move_bandit(old_hex)
    override fun select_player(players: List<PlayerReference>,
                               reason: Int): PlayerReference = moveBanditStrategy.select_player(
            players, reason)

    val setupTurnStrategy = HighestProbablitySetup(this)
    fun do_setup_turn(turn: SetupTurn) = setupTurnStrategy.do_setup_turn(turn)
    var desired_piece: Purchaseable? = null
    var cards_needed: List<Resource> = emptyList()
    override fun take_turn(turn: Turn, is_setup: Boolean) {
        super.take_turn(turn, is_setup)
        //if (delay > 0) Thread.sleep(delay)
        try {
            if (turn.is_setup) {
                do_setup_turn(turn as SetupTurn)
            } else {
                if (!turn.has_rolled()) {
                    turn.roll_dice()
                }
                desired_piece = calculate_desired_piece(turn)

                if (desired_piece != null) {
                    cards_needed = calculate_cards_needed(desired_piece!!).distinct()
                    var break1 = false
                    (1..2).forEach {
                        //Limit this to 2 times
                        if (!break1) {
                            log.debug("Bot " + this + " is attempting to trade")
                            var cardsIDontNeed = resource_cards().map { it.resource }
                            if (desired_piece != null) {
                                val price = desired_piece!!.price
                                cardsIDontNeed = cardsIDontNeed.diff_without_unique(price)
                            }
                            cardsIDontNeed = cardsIDontNeed.diff_without_unique(cards_needed)

                            if (cards_needed.isNotEmpty() && cardsIDontNeed.size >= 2) {
                                val qs = turn.get_quotes(cards_needed, cardsIDontNeed)
                                if (qs.isNotEmpty()) {
                                    val q = qs.sortedBy { it.receiveNum }.first()
                                    if (countResources(q.receiveType) >= q.receiveNum) {
                                        turn.accept_quote(q)
                                    }
                                } else {
                                    break1 = true
                                }
                            } else {
                                break1 = true
                            }
                        }
                    }

                    if (desired_piece != null && can_afford(listOf(desired_piece!!))) {
                        place_desired_piece()
                    }
                }
            }
        } catch(e: Exception) {
            log.error("BLAH color(${info().color}) $e", e)
        } finally {
            //if turn.tainted?
            update_board(board!!)
            turn.done()
        }
    }

    // Ask this bot for a trade
    // This bot will try to get cards it needs for its desired piece
    override fun get_user_quotes(player_reference: PlayerReference, wantList: List<Resource>,
                                 giveList: List<Resource>): List<Quote> {
        var result: List<Quote> = emptyList()
        val iWant = giveList.intersect(cards_needed)
        if (!iWant.isEmpty()) {
            //They're offering something I need
            val iHaveToOffer = (resource_cards().map { it.resource }.intersect(wantList)) - cards_needed
            if (!iHaveToOffer.isEmpty()) {
                iWant.forEach { want ->
                    iHaveToOffer.forEach { have ->
                        result += Quote(this.info(), want, 1, have, 1)
                    }
                }
            }
        }
        return result
    }

    // Ask the org.jjk3.player to select some cards from a list.
    // This is used when a org.jjk3.player must discard or resource monopoly or year of plenty
    override fun select_resource_cards(cards: List<Resource>, count: Int, reason: Int): List<Resource> {
        var selection: List<Resource> = emptyList()
        var cards_to_select_from = cards

        //First try to only get rid of cards that i don't need
        var remaining_cards = cards.diff_without_unique(cards_needed)
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
        val piecesForSale = board!!.getPiecesForSale(color)
        if (piecesForSale.cities.size() > 0) {
            val spots = board!!.getValidCitySpots(color)
            if (spots.isNotEmpty()) {
                return City(color)
            }
        }
        if (piecesForSale.settlements.size() > 0) {
            val spots = board!!.getValidSettlementSpots(true, color)
            if (spots.isNotEmpty()) {
                return Settlement(color)
            }
        }
        if (piecesForSale.roads.size() > 0) {
            val spots = board!!.getValidRoadSpots(color)
            if (spots.isNotEmpty()) {
                return Road(color)
            }
        }
        log.warn("$this can't figure out where to build anything. Pieces left:$piecesForSale")
        return null
    }

    // Calculate the cards that this org.jjk3.player needs to get to purchase
    // the desired_piece
    // Class -> Array of Cards
    fun calculate_cards_needed(piece: Purchaseable): List<Resource> {
        return piece.price.diff_without_unique(resource_cards().map { it.resource })
    }

    // Place your desired piece
    // This method assumes that the org.jjk3.player can afford the piece
    fun place_desired_piece() {
        when (desired_piece) {
            is Road -> {
                val spots = board!!.getValidRoadSpots(color)

                // Organize the edges by the randomNumber of adjecent roads
                // and whether or not they have cities on them

                //spots = spots.sort_and_partition{|e| e.getAdjecentEdges.size}
                //spots.map!{|chunk| chunk.partition{|e| e.nodes.all?{|n| !n.city} }}
                //spots.flatten!

                if (spots.isEmpty()) throw  IllegalStateException("No Valid Spots")
                // Find a spot that will increase your longest road
                val firstEdge = board!!.allEdges().find { e -> e.hasRoad() && e.road!!.color == color }!!

                val longest = board!!.getLongestRoad(firstEdge).size
                var foundGoodSpot = false
                var break1 = false
                spots.forEach { spot ->
                    if (!break1) {
                        board!!.placeRoad(Road(color), spot.coords())
                        val _longest = board!!.getLongestRoad(firstEdge).size
                        board!!.removeRoad(spot.coords())
                        if (_longest > longest) {
                            this.current_turn!!.place_road(spot.coords())
                            foundGoodSpot = true
                            break1 = true
                        }
                    }
                }
                //Then, try to pick an edge that has no cities on it.
                if (!foundGoodSpot) {
                    current_turn!!.place_road(spots.first().coords())
                }
            }
            is Settlement -> {
                val settlement_spots = board!!.getValidSettlementSpots(true, color).sortedBy {
                    Dice.getProbability(it)
                }
                if (settlement_spots.isEmpty()) {
                    throw IllegalStateException("No Valid Spots")
                }
                //spots = spots.sortBy{it.getProbability}
                current_turn!!.place_settlement(settlement_spots.last().coords())
            }
            is City -> {
                val city_spots = board!!.getValidCitySpots(color).sortedBy { Dice.getProbability(it) }
                if (city_spots.isEmpty()) {
                    throw IllegalStateException("No Valid Spots")
                }
                //city_spots.sort!{|a,b| a.getProbability <=> b.getProbability}
                /*if (chatter) admin.chat_msg(this, "We built this city...on wheat and ore...")*/
                current_turn!!.place_city(city_spots.last().coords())
            }
            else -> throw IllegalStateException("Invalid desired_piece")
        }
        update_board(board!!)
    }
}