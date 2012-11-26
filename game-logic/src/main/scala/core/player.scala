package core

import scala.collection.immutable.Map
import org.apache.log4j._

trait PlayerListener {
    def got_turn(turn: Any)
}

class Message(val message: String, val sender: PlayerInfo) extends Serializable{}

abstract class Player(
    val first_name: String,
    val last_name: String,
    val admin: Admin,
    val log: Logger = null,
    cities: Int = 4,
    settlements: Int = 5,
    roads: Int = 15,
    protected var _board: Board = null) extends GameObserver {

    var pic: String = ""
    var pic_square: String = ""
    var _color: String = ""
    var cards_mutex = new Mutex()
    var pieces_mutex = new Mutex()
    var board_mutex = new Mutex()
    var purchased_pieces = 0
    var piecesLeft = Map[Class[_ <: BoardPiece], Int]().withDefaultValue(0)
    var msgLog = List[Message]()
    var preferred_color: String = null
    var listeners = List[PlayerListener]() //These listeners are used by the UI
    var extra_victory_points: Int = 0
    var played_dev_cards: List[DevelopmentCard] = Nil
    private var game_finished: Boolean = false
    protected var current_turn: Turn = null
    protected var cards: Map[Card, Int] = Map().withDefaultValue(0)

    piecesLeft += (classOf[City] -> cities)
    piecesLeft += (classOf[Settlement] -> settlements)
    piecesLeft += (classOf[Road] -> roads)

    def give_free_roads(num_roads: Int): Unit = purchased_pieces += num_roads
    def remove_free_roads(num_roads: Int): Unit = purchased_pieces -= num_roads
    def free_roads: Int = purchased_pieces
    def get_played_dev_cards: List[DevelopmentCard] = played_dev_cards

    /** Notifys the player that they have offically played a development card */
    def played_dev_card(card: DevelopmentCard): Unit = played_dev_cards = played_dev_cards :+ card
    def info = new PlayerInfo(this)

    def color = _color
    def color_=(color: String) = { _color = color }

    def board = _board
    def board_=(b: Board) = { _board = b.copy() }

    def count_dev_cards: Int = {
        cards_mutex.synchronize { () =>
            var count = 0
            get_cards.foreach { kv =>
                if (kv._1.isInstanceOf[DevelopmentCard])
                    count += kv._2
            }
            count
        }
    }

    /** Send a message to this player */
    def chat_msg(player: PlayerInfo, msg: String): Unit = {
        this.msgLog = (this.msgLog :+ new Message(msg, player))
        log.debug("MESSAGE FOR:(*{full_name}:*{color})  *{msg}")
    }

    /** Get an immutable map of cards */
    def get_cards(): Map[Card, Int] = {
        cards_mutex.synchronize {
            return this.cards.toMap[Card, Int]
        }
    }

    def get_cards(c: Card): Int = {
        cards_mutex.synchronize {
            var count = 0
            cards.foreach { kv =>
                if (kv._1.getClass().equals(c.getClass)) {
                    count = count + kv._2
                }
            }
            return count
        }
    }

    /** Inform this player that another player has offered a quote.  i'm not sure we need this.*/
    def offer_quote(quote: Quote) = {

    }

    def add_extra_victory_points(points: Int) = extra_victory_points += points
    def get_extra_victory_points(): Int = extra_victory_points

    /**
     * Can this player afford the given pieces?
     * [pieces] an Array of buyable piece classes (Cities, Settlements, etc.)
     */
    def can_afford(pieces: List[Purchaseable]): Boolean = {
        if (pieces == null) throw new Exception("pieces cannot be nil")
        var cost: Map[Card, Int] = Map().withDefaultValue(0)
        pieces.foreach { piece =>
            admin.get_price(piece).foreach { card =>
                cost += card -> (cost(card) + 1)
            }
        }
        cost.foreach { kv =>
            if (this.cards.getOrElse(kv._1, 0) < kv._2) {
                return false
            }
        }
        return true
    }

    /**
     * Tell this player that they received more cards.
     * [cards] an Array of card types
     */
    def add_cards(cards_to_add: List[Card]) {
        cards_mutex.synchronize { () =>
            if (cards_to_add.isEmpty) throw new Exception("cards is empty")
            if (cards_to_add.contains(null)) throw new Exception("cards contains a null element")
            log.debug(full_name + " Adding cards " + cards_to_add)
            cards_to_add.foreach { c =>
                this.cards = cards + (c -> (cards(c) + 1))
            }
        }
    }

    def copy_pieces_left = {
        pieces_mutex.synchronize { () =>
            piecesLeft //Don't need to copy it since it's immutable
        }
    }

    def get_pieces_left(pieceKlass: Class[BoardPiece]) {
        pieces_mutex.synchronize { () =>
            piecesLeft(pieceKlass)
        }
    }

    def addPiecesLeft(pieceKlass: Class[_ <: BoardPiece], amount: Int) {
        pieces_mutex.synchronize { () =>
            val new_amount = (piecesLeft(pieceKlass) + amount)
            if (new_amount < 0)
                throw new RuleException("Player has 0 " + pieceKlass)
            piecesLeft = piecesLeft + (pieceKlass -> new_amount)
        }
    }

    /**
     * Remove cards from this players hand
     * throws a RuleException if there aren't sufficent cards
     * [cards] an Array of card types
     * [reason] 1 = Bought Settlement
     *          2 = Bought City
     *          3 = Bought Road
     *          4 = A 7 was rolled
     *          5 = Someone stole a card from you
     *          6 = From a trade
     *          7 = Bought a Resource card
     *          8 = Other
     */
    def del_cards(cards_to_lose: List[Card], reason: Int) = {
        cards_mutex.synchronize { () =>
            log.debug(this + " Losing cards " + cards_to_lose + " Current Cards:" + cards)
            var sum = Map[Card, Int]().withDefaultValue(0)
            cards_to_lose.foreach { c => sum += c -> (sum(c) + 1) }
            //validate that there are enough cards
            sum.foreach { kv =>
                if (kv._1 == null) throw new RuleException("card type cannot be null. cards:" + cards)
                if (kv._2 > this.get_cards(kv._1)) {
                    throw new RuleException(this + " does not have enough cards to delete.  Trying to take " +
                        kv._1 + " " + kv._2 + ". cards:" + this.cards + "  Found this many " + kv._1 + " cards:" + this.get_cards(kv._1))
                }
            }
            //If we get here, everything's valid
            sum.foreach { kv => cards += kv._1 -> (cards(kv._1) - kv._2) }
        }
    }

    def ==(o: Player): Boolean = (o != null && this.color == o.color)

    /** This method should be extended */
    def take_turn(turn: Turn, is_setup: Boolean) {
        this.current_turn = turn
        listeners.foreach { l =>
            l.got_turn(turn)
            //turn.register_listener(l)
        }

        if (board == null) throw new IllegalStateException("Bad state in take_turn: board is null")

        /** This is only for server-side debugging */
        //current_turn.class != ProxyObject and 
        if (current_turn.player.color != this.color) {
            throw new IllegalStateException("Turn's player " + current_turn.player + " does not match the actual player: " + this)
        }
    }

    /** This should be overidden in the implementations */
    def get_user_quotes(player_info: PlayerInfo, wantList: List[Resource], giveList: List[Resource]): List[Quote]

    /**
     * Tell this player to move the bandit
     * [old_hex] the hex where the bandit currently sits
     * return a new hex
     * This method should be overridden
     */
    def move_bandit(old_hex: Hex): Hex

    /**
     * Ask the player to select some cards from a list.
     * This is used when a player must discard or resource
     * monopoly or year of plenty
     * This method should be overridden
     */
    def select_resource_cards(cards: List[Resource], count: Int, reason: Int): List[Resource]

    /**
     * Ask the player to choose a player among the given list
     * This method should be overridden
     */
    def select_player(players: List[PlayerInfo], reason: Int): PlayerInfo

    override def player_moved_bandit(player_info: PlayerInfo, new_hex: Hex) = {
        var boards_hex: Hex = null //board.getTile(*new_hex.coords)
        //board.move_bandit(new_hex) unless boards_hex.has_bandit
    }

    /** Notify the observer that the game has begun */
    override def game_start {
        if (board == null) throw new IllegalStateException("Game is starting before a board has been set")
        game_finished = false
    }

    /**
     * Inform the observer that the game has finished.
     * [player] the player who won
     * [points] the number of points they won with.
     */
    override def game_end(winner: PlayerInfo, points: Int) {
        game_finished = true
    }

    def get_board {
        board_mutex.synchronize[Board] { () =>
            board
        }
    }

    /**
     * Update this observer's version of the board
     * [board] the new version of the board
     */
    def update_board(b: Board) {
        if (b == null) throw new IllegalArgumentException("board cannot be nil")
        this.board = b
    }

    /**
     * Notify this observer that a road was placed
     * [player] The player that placed the road
     * [x, y, edge] The edge coordinates
     */
    override def placed_road(player_info: PlayerInfo, x: Int, y: Int, edge: Int) {
        board.place_road(new Road(player_info.color), x, y, edge)
    }

    /**
     * Notify this observer that a settlement was placed
     * [player] The player that placed the settlement
     * [x, y, node] The node coordinates
     */
    override def placed_settlement(player_info: PlayerInfo, x: Int, y: Int, node: Int) {
        board.place_city(new Settlement(player_info.color), x, y, node)
    }

    /**
     * Notify this observer that a city was placed
     * [player] The player that placed the city
     * [x, y, node] The node coordinates
     */
    override def placed_city(player_info: PlayerInfo, x: Int, y: Int, node: Int) {
        board.place_city(new City(player_info.color), x, y, node)
    }

    /** How many resource cards does this player have? */
    def count_resources = resource_cards.size

    /**
     * Gets all the resource cards this user has
     * returns a list of ResourceTypes
     */
    def resource_cards(): List[Resource] = {
        //this.cards.map{|t, c| [t] * c if t.superclass == Resource }.flatten.compact
        var result: List[Resource] = Nil
        this.cards.keys.foreach { key =>
            key match {
                case card: Resource =>
                    (1 to this.cards(key)).foreach { i =>
                        result = result :+ card
                    }
                case _ => Nil
            }
        }
        result
    }

    def register_listener(listener: PlayerListener) {
        this.listeners :+ listener
    }

    def full_name(): String = {
        if (this.last_name != null && this.last_name.length > 0) {
            this.first_name + " " + this.last_name
        } else {
            this.first_name
        }
    }

    override def toString(): String = "<" + this.getClass().getSimpleName() + " name=\"" + info.full_name + "\" color=\"" + color + "\"/>"

}

/**
 * This encapsulates all the readable info about a player
 * This object is essentially a struct that lets other players refer to each other
 * This way, other players will only know so much information about each other
 */
class PlayerInfo(player: Player) extends Serializable{
    val first_name: String = player.first_name
    val last_name: String = player.last_name
    val color = player.color
    val pic = player.pic
    val pic_square = player.pic_square
    val is_bot = player.isInstanceOf[Bot]

    override def equals(o: Any): Boolean = {
        o match {
            case i: PlayerInfo => i != null && i.full_name == this.full_name && i.color == this.color
            case _ => false
        }
    }

    def full_name(): String = {
        if (this.last_name != null && this.last_name.length > 0) {
            this.first_name + " " + this.last_name
        } else {
            this.first_name
        }
    }

    override def toString(): String = "<PlayerInfo name=\"" + full_name + "\"  color=\"" + color + "\" />"

}

