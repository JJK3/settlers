package core

import org.apache.log4j._
import UtilList._

//only abstract since i haven't finished this yet
abstract class Bot(first_name: String,
    last_name: String,
    admin: Admin,
    log: Logger = null,
    cities: Int = 4,
    settlements: Int = 5,
    roads: Int = 15,
    board: Board) extends Player(first_name, last_name, admin, log, cities, settlements, roads, board) {

    var delay = 0;
    var chatter = false
}

trait HighestProbablitySetup {
    def board(): Board
    def color(): String

    def do_setup_turn(turn: SetupTurn) = {
        val spots = board.get_valid_settlement_spots(false, color)
        if (spots.isEmpty)
            throw new IllegalStateException("Could not find any settlement spots")
        //spots.sort!{|a,b| a.get_hex_prob <=> b.get_hex_prob}
        val spot = spots.first
        val has_placed_settlement = turn.placed_settlement != null

        val (sx: Int, sy: Int, sn: Int) = if (has_placed_settlement) {
            turn.placed_settlement //this is for when a bot takes over for a human player
        } else {
            turn.place_settlement(spot.x, spot.y, spot.nodeNum).coords
        }

        /**
         * Wait for the settlement to actually be placed.
         * Really, this is a bit of a hack, since the board SHOULD be updated before the method is done.
         */
        //timeout(10) do
        //while board.getTile(sx,sy).nodes[sn].city == null
        //  sleep(0.1)
        // }
        /// }

        val settlementNode = board.getTile(sx, sy).nodes(sn)
        if (!settlementNode.has_city)
            throw new IllegalStateException("Node (" + sx + "," + sy + "," + sn + ") Should have a Settlement, but it doesn't")
        val road_spots = board.get_valid_road_spots(color, settlementNode)
        val road_spot = road_spots.first
        if (turn.placed_road == null)
            turn.place_road(road_spot.x, road_spot.y, road_spot.edgeNum)
        turn.done
    }
}

/**
 * Part of a bot that takes cards from random players without bias.
 * It also places the bandit on anyone but itself.
 */
trait TakeCardsFromAnyone {
    def board(): Board
    def color(): String

    /**
     * #Tell this player to move the bandit
     * [old_hex] the hex where the bandit currently sits
     * return a new hex
     */
    def move_bandit(old_hex: Hex): Hex = {
        val preferred = board.tiles.values.find { t =>
            val has_me = t.nodes.exists { n => n.has_city && n.city.color == color }
            val has_other = t.nodes.exists { n => n.has_city && n.city.color != color }
            !t.has_bandit && !has_me && has_other
        }.getOrElse(null)
        //admin.chat_msg(self, "gimme a card!") if chatter
        if (preferred != null)
            return preferred
        val preferred2 = board.tiles.values.find { t =>
            val has_me = t.nodes.exists { n => n.has_city && n.city.color == color }
            !t.has_bandit && !has_me
        }.getOrElse(null)
        if (preferred2 != null) return preferred2
        board.tiles.values.find { !_.has_bandit }.getOrElse(null)
    }

    /** Ask the player to choose a player among the given list */
    def select_player(players: List[PlayerInfo], reason: Int) = {
        val other = players.find { _.color != color }.getOrElse(null)
        if (other == null)
            throw new IllegalStateException("I'm being forced to select myself")
        other
    }
}

