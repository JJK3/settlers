package core
import org.apache.log4j.Logger
import UtilList._

/** Base class of a card that does something */
class ActionCard extends Card {
    val log = Logger.getLogger(classOf[ActionCard])

    /* Use this card on a turn */
    def use(turn: Turn) = {
        if (turn != turn.admin.current_turn) {
            throw new RuleException("Cannot play a card on with a finished turn")
        }
        if (turn == null)
            throw new RuleException("Cannot play a card without a turn")
        if (turn.player == null)
            throw new RuleException("Turn must have a player")
    }

    /** Are the card's actions finished? */
    def is_done = true

    /**
     * Does this card HAVE to be finished at the end of a turn?  Or can it
     * be played across multiple turns?
     */
    def single_turn_card = true
}

class DevelopmentCard extends ActionCard with Purchaseable {
    override def equals(o: Any): Boolean = {
        o match {
            case d: DevelopmentCard => d.getClass.equals(this.getClass)
            case _ => false
        }
    }
}

/**
 * Allows a player to move the bandit.
 * NOTE: this can be played before the dice are rolled.
 */
class SoldierCard extends DevelopmentCard {
    override def use(turn: Turn) = {
        super.use(turn)
        var old_bandit_hex: Hex = turn.admin.board.tiles.values.find { _.has_bandit }.getOrElse(null)
        var new_banit_loc = turn.player.move_bandit(old_bandit_hex)

        // get the official hex to use.  Don't trust the one the player returns.
        var actual_new_hex: Hex = turn.admin.board.getTile(new_banit_loc.coords._1, new_banit_loc.coords._2)
        turn.move_bandit(actual_new_hex)
    }
}

/** Allows a player to build 2 roads in his turn */
class RoadBuildingCard extends DevelopmentCard {
    override def use(turn: Turn) = {
        super.use(turn)
        turn.player.give_free_roads(2)
        log.debug("Giving 2 roads to " + turn.player + ": " + turn.player.purchased_pieces)
    }

    //We don't need to override is_done because NO TURN should be allowed 
    //to finish with unused, purchased pieces already.
}

/** Allows a user to steal a specific resource from all other players */
class ResourceMonopolyCard extends DevelopmentCard {
    override def use(turn: Turn) = {
        super.use(turn)
        val res = turn.player.select_resource_cards(HexType.RESOURCE_TYPES, 1, Admin.SELECT_CARDS_RES_MONOPOLY).first
        if (!HexType.RESOURCE_TYPES.contains(res)) {
            throw new RuleException("Player must select a resource. Found #{res} instead")
        }
        turn.admin.other_players(turn.player.info).foreach { p =>
            var cards: List[Resource] = Nil
            (1 to p.get_cards(res)).foreach { i => cards = cards :+ res }
            if (cards.isEmpty) {
                log.info(turn.player + " is trying to take " + res + " cards from " + p + ", but " + p + " has none.")
            } else {
                log.info(turn.player + " is taking " + cards + " from " + p)
                p.del_cards(cards, 7)
                turn.player.add_cards(cards)
            }
        }
    }
}

/** Lets a user select 2 resources and add them to his hand */
class YearOfPlentyCard extends DevelopmentCard {
    override def use(turn: Turn) = {
        super.use(turn)
        val cards = List(HexType.RESOURCE_TYPES, HexType.RESOURCE_TYPES).flatten
        val res = turn.player.select_resource_cards(cards, 2, Admin.SELECT_CARDS_YEAR_OF_PLENTY)
        if (res == null) throw new RuleException("select_resource_cards returned null")
        if (res.size != 2) throw new RuleException("select_resource_cards expected 2 cards but was " + res)
        turn.player.add_cards(res)
    }
}

/** Lets a user select 2 resources and add them to his hand */
class VictoryPointCard extends DevelopmentCard {
    override def use(turn: Turn) = {
        super.use(turn)
        turn.player.add_extra_victory_points(1)
    }
}

/** A randomized collection of development cards. */
class DevelopmentCardBag {
    var cards: List[DevelopmentCard] = Nil.padTo(14, new SoldierCard()) ++
        Nil.padTo(2, new RoadBuildingCard()) ++
        Nil.padTo(2, new ResourceMonopolyCard()) ++
        Nil.padTo(2, new YearOfPlentyCard()) ++
        Nil.padTo(5, new VictoryPointCard())

    /** This gets called by the admin to give a card to somebody */
    def get_card: DevelopmentCard = {
        if (cards.isEmpty) throw new RuleException("No Development cards left")
        return cards.remove_random()._1
    }
}
