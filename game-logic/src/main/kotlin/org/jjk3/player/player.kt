package org.jjk3.player

import org.apache.log4j.Logger
import org.jjk3.core.*
import java.io.Serializable

interface PlayerListener {
    fun got_turn(turn: Any)
}

abstract class Player(
        val firstName: String,
        val lastName: String,
        val cities: Int = 4,
        val settlements: Int = 5,
        val roads: Int = 15) : GameObserver {

    companion object {
        val log: Logger = Logger.getLogger(this::class.java)
    }

    var _color: String = ""
    var cardsMutex = Object()
    var piecesMutex = Object()
    var boardMutex = Object()

    var purchasedRoads = 0
    var preferredColor: String? = null
    var playedDevCards = emptyList<DevelopmentCard>()
    private var gameFinished: Boolean = false
    protected var currentTurn: Turn? = null
    var cards = emptyList<Card>()
        protected set(value) {
            field = value
        }

    open fun giveFreeRoads(num_roads: Int): Unit {
        purchasedRoads += num_roads
    }

    open fun removeFreeRoads(num_roads: Int): Unit {
        purchasedRoads -= num_roads
    }

    fun freeRoads(): Int = purchasedRoads
    /** Notifies the player that they have officially played a development card */
    open fun playedDevCard(card: DevelopmentCard): Unit {
        playedDevCards += card
    }

    fun ref() = PlayerReference(this)
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
        return getCards(DevelopmentCard::class.java)
    }

    fun getCards(card: Class<out Card>): Int {
        return synchronized(cardsMutex) {
            cards.count { card.isInstance(it) }
        }
    }

    fun countResources(resource: Resource): Int = cards.count { it is ResourceCard && it.resource == resource }
    /** Inform this player that another player has offered a quote.  i'm not sure we need this.*/
    open fun offerQuote(quote: Quote) {

    }

    fun getExtraVictoryPoints(): Int = playedDevCards.filterIsInstance(VictoryPointCard::class.java).count()
    /**
     * Can this player afford the given pieces?
     * [pieces] an Array of buyable piece classes (Cities, Settlements, etc.)
     */
    fun canAfford(pieces: List<Purchaseable>): Boolean {
        val totalCost = pieces.flatMap { it.price }.map(::ResourceCard)
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
    open fun giveCards(cardsToAdd: List<Card>) {
        synchronized(cardsMutex) { ->
            log.debug("${fullName()} Adding cards $cardsToAdd")
            cards += cardsToAdd
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
    open fun takeCards(cardsToLose: List<Card>, reason: Turn.ReasonToTakeCards) {
        synchronized(cardsMutex) { ->
            cards = removeCards(cards, cardsToLose)
        }
    }

    private fun removeCards(cards: List<Card>, cardsToRemove: List<Card>): List<Card> {
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
    open fun takeTurn(turn: Turn) {
        this.currentTurn = turn
    }

    /** This should be overidden in the implementations */
    abstract fun getUserQuotes(player_reference: PlayerReference, wantList: List<Resource>,
                               giveList: List<Resource>): List<Quote>

    /**
     * Tell this player to move the bandit
     * [old_hex] the hex where the bandit currently sits
     * return a  hex
     * This method should be overridden
     */
    abstract fun moveBandit(old_hex: Hex): Hex

    /**
     * Ask the player to select some cards from a list.
     * This is used when a player must discard or resource
     * monopoly or year of plenty
     * This method should be overridden
     */
    abstract fun selectResourceCards(cards: List<Resource>, count: Int, reason: Int): List<Resource>

    /**
     * Ask the player to choose a player among the given list
     * This method should be overridden
     */
    abstract fun selectPlayer(players: List<PlayerReference>, reason: Int): PlayerReference

    override fun playerMovedBandit(player: PlayerReference, hex: Hex) {
        board?.moveBandit(hex)
    }

    /** Notify the observer that the game has begun */
    override fun gameStart(maxScore: Int) {
        if (board == null) {
            throw  IllegalStateException("Game is starting before a org.jjk3.board has been set")
        }
        gameFinished = false
    }

    /**
     * Inform the observer that the game has finished.
     * [winner] the player who won
     * [points] the randomNumber of points they won ,.
     */
    override fun gameEnd(winner: PlayerReference, points: Int) {
        gameFinished = true
    }

    fun get_board(): Board? {
        return synchronized(boardMutex) {
            board
        }
    }

    /**
     * Update this observer's version of the org.jjk3.board
     * [board] the  version of the org.jjk3.board
     */
    open fun updateBoard(b: Board) {
        this.board = b
    }

    /**
     * Notify this observer that a road was placed
     * [player] The player that placed the road
     * [x, y, edge] The edge coordinates
     */
    override fun placedRoad(player: PlayerReference, edgeCoordinate: EdgeCoordinate) {
        board?.placeRoad(Road(player.color), edgeCoordinate)
    }

    /**
     * Notify this observer that a settlement was placed
     * [player] The player that placed the settlement
     * [x, y, node] The node coordinates
     */
    override fun placedSettlement(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        board?.placeCity(Settlement(player.color), nodeCoordinate)
    }

    /**
     * Notify this observer that a city was placed
     * [player] The player that placed the city
     * [x, y, node] The node coordinates
     */
    override fun placedCity(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        board?.placeCity(City(player.color), nodeCoordinate)
    }

    /**
     * Gets all the resource cards this user has
     * returns a list of ResourceTypes
     */
    fun resourceCards(): List<ResourceCard> = cards.filterIsInstance(ResourceCard::class.java)

    fun fullName(): String = this.firstName + " " + this.lastName
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
        return "Player(first_name='$firstName', last_name='$lastName', color='$color')"
    }

}

/**
 * This encapsulates all the readable ref about a player
 * This object is essentially a struct that lets other players refer to each other
 * This way, other players will only know so much information about each other
 */
class PlayerReference(player: Player) : Serializable {
    val firstName: String = player.firstName
    val lastName: String = player.lastName
    val color = player.color
    fun fullName(): String = this.firstName + " " + this.lastName
    override fun toString(): String = "<PlayerReference name=\"${fullName()}\"  color=\"$color\" />"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as PlayerReference

        if (firstName != other.firstName) return false
        if (lastName != other.lastName) return false
        if (color != other.color) return false

        return true
    }

    override fun hashCode(): Int {
        var result = firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + color.hashCode()
        return result
    }

}

