package org.jjk3.gameplay.actions.setup

import org.jjk3.board.*
import org.jjk3.gameplay.TurnState

class PlaceSettlementAndRoad(val settlementLocation: NodeCoordinate,
                             val roadLocation: EdgeCoordinate) : SetupTurnAction<Unit>() {
    override fun run() {
        placeSettlement(settlementLocation)
        placeRoad(roadLocation)
    }

    private fun assertCanPlaceRoad(edgeCoordinate: EdgeCoordinate) {
        assertGameIsNotDone()
        turn.assertState(TurnState.Active)
        val edge = board.getEdge(edgeCoordinate)
        val validSpots = board.getValidRoadSpots(player.color !!, settlementLocation)
        assertRule(validSpots.contains(edge), "Road must touch the settlement just placed")
    }

    private fun placeRoad(edgeCoordinate: EdgeCoordinate) {
        assertCanPlaceRoad(edgeCoordinate)
        val road = board.getPiecesForSale(player.color !!).takeRoad()
        board.placeRoad(road, edgeCoordinate)
        admin.observers.forEach { it.placedRoad(player.ref(), edgeCoordinate) }
    }

    private fun assertCanPlaceSettlement(coord: NodeCoordinate) {
        assertGameIsNotDone()
        turn.assertState(TurnState.Active)
        val node = board.getNode(coord)
        if (! board.getValidSettlementSpots().contains(node)) {
            breakRule("Invalid Settlement Placement $coord")
        }
    }

    private fun placeSettlement(coord: NodeCoordinate): Node {
        assertCanPlaceSettlement(coord)
        val node = board.getNode(coord)
        val settlement = board.getPiecesForSale(player.color !!).takeSettlement()
        board.placeCity(settlement, coord)
        collect2CardsOnLastSettlement(node)
        admin.observers.forEach { it.placedSettlement(player.ref(), coord) }
        return node
    }

    private fun collect2CardsOnLastSettlement(node: Node) {
        val settlementCount = board.allNodes().count { it.city?.color == player.color }
        if (settlementCount == 2) {
            // A Player gets cards for the 2nd settlement he places
            val touchingHexes = node.hexes.keys.filterNot { it.resource == null }
            val resources = touchingHexes.map(Hex::getCard)
            player.giveCards(resources.map(::ResourceCard))
        } else if (settlementCount != 1) {
            breakRule("Bad Game state.  Wrong # of settlements placed: " + settlementCount)
        }
    }
}