/** An AI player that just chooses moves at random */
class RandomPlayer(first_name: String,
    last_name: String,
    admin: Admin,
    log: Logger = null,
    cities: Int = 0,
    settlements: Int = 0,
    roads: Int = 0,
    board: Board) extends Bot(first_name, last_name, admin, log, cities, settlements, roads, board) with TakeCardsFromAnyone with HighestProbablitySetup {

    override def take_turn(turn: Turn, is_setup: Boolean) = {
        //   super.take_turn(turn, is_setup)
        try {
            //  if (delay > 0) Thread.sleep(delay)
            if (is_setup) {
                do_setup_turn(turn.asInstanceOf[SetupTurn])
            } else {
                if (!turn.has_rolled()) {
                    turn.roll_dice
                }

                val city = new City(color)
                if (can_afford(List(city)) && piecesLeft(classOf[City]) > 0) {
                    var spots = board.get_valid_city_spots(color)
                    if (spots.length > 0) {
                        spots = spots.sortBy { _.get_hex_prob }
                        turn.place_city(spots.last.x, spots.last.y, spots.last.nodeNum)
                        //turn.taint
                        if (chatter)
                            admin.chat_msg(this, "We built this city...on wheat && ore...")
                        log.info("<BOT: " + full_name + ": Bought City> ")
                    }
                }

                if (!admin.is_game_done && can_afford(List(new Settlement(color))) && piecesLeft(classOf[Settlement]) > 0) {
                    val spots = board.get_valid_settlement_spots(true, color)
                    if (spots.length > 0) {
                        //spots.sort!{|a,b| a.get_hex_prob <=> b.get_hex_prob}
                        turn.place_settlement(spots.last.x, spots.last.y, spots.last.nodeNum)
                        //turn.taint
                        log.info("<BOT: " + full_name + ": Bought Settlement> ")
                    }
                }

                if (!admin.is_game_done() && can_afford(List(new Road(color))) && piecesLeft(classOf[Road]) > 0) {
                    val spots = board.get_valid_road_spots(color)
                    val longest_road = board.has_longest_road(color)
                    if (board.get_valid_settlement_spots(true, color).length < 4) {
                        if (spots.length > 0) {
                            turn.place_road(spots.first.x, spots.first.y, spots.first.edgeNum)
                        }
                        //turn.taint
                        if (!longest_road && board.has_longest_road(color)) {
                            log.info("<BOT " + full_name + ": Got longest road> ")
                        }
                    }
                }
                if (!admin.is_game_done()) {
                    HexType.RESOURCE_TYPES.foreach { r =>
                        if (get_cards(r) < 3) {
                            val giveCards = resource_cards.distinct.filter { get_cards(_) > 3 }
                            if (!giveCards.isEmpty) {
                                val qs = turn.get_quotes(List(r), giveCards)
                                if (qs.length > 0) {
                                    val q = qs.sortBy { _.receiveNum }.first
                                    turn.accept_quote(q)
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            //if turn.tainted?
            update_board(board)
            turn.done
        }
    }

    /** This bot will offer trades if it has more than 4 of 1 kind of card. */
    override def get_user_quotes(player_info: PlayerInfo, wantList: List[Resource], giveList: List[Resource]): List[Quote] = {
        var result: List[Quote] = Nil
        wantList.foreach { w =>
            giveList.foreach { g =>
                if (get_cards(g) < 4 && get_cards(w) > 4)
                    result = result :+ new Quote(this.info, g, 1, w, 1)
            }
        }
        result
    }

    /**
     * Ask the player to select some cards from a list.
     * This is used when a player must discard or resource
     * monopoly or year of plenty
     */
    override def select_resource_cards(cards: List[Resource], count: Int, reason: Int): List[Resource] = {
        var selection: List[Resource] = Nil
        var list_copy = cards
        (1 to count).foreach { i =>
            val (item, new_list) = list_copy.remove_random()
            list_copy = new_list
            selection = selection :+ item
        }
        selection
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
    log: Logger = null,
    cities: Int = 0,
    settlements: Int = 0,
    roads: Int = 0,
    board: Board) extends Bot(first_name, last_name, admin, log, cities, settlements, roads, board) with TakeCardsFromAnyone with HighestProbablitySetup {

    var desired_piece: Purchaseable = null
    var cards_needed: List[Resource] = Nil

    override def take_turn(turn: Turn, is_setup: Boolean) = {
        super.take_turn(turn, is_setup)
        //if (delay > 0) Thread.sleep(delay)
        try {
            if (turn.is_setup) {
                do_setup_turn(turn.asInstanceOf[SetupTurn])
            } else {
                if (!turn.has_rolled()) {
                    turn.roll_dice
                }
                desired_piece = calculate_desired_piece(turn)

                if (desired_piece != null) {
                    cards_needed = calculate_cards_needed(desired_piece).distinct
                    var break = false
                    (1 to 2).foreach { i => //Limit this to 2 times
                        if (!break) {
                            log.debug("Bot " + this + " is attempting to trade")
                            var cardsIDontNeed = resource_cards
                            if (desired_piece != null) {
                                val price = admin.get_price(desired_piece)
                                cardsIDontNeed = cardsIDontNeed.diff_without_unique(price)
                            }
                            cardsIDontNeed = cardsIDontNeed.diff_without_unique(cards_needed)

                            if (cards_needed.size > 0 && cardsIDontNeed.size >= 2) {
                                val qs = turn.get_quotes(cards_needed, cardsIDontNeed)
                                if (qs.size > 0) {
                                    val q = qs.sortBy { _.receiveNum }.first
                                    if (get_cards(q.receiveType) >= q.receiveNum) {
                                        turn.accept_quote(q)
                                    }
                                } else {
                                    break = true
                                }
                            } else {
                                break = true
                            }
                        }
                    }

                    if (desired_piece != null && can_afford(List(desired_piece))) {
                        place_desired_piece
                    }
                }
            }
        } catch {
            case e => log.error("BLAH color(" + info.color + ") " + e, e)
        } finally {
            //if turn.tainted?
            update_board(board)
            turn.done
        }
    }

    // Ask this bot for a trade
    // This bot will try to get cards it needs for its desired piece
    override def get_user_quotes(player_info: PlayerInfo, wantList: List[Resource], giveList: List[Resource]): List[Quote] = {
        var result: List[Quote] = Nil
        if (wantList == null || giveList == null || cards_needed == null)
            return result
        val iWant = giveList.intersect(cards_needed)
        if (!iWant.isEmpty) {
            //They're offering something I need
            val iHaveToOffer = (resource_cards().intersect(wantList)) -- cards_needed
            if (!iHaveToOffer.isEmpty) {
                iWant.foreach { want =>
                    iHaveToOffer.foreach { have =>
                        result = result :+ new Quote(this.info, want, 1, have, 1)
                    }
                }
            }
        }
        result
    }

    // Ask the player to select some cards from a list.
    // This is used when a player must discard or resource monopoly or year of plenty
    override def select_resource_cards(cards: List[Resource], count: Int, reason: Int): List[Resource] = {
        var selection: List[Resource] = Nil
        var cards_to_select_from = cards

        //First try to only get rid of cards that i don't need
        var remaining_cards = cards.diff_without_unique(cards_needed)
        while (remaining_cards.size > 0 && selection.size < count) {
            val (chosen_card, new_remaining_cards) = remaining_cards.remove_random()
            remaining_cards = new_remaining_cards
            selection = selection :+ chosen_card

            val initial_index = cards_to_select_from.indexOf(chosen_card)
            cards_to_select_from = cards_to_select_from.remove(initial_index)
        }

        //Then, if you still have to get rid of cards, pick at random
        remaining_cards = cards_to_select_from
        while (selection.size < count) {
            val (item, new_remaining_cards) = remaining_cards.remove_random()
            remaining_cards = new_remaining_cards
            selection = selection :+ item
        }
        selection
    }

    //calculate which piece to try for based on the current turn.
    private def calculate_desired_piece(turn: Turn): Purchaseable = {
        if (piecesLeft(classOf[City]) > 0) {
            val spots = board.get_valid_city_spots(color)
            if (spots.length > 0) return new City(color)
        }
        if (piecesLeft(classOf[Settlement]) > 0) {
            val spots = board.get_valid_settlement_spots(true, color)
            if (spots.length > 0) return new Settlement(color)
        }
        if (piecesLeft(classOf[Road]) > 0) {
            val spots = board.get_valid_road_spots(color)
            if (spots.length > 0) return new Road(color)
        }
        log.warn(this + " can't figure out where to build anything. Pieces left:" + piecesLeft)
        return null
    }

    // Calculate the cards that this player needs to get to purchase
    // the desired_piece
    // Class -> Array of Cards
    def calculate_cards_needed(piece: Purchaseable): List[Resource] = {
        var cards_needed: List[Resource] = Nil
        val price = admin.get_price(piece)
        if (price == null) return cards_needed
        val price_map = price.groupBy { _.getClass }
        price_map.foreach { kv =>
            val count = kv._2.size
            val card = kv._2.first
            val need = count - get_cards(card)
            if (need > 0)
                cards_needed = cards_needed ++ Nil.padTo(need, card)
        }
        cards_needed
    }

    // Place your desired piece
    // This method assumes that the player can afford the piece
    def place_desired_piece = {
        desired_piece match {
            case r: Road => {
                var spots = board.get_valid_road_spots(color)

                // Organize the edges by the number of adjecent roads
                // and whether or not they have cities on them

                //spots = spots.sort_and_partition{|e| e.get_adjecent_edges.size}
                //spots.map!{|chunk| chunk.partition{|e| e.nodes.all?{|n| !n.city} }}
                //spots.flatten!

                if (spots.length == 0) throw new IllegalStateException("No Valid Spots")
                // Find a spot that will increase your longest road
                val firstEdge = board.edges.find { e => e.has_road && e.road.color == color }.getOrElse(null)

                val longest = board.find_longest_road(firstEdge)
                var foundGoodSpot = false
                var break = false
                spots.foreach { spot =>
                    if (!break) {
                        board.place_road(new Road(color), spot.coords._1, spot.coords._2, spot.coords._3)
                        val new_longest = board.find_longest_road(firstEdge)
                        board.remove_road(spot.coords._1, spot.coords._2, spot.coords._3)
                        if (new_longest > longest) {
                            this.current_turn.place_road(spot.coords._1, spot.coords._2, spot.coords._3)
                            foundGoodSpot = true
                            break = true
                        }
                    }
                }
                //Then, try to pick an edge that has no cities on it.
                if (!foundGoodSpot) {
                    current_turn.place_road(spots.first.coords._1, spots.first.coords._2, spots.first.coords._3)
                }
            }
            case s: Settlement => {
                val settlement_spots = board.get_valid_settlement_spots(true, color).sortBy { _.get_hex_prob }
                if (settlement_spots.length == 0)
                    throw new IllegalStateException("No Valid Spots")
                //spots = spots.sortBy{_.get_hex_prob}
                current_turn.place_settlement(settlement_spots.last.coords._1, settlement_spots.last.coords._2, settlement_spots.last.coords._3)
            }
            case c: City => {
                val city_spots = board.get_valid_city_spots(color).sortBy { _.get_hex_prob }
                if (city_spots.length == 0) throw new IllegalStateException("No Valid Spots")
                //city_spots.sort!{|a,b| a.get_hex_prob <=> b.get_hex_prob}
                if (chatter) admin.chat_msg(this, "We built this city...on wheat and ore...")
                current_turn.place_city(city_spots.last.coords._1, city_spots.last.coords._2, city_spots.last.coords._3)
            }
            case _ => throw new IllegalStateException("Invalid desired_piece")
        }
        update_board(board)
    }
}