package core

import org.apache.log4j.Logger
import player.Admin
import player.Turn
import java.util.*

interface Card
/** Base class of a card that does something */
abstract class ActionCard : Card {
    val log: Logger = Logger.getLogger(javaClass)
    /* Use this card on a turn */
    abstract fun use(turn: Turn)

    /** Are the card's actions finished? */
    var is_done = true

    /**
     * Does this card HAVE to be finished at the end of a turn?  Or can it
     * be played across multiple turns?
     */
    open val single_turn_card = true
}

data class ResourceCard(val resource: Resource) : Card
abstract class DevelopmentCard : ActionCard(), Purchaseable
/**
 * Allows a player to move the bandit.
 * NOTE: this can be played before the dice are rolled.
 */
class SoldierCard : DevelopmentCard() {
    override fun use(turn: Turn) {
        val old_bandit_hex: Hex = turn.admin.board.tiles.values.find(Hex::has_bandit) ?: throw IllegalArgumentException(
                "Could not find hex with bandit")
        val banitHex = turn.player.move_bandit(old_bandit_hex).coords
        val actual__hex: Hex = turn.admin.board.getTile(banitHex) ?: throw IllegalArgumentException(
                "Could not find hex $banitHex")
        turn.move_bandit(actual__hex)
    }
}

/** Allows a player to build 2 roads in his turn */
class RoadBuildingCard : DevelopmentCard() {
    override fun use(turn: Turn) {
        turn.player.give_free_roads(2)
        log.debug("Giving 2 roads to ${turn.player}: ${turn.player.purchased_pieces}")
    }
}

/** Allows a user to steal a specific resource from all other players */
class ResourceMonopolyCard : DevelopmentCard() {
    override fun use(turn: Turn) {
        val res = turn.player.select_resource_cards(Resource.values().toList(), 1,
                Admin.SELECT_CARDS_RES_MONOPOLY).first()

        turn.admin.other_players(turn.player.info()).forEach { p ->
            val cards: List<ResourceCard> = (1..p.countResources(res)).map { ResourceCard(res) }
            if (cards.isEmpty()) {
                log.info("${turn.player} is trying to take $res cards from $p, but $p has none.")
            } else {
                log.info("${turn.player} is taking $cards from $p")
                p.del_cards(cards, 7)
                turn.player.add_cards(cards)
            }
        }
    }
}

/** Lets a user select 2 resources and add them to his hand */
class YearOfPlentyCard : DevelopmentCard() {
    override fun use(turn: Turn) {
        val res = turn.player.select_resource_cards(Resource.values().toList(), 2, Admin.SELECT_CARDS_YEAR_OF_PLENTY)
        if (res.size != 2) {
            throw RuleException("select_resource_cards expected 2 cards but was " + res)
        }
        turn.player.add_cards(res.map(::ResourceCard))
    }
}

/** Lets a user select 2 resources and add them to his hand */
class VictoryPointCard : DevelopmentCard() {
    override fun use(turn: Turn) {
        turn.player.add_extra_victory_points(1)
    }
}


abstract class RandomBag<A> {
    private var items: List<A> = emptyList()
    private val rand = Random(System.currentTimeMillis())
    fun add(item: A) {
        items += item
    }

    fun grab(): A {
        if (items.isEmpty()) throw IllegalStateException("Cannot grab from an empty bag")
        val i = rand.nextInt(items.size)
        return pick_and_remove(i)
    }

    fun next() = pick_and_remove(0)
    fun pick_and_remove(i: Int): A {
        val grabbedItem = items[i]
        items = items.filterIndexed { index, _ -> index != i }
        return grabbedItem
    }
}

/** A randomized collection of development cards. */
class DevelopmentCardBag : RandomBag<DevelopmentCard>() {
    init {
        (1..14).forEach { add(SoldierCard()) }
        (1..2).forEach { add(RoadBuildingCard()) }
        (1..2).forEach { add(ResourceMonopolyCard()) }
        (1..2).forEach { add(YearOfPlentyCard()) }
        (1..5).forEach { add(VictoryPointCard()) }
    }
}
