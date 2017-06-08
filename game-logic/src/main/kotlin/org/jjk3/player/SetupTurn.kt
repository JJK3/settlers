package org.jjk3.player

import org.jjk3.core.*

class SetupTurn(admin: Admin, player: Player, board: Board) : Turn(admin, player, board) {

    var placedRoad: Edge? = null
    var placedSettlement: Node? = null
    private fun assertCanPlaceRoad(edgeCoordinate: EdgeCoordinate) {
        assertGameIsNotDone()
        assertState(TurnState.Active)
        assertRule(placedRoad == null, "Too many roads placed in setup")
        assertRule(placedSettlement != null, "Must place settlement before road")
        val edge = board.getEdge(edgeCoordinate)
        val validSpots = board.getValidRoadSpots(player.color, placedSettlement)
        assertRule(validSpots.contains(edge), "Road must touch the settlement just placed")
    }

    override fun placeRoad(edgeCoordinate: EdgeCoordinate) {
        assertCanPlaceRoad(edgeCoordinate)
        val edge = board.getEdge(edgeCoordinate)
        val road = board.getPiecesForSale(player.color).takeRoad()
        board.placeRoad(road, edgeCoordinate)
        this.placedRoad = edge
        admin.observers.forEach { it.placedRoad(player.ref(), edgeCoordinate) }
    }

    override fun assertCanPlaceSettlement(coord: NodeCoordinate) {
        assertGameIsNotDone()
        assertState(TurnState.Active)
        val node = board.getNode(coord)
        if (!board.getValidSettlementSpots().contains(node)) {
            breakRule("Invalid Settlement Placement $coord")
        }
        if (placedSettlement != null) {
            breakRule("Too many settlements placed in setup")
        }
    }

    override fun placeSettlement(coord: NodeCoordinate): Node {
        assertCanPlaceSettlement(coord)
        val node = board.getNode(coord)
        val settlement = board.getPiecesForSale(player.color).takeSettlement()
        board.placeCity(settlement, coord)
        collect2CardsOnLastSettlement(node)
        placedSettlement = node
        admin.observers.forEach { it.placedSettlement(player.ref(), coord) }
        return node
    }

    private fun collect2CardsOnLastSettlement(node: Node) {
        val settlementCount = board.allNodes().count { it.city?.color == player.color }
        if (settlementCount == 2) {
            // A Player gets cards for the 2nd settlement he places
            val touchingHexes = node.hexes.keys.filterNot { it.resource == null }
            val resources = touchingHexes.map(Hex::get_card)
            player.addCards(resources.map(::ResourceCard))
        } else if (settlementCount != 1) {
            breakRule("Bad Game state.  Wrong # of settlements placed: " + settlementCount)
        }
    }

    override fun done() {
        assertRule(placedSettlement != null, "You cannot end a setup turn without placing a settlement.")
        assertRule(placedRoad != null, "You cannot end a setup turn without placing a road.")
        forceDone()
        log.debug("Turn done")
    }

    override fun rollDice(): Pair<Int, Int> {
        throw RuleException("Cannot roll dice during setup")
    }

    override fun placeCity(nodeCoordinate: NodeCoordinate): Node {
        throw RuleException("Cannot place city during setup")
    }

    override fun buyDevelopmentCard(): DevelopmentCard {
        throw RuleException("Cannot buy development card during setup")
    }

}