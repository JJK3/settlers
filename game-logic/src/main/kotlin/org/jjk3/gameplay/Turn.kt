package org.jjk3.gameplay

import org.apache.log4j.Logger
import org.jjk3.board.Board
import org.jjk3.board.RuleException
import org.jjk3.gameplay.actions.RequestQuotesAction
import org.jjk3.gameplay.actions.TurnAction
import org.jjk3.player.Player

open class Turn(val admin: Admin, val player: Player, val board: Board) : HasTurnState() {

    companion object {
        var log: Logger = Logger.getLogger(Turn::class.java)
    }

    enum class ReasonToTakeCards {
        PurchasedSettlement,
        PurchasedCity,
        PurchasedRoad,
        PurchasedDevelopmentCard,
        Rolled7,
        MovedBandit,
        ResourceMonopoly,
        Trade,
        Other
    }

    var playedActions = emptyList<TurnAction<out Any?>>()
    open fun <T> play(action: TurnAction<T>): T {
        val result = action.run(admin, this, board, player)
        playedActions += action
        return result
    }

    fun recievedQuotes(): List<Quote> = playedActions.filterIsInstance<RequestQuotesAction>().flatMap { it.result ?: emptySet() }
    /** The list of action cards currently in play. i.e. SoldierCards etc. */
    fun activeCards() = player.playedDevCards.filter { ! it.isDone }

    /** The user has broken a rule.  They going to be kicked out */
    fun breakRule(msg: String) {
        val error = RuleException(msg)
        state = TurnState.DoneWithError
        throw error
    }

    open fun done() {
        if (! admin.isGameDone()) {
            assertRule(hasRolled() && ! state.isTerminal,
                    "Turn ended before dice were rolled.  Current state: $state")
            assertRule(activeCards().isEmpty(), "All card actions must be finished.")
            assertRule(player.freeRoads() == 0, "You cannot end a turn while there are purchased pieces to place")
            assertRule(! activeCards().any { it.single_turn_card }, "There are still active cards: ${activeCards()}")
        }
        forceDone()
        log.debug("Turn done")
    }

    fun forceDone() {
        state = TurnState.Done
    }

    fun assertRule(condition: Boolean, errorMsg: String) {
        if (! condition) {
            breakRule(errorMsg)
        }
    }

}