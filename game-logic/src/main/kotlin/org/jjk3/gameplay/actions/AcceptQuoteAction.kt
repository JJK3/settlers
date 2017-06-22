package org.jjk3.gameplay.actions

import org.jjk3.board.ResourceCard
import org.jjk3.gameplay.Quote
import org.jjk3.gameplay.Turn
import org.jjk3.gameplay.TurnState

class AcceptQuoteAction(val quote: Quote) : TurnAction<Unit>() {
    override fun run() {
        assertValidQuote(quote)
        val bidder = quote.bidder?.let { getPlayer(it.color) }
        val bidderName = bidder?.color ?: "The Bank"
        Turn.log.debug("$player is accepting a trade from $bidderName ${quote.giveNum} ${quote.giveType}" +
                " for ${quote.receiveNum} ${quote.receiveType}")

        val cardsToAdd = (1..quote.giveNum).map { ResourceCard(quote.giveType) }
        val cardsToLose = (1..quote.receiveNum).map { ResourceCard(quote.receiveType) }

        player.giveCards(cardsToAdd)
        player.takeCards(cardsToLose, Turn.ReasonToTakeCards.Trade)
        if (bidder != null) {
            bidder.giveCards(cardsToLose)
            bidder.takeCards(cardsToAdd, Turn.ReasonToTakeCards.Trade)
        }
    }

    private fun assertValidQuote(quote: Quote) {
        turn.assertState(TurnState.RolledDice)
        if (! turn.recievedQuotes().contains(quote)) {
            breakRule("Attempting to accept a quote that hasn't been received:$quote Other quotes: $turn.allQuotes")
        }
        admin.validateQuote(quote)
        // Check to make sure that everybody has enough cards
        if (player.countResources(quote.receiveType) < quote.receiveNum) {
            breakRule("You don't have enough cards for this quote: " + quote)
        }
        val bidder = quote.bidder?.let { getPlayer(it.color) }
        bidder?.let {
            if (it.countResources(quote.giveType) < quote.giveNum) {
                breakRule("Bidder $it doesn't have enough cards for this quote: $quote")
            }
        }
    }
}