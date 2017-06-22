package org.jjk3.gameplay.actions

import org.jjk3.board.DevelopmentCard
import org.jjk3.gameplay.TurnState


class BuyDevelopmentCardAction : TurnAction<DevelopmentCard>() {
    override fun run(): DevelopmentCard {
        turn.assertState(TurnState.RolledDice)
        val (newDevCards, card) = board.developmentCards.removeRandom()
        board.developmentCards = newDevCards
        payFor(card)
        player.giveCards(listOf(card))
        return card
    }
}