package org.jjk3.board

import org.apache.log4j.Logger
import java.util.*

interface Card
/** Base class of a card that does something */
abstract class ActionCard : Card {
    val log: Logger = Logger.getLogger(javaClass)

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
class SoldierCard : DevelopmentCard()

/** Allows a player to build 2 roads in his turn */
class RoadBuildingCard : DevelopmentCard()

/** Allows a user to steal a specific resource from all other players */
class ResourceMonopolyCard : DevelopmentCard()

/** Lets a user select 2 resources and plus them to his hand */
class YearOfPlentyCard : DevelopmentCard()

/** Lets a user select 2 resources and plus them to his hand */
class VictoryPointCard : DevelopmentCard()

open class Bag<A>(protected val items: List<A> = emptyList()) {
    private val rand = Random(System.currentTimeMillis())
    fun size() = items.size
    fun isEmpty() = size() == 0
    operator fun plus(item: A): Bag<A> = Bag(items + item)
    fun removeRandom(): Pair<Bag<A>, A> {
        if (! items.isEmpty()) {
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
