package org.jjk3.gameplay.actions

import org.jjk3.board.Card
import org.jjk3.board.ResourceCard
import org.jjk3.board.RuleException
import org.jjk3.gameplay.*
import org.jjk3.player.Player

open class RollDiceAction : TurnAction<DiceRoll>() {
    override fun run(): DiceRoll {
        turn.assertState(TurnState.Active)
        val roll = getDiceRoll()
        observers { it.playerRolled(player.ref(), roll) }
        if (roll.sum() == 7) {
            makePlayersDiscardIfNecessary()
            Turn.log.info("Rolled a 7, move the bandit.")
            makePlayerMoveBandit(player)
        } else {
            giveOutCards(roll.sum())
        }
        turn.state = TurnState.RolledDice
        return roll
    }

    open fun getDiceRoll() = NormalDiceRoll()
    private fun giveOutCards(sum: Int) {
        admin.players.forEach { player ->
            val cards: List<Card> = board.getCards(sum, player.color !!)
            if (cards.isNotEmpty()) {
                player.giveCards(cards)
            }
        }
    }

    protected fun makePlayerMoveBandit(player: Player) {
        board.findBandit()?.let { hexWithBandit ->
            val h = player.moveBandit(hexWithBandit.coords)
            board.getHex(h).let { moveBandit(it.coords) }
        }
    }

    protected fun makePlayersDiscardIfNecessary() {
        // Each player must first get rid of half their cards if they more than 7
        admin.players.forEach { p ->
            val resourceCards = p.resourceCards()
            if (resourceCards.size > 7) {
                val howManyCardsToLose = resourceCards.size / 2
                val chosenCards = p.selectResourceCards(resourceCards.map { it.resource },
                        howManyCardsToLose, Admin.SELECT_CARDS_ROLLED_7)
                if (chosenCards.size != howManyCardsToLose) {
                    throw RuleException("You did not select the right number of cards. " +
                            "expected:$howManyCardsToLose found:${chosenCards.size}")
                }
                p.takeCards(chosenCards.map(::ResourceCard), Turn.ReasonToTakeCards.Rolled7)
            }
        }
    }

}