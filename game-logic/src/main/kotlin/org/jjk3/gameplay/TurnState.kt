package org.jjk3.gameplay

import org.jjk3.gameplay.TurnState.DoneWithError
import org.jjk3.gameplay.TurnState.RolledDice

enum class TurnState(val desc: String, val isTerminal: Boolean) {

    Created("New Turn", false),
    Active("The turn was given to the user, and is considered active", false),
    RolledDice("The dice were rolled", false),
    Done("Turn is done", true),
    DoneWithError("Turn ended with an error", true);

    override fun toString(): String {
        return "$name(desc='$desc', is_terminal_state=$isTerminal)"
    }
}

interface TurnStateListener {
    fun stateChanged(_state: TurnState)
}

/** A module that can have a turn state */
open class HasTurnState {
    var state: TurnState = TurnState.Created
        set(value) {
            field = value
            stateListeners.forEach { it.stateChanged(value) }
        }

    var stateListeners = emptyList<TurnStateListener>()
    fun isDone() = state.isTerminal
    fun assertState(state: TurnState) {
        if (this.state != state) {
            try {
                throw IllegalStateException("Expected turn state to be $state, but was ${this.state}")
            } finally {
                this.state = DoneWithError
            }
        }
    }

    fun hasRolled(): Boolean {
        return state == RolledDice
    }

    fun registerListener(listener: TurnStateListener) {
        stateListeners += listener
    }
}

