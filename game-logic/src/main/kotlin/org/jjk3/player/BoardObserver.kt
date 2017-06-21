package org.jjk3.player

import org.jjk3.board.*

/** A Game Observer that listens for board updates. */
class BoardObserver : GameObserver {

    var board: Board? = null
    override fun updateBoard(board: Board) {
        this.board = board.copy()
    }

    override fun playerMovedBandit(player: PlayerReference, hex: HexCoordinate) {
        board?.moveBandit(hex)
    }

    override fun placedRoad(player: PlayerReference, edgeCoordinate: EdgeCoordinate) {
        board?.placeRoad(Road(player.color), edgeCoordinate)
    }

    override fun placedSettlement(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        board?.placeCity(Settlement(player.color), nodeCoordinate)
    }

    override fun placedCity(player: PlayerReference, nodeCoordinate: NodeCoordinate) {
        board?.placeCity(City(player.color), nodeCoordinate)
    }
}