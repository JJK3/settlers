package player

import core.*
import org.apache.log4j.Logger
import java.io.Serializable

interface PlayerListener {
    fun got_turn(turn: Any)
}

abstract class Player(
        val first_name: String,
        val last_name: String,
        val cities: Int = 4,
        val settlements: Int = 5,
        val roads: Int = 15) : GameObserver {

    companion object {
        val log = Logger.getLogger(this::class.java)
    }

    var _color: String = ""
    var cards_mutex = Object()
    var pieces_mutex = Object()
    var board_mutex = Object()

    var purchasedRoads = 0
    var preferred_color: String? = null
    var played_dev_cards = emptyList<DevelopmentCard>()
    private var game_finished: Boolean = false
    protected var current_turn: Turn? = null
    protected var cards = emptyList<Card>()
    open fun give_free_roads(num_roads: Int): Unit {
        purchasedRoads += num_roads
    }

    open fun remove_free_roads(num_roads: Int): Unit {
        purchasedRoads -= num_roads
    }

    fun free_roads(): Int = purchasedRoads
    fun get_played_dev_cards(): List<DevelopmentCard> = played_dev_cards
    /** Notifys the player that they have offically played a development card */
    open fun played_dev_card(card: DevelopmentCard): Unit {
        played_dev_cards += card
    }

    fun info() = PlayerReference(this)
    open var color: String
        get() {
            return _color
        }
        set(value) {
            _color = value
        }

    open var board: Board? = null
        set(value) {
            field = value?.copy()
        }

    fun count_dev_cards(): Int {
        return get_cards(DevelopmentCard::class.java)
    }

    /** Get an immutable map of cards */
    fun get_cards(): List<Card> = cards

    fun get_cards(card: Class<out Card>): Int {
        return synchronized(cards_mutex) {
            cards.count { card.isInstance(it) }
        }
    }

    fun countResources(resource: Resource): Int = cards.count { it is ResourceCard && it.resource == resource }

    /** Inform this player that another player has offered a quote.  i'm not sure we need this.*/
    open fun offer_quote(quote: Quote) {

    }

    fun get_extra_victory_points(): Int = played_dev_cards.filterIsInstance(VictoryPointCard::class.java).count()
    /**
     * Can this player afford the given pieces?
     * [pieces] an Array of buyable piece classes (Cities, Settlements, etc.)
     */
    fun can_afford(pieces: List<Purchaseable>): Boolean {
        val totalCost = pieces.flatMap { it.price }
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
            cards = removeCards(cards, cards_to_lose)
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
     * [points] the randomNumber of points they won ,.
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
    override fun placed_road(player: PlayerReference, edgeCoordinate: EdgeCoordinate) {
        board?.placeRoad(Road(player.color), edgeCoordinate)
    }

    /**
     * Notify this observer that a settlement was placed
     * [player] The player that placed the settlement
     * [x, y, node] The node coordinates
     */
    override fun placed_settlement(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        board?.placeCity(Settlement(player.color), nodeCoordinate)
    }

    /**
     * Notify this observer that a city was placed
     * [player] The player that placed the city
     * [x, y, node] The node coordinates
     */
    override fun placed_city(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        board?.placeCity(City(player.color), nodeCoordinate)
    }

    /** How many resource cards does this player have? */
    fun count_resources() = resource_cards().size

    /**
     * Gets all the resource cards this user has
     * returns a list of ResourceTypes
     */
    fun resource_cards(): List<ResourceCard> = cards.filterIsInstance(ResourceCard::class.java)

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

