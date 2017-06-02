package player

import core.*
import org.apache.log4j.Logger
import java.io.Serializable

interface PlayerListener {
    fun got_turn(turn: Any)
}

class Message(val message: String, val sender: PlayerReference?) : Serializable
abstract class Player(
        val first_name: String,
        val last_name: String,
        val admin: Admin,
        cities: Int = 4,
        settlements: Int = 5,
        roads: Int = 15) : GameObserver {

    val log = Logger.getLogger(javaClass)
    var pic: String = ""
    var pic_square: String = ""
    var _color: String = ""
    var cards_mutex = Object()
    var pieces_mutex = Object()
    var board_mutex = Object()


    var purchased_pieces = 0
    var piecesLeft = emptyList<BoardPiece>()
    var msgLog = emptyList<Message>()
    var preferred_color: String? = null
    var listeners = emptyList<PlayerListener>() //These listeners are used by the UI
    var extra_victory_points: Int = 0
    var played_dev_cards = emptyList<DevelopmentCard>()
    private var game_finished: Boolean = false
    protected var current_turn: Turn? = null
    protected var cards = emptyList<Card>()

    init {
        piecesLeft += (0..cities).map { City(color) }
        piecesLeft += (0..settlements).map { Settlement(color) }
        piecesLeft += (0..roads).map { Road(color) }
    }

    open fun give_free_roads(num_roads: Int): Unit {
        purchased_pieces += num_roads
    }

    open fun remove_free_roads(num_roads: Int): Unit {
        purchased_pieces -= num_roads
    }

    fun free_roads(): Int = purchased_pieces
    fun get_played_dev_cards(): List<DevelopmentCard> = played_dev_cards
    /** Notifys the player that they have offically played a development card */
    open fun played_dev_card(card: DevelopmentCard): Unit {
        played_dev_cards += card
    }

    fun info() = PlayerReference(this)
    open var color = _color

    private var internalBoard: Board? = null
    open var board: Board?
        get() {
            return internalBoard
        }
        set(value) {
            internalBoard = value?.copy()
        }

    fun count_dev_cards(): Int {
        return get_cards(DevelopmentCard::class.java)
    }

    /** Send a message to this player */
    open fun chat_msg(player: PlayerReference?, msg: String): Unit {
        this.msgLog += Message(msg, player)
        log.debug("MESSAGE FOR:(*{full_name}:*{color})  *{msg}")
    }

    /** Get an immutable map of cards */
    fun get_cards(): List<Card> = cards

    fun get_cards(card: Class<out Card>): Int {
        return synchronized(cards_mutex) {
            cards.count { card.isInstance(it) }
        }
    }

    fun countResources(resource: Resource): Int = cards.count { it is ResourceCard && it.resource == resource }
    fun countCitiesLeft() = piecesLeft.count { it is City }
    fun countSettlementsLeft() = piecesLeft.count { it is Settlement }
    fun countRoadsLeft() = piecesLeft.count { it is Road }
    /** Inform this player that another player has offered a quote.  i'm not sure we need this.*/
    open fun offer_quote(quote: Quote) {

    }

    open fun add_extra_victory_points(points: Int) = extra_victory_points + points
    fun get_extra_victory_points(): Int = extra_victory_points
    /**
     * Can this player afford the given pieces?
     * [pieces] an Array of buyable piece classes (Cities, Settlements, etc.)
     */
    fun can_afford(pieces: List<Purchaseable>): Boolean {
        val totalCost = pieces.flatMap { admin.getPrice(it) }
        try {
            removeCards(cards, totalCost)
            return true
        } catch(e: RuleException) {
            return false
        }
    }

    /**
     * Tell this player that they received more cards.
     * [cards] an Array of card types
     */
    open fun add_cards(cards_to_add: List<Card>) {
        synchronized(cards_mutex) { ->
            log.debug("${full_name()} Adding cards $cards_to_add")
            cards += cards_to_add
        }
    }

    fun copy_pieces_left() {
        synchronized(pieces_mutex) { ->
            piecesLeft //Don't need to copy it since it's immutable
        }
    }

    fun get_pieces_left(pieceKlass: Class<out BoardPiece>): Int {
        return synchronized(pieces_mutex) { ->
            piecesLeft.count { pieceKlass.isInstance(it) }
        }
    }

    open fun addPiecesLeft(pieceKlass: Class<out BoardPiece>, amount: Int) {
        return synchronized(pieces_mutex) { ->
            piecesLeft += (0..amount).map {
                pieceKlass.constructors.first().newInstance(color) as BoardPiece
            }
        }
    }

    fun removePiece(piece: BoardPiece) {
        return synchronized(pieces_mutex) { ->
            if (!piecesLeft.contains(piece)) {
                throw RuleException("Player does not have $piece in $piecesLeft")
            }
            piecesLeft -= piece
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
    open fun del_cards(cards_to_lose: List<Card>, reason: Int) {
        synchronized(cards_mutex) { ->
            cards = removeCards(cards_to_lose, cards)
        }
    }

    private fun removeCards(cards: List<Card>, cardsToRemove: List<*>): List<Card> {
        if (cardsToRemove.isEmpty()) {
            return cards
        }
        val c = cardsToRemove.first()
        val i = cards.indexOf(c)
        if (i < 0) {
            throw RuleException("Cannot remove $cardsToRemove from $cards")
        }
        return removeCards(cards.remove(i), cardsToRemove.drop(1))
    }

    /** This method should be extended */
    open fun take_turn(turn: Turn, is_setup: Boolean) {
        this.current_turn = turn
        listeners.forEach { l ->
            l.got_turn(turn)
            //turn.register_listener(l)
        }

        /** This is only for server-side debugging */
        //currentTurn.class != ProxyObject and
        if (current_turn?.player?.color != this.color) {
            throw IllegalStateException(
                    "Turn's player ${current_turn?.player} does not match the actual player: ${this}")
        }
    }

    /** This should be overidden in the implementations */
    abstract fun get_user_quotes(player_reference: PlayerReference, wantList: List<Resource>,
                                 giveList: List<Resource>): List<Quote>

    /**
     * Tell this player to move the bandit
     * [old_hex] the hex where the bandit currently sits
     * return a  hex
     * This method should be overridden
     */
    abstract fun move_bandit(old_hex: Hex): Hex

    /**
     * Ask the player to select some cards from a list.
     * This is used when a player must discard or resource
     * monopoly or year of plenty
     * This method should be overridden
     */
    abstract fun select_resource_cards(cards: List<Resource>, count: Int, reason: Int): List<Resource>

    /**
     * Ask the player to choose a player among the given list
     * This method should be overridden
     */
    abstract fun select_player(players: List<PlayerReference>, reason: Int): PlayerReference

    override fun player_moved_bandit(player_reference: PlayerReference, hex: Hex) {
        board?.moveBandit(hex)
    }

    /** Notify the observer that the game has begun */
    override fun game_start() {
        if (board == null) {
            throw  IllegalStateException("Game is starting before a board has been set")
        }
        game_finished = false
    }

    /**
     * Inform the observer that the game has finished.
     * [player] the player who won
     * [points] the number of points they won ,.
     */
    override fun game_end(winner: PlayerReference, points: Int) {
        game_finished = true
    }

    fun get_board(): Board? {
        return synchronized(board_mutex) {
            board
        }
    }

    /**
     * Update this observer's version of the board
     * [board] the  version of the board
     */
    open fun update_board(b: Board) {
        this.board = b
    }

    /**
     * Notify this observer that a road was placed
     * [player] The player that placed the road
     * [x, y, edge] The edge coordinates
     */
    override fun placed_road(player_reference: PlayerReference, edgeCoordinate: EdgeCoordinate) {
        board?.placeRoad(Road(player_reference.color), edgeCoordinate)
    }

    /**
     * Notify this observer that a settlement was placed
     * [player] The player that placed the settlement
     * [x, y, node] The node coordinates
     */
    override fun placed_settlement(player_reference: PlayerReference, nodeCoordinate: NodeCoordinate) {
        board?.placeCity(Settlement(player_reference.color), nodeCoordinate)
    }

    /**
     * Notify this observer that a city was placed
     * [player] The player that placed the city
     * [x, y, node] The node coordinates
     */
    override fun placed_city(player_reference: PlayerReference, nodeCoordinate: NodeCoordinate) {
        board?.placeCity(City(player_reference.color), nodeCoordinate)
    }

    /** How many resource cards does this player have? */
    fun count_resources() = resource_cards().size

    /**
     * Gets all the resource cards this user has
     * returns a list of ResourceTypes
     */
    fun resource_cards(): List<ResourceCard> = cards.filterIsInstance(ResourceCard::class.java)

    fun register_listener(listener: PlayerListener) {
        this.listeners += listener
    }

    fun full_name(): String = this.first_name + " " + this.last_name
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Player

        if (color != other.color) return false

        return true
    }

    override fun hashCode(): Int {
        return color.hashCode()
    }

    override fun toString(): String {
        return "Player(first_name='$first_name', last_name='$last_name', color='$color')"
    }

}

/**
 * This encapsulates all the readable info about a player
 * This object is essentially a struct that lets other players refer to each other
 * This way, other players will only know so much information about each other
 */
class PlayerReference(player: Player) : Serializable {
    val first_name: String = player.first_name
    val last_name: String = player.last_name
    val color = player.color
    val pic = player.pic
    val pic_square = player.pic_square
    fun full_name(): String = this.first_name + " " + this.last_name
    override fun toString(): String = "<PlayerReference name=\"${full_name()}\"  color=\"$color\" />"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as PlayerReference

        if (first_name != other.first_name) return false
        if (last_name != other.last_name) return false
        if (color != other.color) return false

        return true
    }

    override fun hashCode(): Int {
        var result = first_name.hashCode()
        result = 31 * result + last_name.hashCode()
        result = 31 * result + color.hashCode()
        return result
    }

}

