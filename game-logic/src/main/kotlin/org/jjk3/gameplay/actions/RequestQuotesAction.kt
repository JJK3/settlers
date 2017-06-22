package org.jjk3.gameplay.actions

import org.jjk3.board.Resource
import org.jjk3.gameplay.Quote
import org.jjk3.gameplay.TurnState

/**
 *  Gets a list of quotes from the bank and other users
 * Optionally takes a block that iterates through each quote as they come
 * (List(CardType), List(CardType)) -> List(Quote)
 * This is from the current player's point of view.  He wants the want list and will give the giveList
 */
class RequestQuotesAction(val wantList: Set<Resource>, val giveList: Set<Resource>) : TurnAction<Set<Quote>>() {
    override fun run(): Set<Quote> {
        validateQuoteLists(giveList)
        turn.assertState(TurnState.RolledDice)
        val quotes = admin.getQuotes(player, wantList, giveList)
        return quotes
    }

    /** Make sure that the player has enough cards to make the offer */
    fun validateQuoteLists(giveList: Set<Resource>) {
        for (giveType in giveList.distinct()) {
            if (player.countResources(giveType) == 0) {
                breakRule("Offering $giveType but only has ${player.cards}")
            }
        }
    }

}