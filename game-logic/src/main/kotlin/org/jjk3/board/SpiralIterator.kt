package org.jjk3.board

/**
 * Iterates over hexes in a clockwise spiral
 */
class SpiralIterator(val board: Board) {

    private var hexes: List<Hex> = emptyList()
    var startingTile: Hex = outerHexes().pickRandom()
    fun outerHexes(): List<Hex> = nonDesertTiles().filter(Hex::isOnOutside)
    fun nonDesertTiles() = board.tiles.values.filterNot { it.resource == null }
    fun startingEdge() = startingTile.edges.filter(Edge::isOutsideEdge).firstOrNull() ?: throw IllegalArgumentException(
            "This hex is not on the outside")

    fun getHexes(): List<Hex> {
        if (!hexes.isEmpty()) {
            return hexes
        }
        hexes += startingTile
        var next = getNextNewTile(startingTile, startingEdge())
        while (next != null) {
            hexes += next.first
            next = getNextNewTile(next.first, next.second)
        }
        return hexes
    }

    internal fun getNextNewTile(hex: Hex, edge: Edge): Pair<Hex, Edge>? {
        val sizeLimit = EdgeNumber.ALL.size
        var count = 0
        var next = getNext(hex, edge)
        while (hexes.contains(next.first)) {
            next = getNext(hex, next.second)
            if (count++ > sizeLimit) {
                return null
            }
        }
        return next
    }

    internal fun getNext(hex: Hex, edge: Edge): Pair<Hex, Edge> {
        val connectingEdge = getNextClockwiseEdge(hex, edge)
        val nexHex = connectingEdge.hexes.keys.filter { it != hex }.first()
        return Pair(nexHex, connectingEdge)
    }

    internal fun getNextClockwiseEdge(hex: Hex, edge: Edge): Edge {
        fun findNext(e: Edge) = hex.edge(hex.getEdgeIndex(e).next())
        var next = findNext(edge)
        while (next.isOutsideEdge() && next != edge) {
            next = findNext(next)
        }
        return next
    }

}