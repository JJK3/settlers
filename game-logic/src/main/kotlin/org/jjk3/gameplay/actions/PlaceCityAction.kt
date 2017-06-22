package org.jjk3.gameplay.actions

import org.jjk3.board.Node
import org.jjk3.board.NodeCoordinate
import org.jjk3.board.Settlement
import org.jjk3.gameplay.TurnState

class PlaceCityAction(val coord: NodeCoordinate) : TurnAction<Node>() {
    override fun run(): Node {
        assertCanPlaceCity(coord)
        val node = board.getNode(coord)
        val city = purchaseCity()
        board.getPiecesForSale(player.color !!).putBack(node.city as Settlement)
        board.placeCity(city, coord)
        observers { it.placedCity(player.ref(), coord) }
        admin.checkForWinner()
        return node
    }

    private fun assertCanPlaceCity(coord: NodeCoordinate) {
        assertGameIsNotDone()
        turn.assertState(TurnState.RolledDice)
        val node = board.getNode(coord)
        assertRule(node.hasCity(), "Invalid City Placement.  There is no settlement at $coord")
        assertRule(node.city?.color == player.color, "Invalid City Placement. " +
                "Settlement has wrong color at $coord. expected: ${player.color} was:${node.city?.color}")
        assertRule(node.city is Settlement, "A city must be placed on top of a Settlement, not a ${node.city}")
    }

    private fun purchaseCity() = board.getPiecesForSale(player.color !!).takeCity().also { payFor(it) }
}