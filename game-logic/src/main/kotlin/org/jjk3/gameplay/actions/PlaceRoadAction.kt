package org.jjk3.gameplay.actions

import org.jjk3.board.Board
import org.jjk3.board.EdgeCoordinate
import org.jjk3.board.Road
import org.jjk3.gameplay.Turn
import org.jjk3.gameplay.TurnState
import org.jjk3.player.Player

class PlaceRoadAction(val edgeCoordinate: EdgeCoordinate) : TurnAction<Unit>() {

    override fun run() {
        assertCanPlaceRoad(turn, board, player, edgeCoordinate)
        // If a player uses a roadBuilding card, then his purchasedRoads > 0
        // they shouldn't pay for the road in this case.
        val road: Road =
                if (player.freeRoads() > 0) {
                    player.removeFreeRoads(1)
                    board.getPiecesForSale(player.color !!).takeRoad()
                } else {
                    purchaseRoad()
                }
        board.placeRoad(road, edgeCoordinate)
        observers { it.placedRoad(player.ref(), edgeCoordinate) }
        admin.checkForWinner()
    }

    private fun purchaseRoad() = board.getPiecesForSale(player.color !!).takeRoad().also { payFor(it) }
    private fun assertCanPlaceRoad(turn: Turn, board: Board, player: Player, edgeCoordinate: EdgeCoordinate) {
        assertGameIsNotDone()
        turn.assertState(TurnState.RolledDice)
        val edge = board.getEdge(edgeCoordinate)
        if (! board.getValidRoadSpots(player.color !!).contains(edge)) {
            turn.breakRule("Invalid Road Placement $edgeCoordinate")
        }
    }

}