package org.jjk3.player

import org.apache.log4j.Logger

abstract class UsesGameState {
    companion object {
        private val log: Logger = Logger.getLogger(javaClass)
    }

    enum class GameState(val id: Int, val desc: String, val is_terminal_state: Boolean) {
        Waiting(0, "Waiting for players to join", false),
        Running(1, "Game is running", false),
        Finished(2, "Game is over", true),
        Stalemate(3, "Game ended in a stalemate", true);

        override fun toString() = "<state id=\"$id\" name=\"$name\" />"

    }

    private var game_state: GameState = GameState.Waiting
    private val state_mutex = Object()

    open var state: GameState
        get() {
            return synchronized(state_mutex) {
                game_state
            }
        }
        set(value) {
            synchronized(state_mutex) {
                log.info("Game State is changing to " + value)
                game_state = value
            }
        }

    fun isGameDone(): Boolean = state.is_terminal_state
    fun isGameWaiting(): Boolean = state == GameState.Waiting
    fun isGameInProgress(): Boolean = state == GameState.Running
    /** Assert a state */
    fun assertState(expectedState: GameState, msg: String = "") {
        if (state != expectedState)
            throw  IllegalStateException("Assertion Error: Expected turn state:$expectedState Found:$state. $msg")
    }

    fun assert_not_state(notExpectedState: GameState, msg: String = "") {
        if (state == notExpectedState) {
            throw  IllegalStateException(
                    "Assertion Error: Turn state was expected to not be :$notExpectedState Found:$state. $msg")
        }
    }
}