package org.jjk3.player

import org.jjk3.board.*
import org.jjk3.gameplay.Quote
import org.jjk3.gameplay.Turn
import java.io.Serializable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

abstract class Player(val boardObserver: BoardObserver = BoardObserver()) : GameObserver by boardObserver {

    private var purchasedRoads = AtomicInteger()

    var playedDevCards = emptyList<DevelopmentCard>()
        private set
    private var gameFinished: Boolean = false
    protected var currentTurn: Turn? = null
    var cards: List<Card> = CopyOnWriteArrayList<Card>()
        protected set
    open var color: String? = null
    open fun giveFreeRoads(num_roads: Int) = purchasedRoads.incrementAndGet()
    open fun removeFreeRoads(num_roads: Int) = purchasedRoads.decrementAndGet()
    fun board(): Board? = boardObserver.board
    fun freeRoads(): Int = purchasedRoads.get()
    /** Notifies the player that they have officially played a development card */
    open fun playedDevCard(card: DevelopmentCard): Unit {
        playedDevCards += card
    }

    fun ref() = PlayerReference(color !!)
    fun countDevelopmentCards(): Int = developmentCards().size
    fun countResources(resource: Resource): Int = resourceCards().filter { it.resource == resource }.size
    fun resourceCards(): List<ResourceCard> = cards.filterIsInstance(ResourceCard::class.java)
    fun developmentCards(): List<DevelopmentCard> = cards.filterIsInstance(DevelopmentCard::class.java)
    fun countCards(card: Class<out Card>): Int = cards.count { card.isInstance(it) }
    fun getExtraVictoryPoints(): Int = playedDevCards.filterIsInstance(VictoryPointCard::class.java).count()
    fun canAfford(pieces: List<Purchaseable>): Boolean {
        val totalCost = pieces.flatMap { it.price }.map(::ResourceCard)
        try {
            removeCards(cards, totalCost)
            return true
        } catch(e: RuleException) {
            return false
        }
    }

    /** Tell this player that they received more cards. */
    open fun giveCards(cardsToAdd: List<Card>) {
        cards += cardsToAdd
    }

    /** Remove cards from this players hand. Throws a RuleException if there aren't sufficient cards */
    open fun takeCards(cardsToLose: List<Card>, reason: Turn.ReasonToTakeCards) {
        cards = removeCards(cards, cardsToLose)
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

    /** This should be overridden in the implementations */
    abstract fun getUserQuotes(playerRef: PlayerReference, wantList: Set<Resource>,
                               giveList: Set<Resource>): Set<Quote>

    /**
     * Tell this player to move the bandit
     * [old_hex] the hex where the bandit currently sits
     * return a  hex
     * This method should be overridden
     */
    abstract fun moveBandit(oldLocation: HexCoordinate): HexCoordinate

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

    /** Notify the observer that the game has begun */
    override fun gameStart(maxScore: Int) {
        if (board() == null) {
            throw IllegalStateException("Game is starting before a board has been set")
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

    override fun toString(): String = "Player(color='$color')"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Player
        if (color != other.color) return false
        return true
    }

    override fun hashCode(): Int {
        return color?.hashCode() ?: 0
    }
}

/** A reference to a player */
data class PlayerReference(val color: String) : Serializable

interface HasColorPreference {
    var preferredColor: String?
}

interface HasName {
    val firstName: String
    val lastName: String
    fun fullName(): String
}

abstract class PlayerWithName(
        override val firstName: String,
        override val lastName: String) : Player(), HasName {

    override fun fullName(): String = this.firstName + " " + this.lastName
    override fun toString(): String {
        return "PlayerWithName(name='${fullName()}')"
    }

}

