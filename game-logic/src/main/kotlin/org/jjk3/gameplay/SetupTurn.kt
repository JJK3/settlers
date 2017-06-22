package org.jjk3.gameplay

import org.jjk3.board.Board
import org.jjk3.gameplay.actions.TurnAction
import org.jjk3.gameplay.actions.setup.SetupTurnAction
import org.jjk3.player.Player

class SetupTurn(admin: Admin, player: Player, board: Board) : Turn(admin, player, board) {

    override fun <T> play(action: TurnAction<T>): T {
        if (action !is SetupTurnAction) {
            throw IllegalArgumentException("SetupTurn can only accept SetupTurnActions")
        }
        return super.play(action)
    }

    override fun done() {
        forceDone()
        log.debug("Turn done")
    }

}