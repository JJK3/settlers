package org.jjk3.gameplay.actions

import org.jjk3.board.Node
import org.jjk3.board.NodeCoordinate
import org.jjk3.gameplay.TurnState

class PlaceSettlementAction(val coord: NodeCoordinate) : TurnAction<Node>() {

    override fun run(): Node {
        assertCanPlaceSettlement(coord)
        val node = board.getNode(coord)
        val sett = purchaseSettlement()
        board.placeCity(sett, coord)
        observers { it.placedSettlement(player.ref(), coord) }
        admin.checkForWinner()
        return node
    }

    private fun purchaseSettlement() = board.getPiecesForSale(player.color !!).takeSettlement().also { payFor(it) }
    private fun assertCanPlaceSettlement(coord: NodeCoordinate) {
        assertGameIsNotDone()
        turn.assertState(TurnState.RolledDice)
        val node = board.getNode(coord)
        if (! board.getValidSettlementSpots(player.color !!).contains(node)) {
            turn.breakRule("Invalid Settlement Placement $coord")
        }
    }

}