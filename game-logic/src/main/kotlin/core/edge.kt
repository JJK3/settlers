package core

import java.util.*

/**
 * Edge numbers
 *         0
 *     ---------
 *    /         \
 *  5/           \1
 *  /             \
 *  \             /
 *  4\           / 2
 *    \         /
 *     ---------
 *        3
 */
data class EdgeNumber(val n: Int) {
    companion object {
        val ALL = (0..5).map(::EdgeNumber)
    }

    init {
        if (!(0..5).contains(n)) {
            throw IllegalArgumentException("Invalid EdgeNumber: $n")
        }
    }

    private fun normalize(x: Int) = x % 6
    fun next() = EdgeNumber(normalize(n + 1))
    fun prev() = EdgeNumber(normalize(n + 5))
    fun opposite() = EdgeNumber(normalize(n + 3))
    /** Going counter-clockwise, get the coord of the previous node. */
    fun prevNode() = NodeNumber(normalize(n + 5))

    /** Going clockwise, get the coord of the next node. */
    fun nextNode() = NodeNumber(n)

    fun direction() =
            when (n) {
                0 -> Direction.Up
                1 -> Direction.RightUp
                2 -> Direction.RightDown
                3 -> Direction.Down
                4 -> Direction.LeftDown
                5 -> Direction.LeftUp
                else -> throw IllegalArgumentException("Can't happen")
            }

}

data class EdgeCoordinate(val hex: HexCoordinate, val edgeNumber: EdgeNumber) {

    constructor(x: Int, y: Int, edgeNumber: Int) : this(HexCoordinate(x, y), EdgeNumber(edgeNumber))

    /** Two EdgeCoordinate are equivalent when they are on different Hexes, but still refer to the same edge */
    fun equivalent(other: EdgeCoordinate): Boolean {
        if (other == this) {
            return true
        }
        val oppositeHex = hex.move(edgeNumber.direction())
        return EdgeCoordinate(oppositeHex, edgeNumber.opposite()) == other
    }
}

/** An Edge is basically just a collection of 2 nodes that belongs to a hex */
class Edge {
    var hexes: Map<Hex, EdgeNumber> = emptyMap()
    var road: Road? = null
    fun coords() = hexes.map { EdgeCoordinate(it.key.coords, it.value) }.first()
    fun nodes() = hexes.flatMap { it.key.adjecentNodes(it.value) }.toSet()
    /**
     * Get all the Edges touching this Edge.
     * This will return a Set , size bewteen 2 and 4.
     */
    fun getAdjecentEdges(): Set<Edge> = nodes().flatMap(Node::edges).filter { it != this }.toSet()

    /** Given one adjecent edge, get the edge(s) on the other side. */
    fun getOppositeEdges(adjecentEdge: Edge): Set<Edge> {
        val touchingNode = nodes().find { it.edges().contains(adjecentEdge) }
        val otherNode = (nodes() - touchingNode).first()!!
        return otherNode.edges().filter { it != this }.toSet()
    }

    /** Is this edge touching the outside of the board? */
    fun isOutsideEdge() = hexes.size < 2

    fun hasRoad(): Boolean = this.road != null
    fun hasPort(): Boolean = nodes().all(Node::hasPort)
    /** Get every mathing connected road */
    fun getCompleteRoad(): Set<Edge> {
        return getRoadHelper(HashSet())
    }

    private fun getRoadHelper(visitedEdges: Set<Edge>): Set<Edge> {
        road?.let { road ->
            var roadEdges = visitedEdges + this
            getAdjecentEdges().forEach { e ->
                if (!roadEdges.contains(e)) {
                    if (road.color == e.road?.color) {
                        roadEdges += e.getRoadHelper(roadEdges)
                    }
                }
            }
            return roadEdges
        }
        return emptySet()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Edge
        if (hexes != other.hexes) return false
        return true
    }

    override fun hashCode(): Int {
        return hexes.hashCode()
    }

    override fun toString(): String {
        return "Edge(${coords()})"
    }

}
