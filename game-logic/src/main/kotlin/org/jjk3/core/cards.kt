package org.jjk3.core

import org.apache.log4j.Logger
import org.jjk3.player.Admin
import org.jjk3.player.Turn
import java.util.*

interface Card
/** Base class of a card that does something */
abstract class ActionCard : Card {
    val log: Logger = Logger.getLogger(javaClass)
    /* Use this card on a turn */
    abstract fun use(turn: Turn)

    /** Are the card's actions finished? */
    var isDone = true

    /**
     * Does this card HAVE to be finished at the end of a turn?  Or can it
     * be played across multiple turns?
     */
    open val single_turn_card = true
}

data class ResourceCard(val resource: Resource) : Card
abstract class DevelopmentCard : ActionCard(), Purchaseable {
    override val price = listOf(Resource.Ore, Resource.Sheep, Resource.Wheat)
}

/**
 * Allows a player to move the bandit. This can be played before the dice are rolled.
 */
class SoldierCard : DevelopmentCard() {
    override fun use(turn: Turn) {
        val old_bandit_hex: Hex = turn.admin.board.tiles.values.find(Hex::has_bandit) ?: throw IllegalArgumentException(
                "Could not find hex with bandit")
        val banitHex = turn.player.moveBandit(old_bandit_hex).coords
        val actual__hex: Hex = turn.admin.board.getHex(banitHex) ?: throw IllegalArgumentException(
                "Could not find hex $banitHex")
        turn.moveBandit(actual__hex)
    }
}

/** Allows a player to build 2 roads in his turn */
class RoadBuildingCard : DevelopmentCard() {
    override fun use(turn: Turn) {
        turn.player.giveFreeRoads(2)
        log.debug("Giving 2 roads to ${turn.player}: ${turn.player.purchasedRoads}")
    }
}

/** Allows a user to steal a specific resource from all other players */
class ResourceMonopolyCard : DevelopmentCard() {
    override fun use(turn: Turn) {
        val res = turn.player.selectResourceCards(Resource.values().toList(), 1,
                Admin.SELECT_CARDS_RES_MONOPOLY).first()

        turn.admin.otherPlayers(turn.player.ref()).forEach { p ->
            val cards: List<ResourceCard> = (1..p.countResources(res)).map { ResourceCard(res) }
            if (cards.isEmpty()) {
                log.info("${turn.player} is trying to take $res cards from $p, but $p has none.")
            } else {
                log.info("${turn.player} is taking $cards from $p")
                p.takeCards(cards, 7)
                turn.player.addCards(cards)
            }
        }
    }
}

/** Lets a user select 2 resources and plus them to his hand */
class YearOfPlentyCard : DevelopmentCard() {
    override fun use(turn: Turn) {
        val res = turn.player.selectResourceCards(Resource.values().toList(), 2, Admin.SELECT_CARDS_YEAR_OF_PLENTY)
        if (res.size != 2) {
            throw RuleException("selectResourceCards expected 2 cards but was " + res)
        }
        turn.player.addCards(res.map(::ResourceCard))
    }
}

/** Lets a user select 2 resources and plus them to his hand */
class VictoryPointCard : DevelopmentCard() {
    override fun use(turn: Turn) {

    }
}

open class Bag<A>(protected val items: List<A> = emptyList()) {
    private val rand = Random(System.currentTimeMillis())
    fun size() = items.size
    fun isEmpty() = size() == 0
    operator fun plus(item: A): Bag<A> = Bag(items + item)
    fun removeRandom(): Pair<Bag<A>, A> {
        if (!items.isEmpty()) {
            val i = rand.nextInt(items.size)
            return pick_and_remove(i)
        }
        throw IllegalStateException("Cannot removeRandom from an empty bag")
    }

    private fun next() = pick_and_remove(0)
    private fun pick_and_remove(i: Int): Pair<Bag<A>, A> {
        val grabbedItem = items[i]
        return Pair(Bag(items.filterIndexed { index, _ -> index != i }), grabbedItem)
    }
}

/** A randomized collection of development cards. */
object DevelopmentCardBag {
    fun create(): Bag<DevelopmentCard> {
        var bag = Bag<DevelopmentCard>()
        (1..14).forEach { bag += SoldierCard() }
        (1..2).forEach { bag += RoadBuildingCard() }
        (1..2).forEach { bag += ResourceMonopolyCard() }
        (1..2).forEach { bag += YearOfPlentyCard() }
        (1..5).forEach { bag += VictoryPointCard() }
        return bag
    }
}
