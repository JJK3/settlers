package org.jjk3.core

/**
 * Node numbers
 *    5---------0
 *    /         \
 *   /           \
 * 4/             \1
 *  \             /
 *   \           /
 *    \         /
 *    3---------2
 */
data class NodeNumber(val n: Int) {
    companion object {
        val ALL = (0..5).map(::NodeNumber)
    }

    init {
        if (!(0..5).contains(n)) {
            throw IllegalArgumentException("Invalid NodeNumber: $n")
        }
    }

    private fun normalize(x: Int) = x % 6
    fun next() = NodeNumber(normalize(n + 1))
    fun prev() = NodeNumber(normalize(n + 5))
    fun opposite() = NodeNumber(normalize(n + 3))
    /** Going counter-clockwise, get the coord of the previous Edge. */
    fun prevEdge() = EdgeNumber(n)

    /** Going clockwise, get the coord of the next Edge. */
    fun nextEdge() = EdgeNumber(normalize(n + 1))
}

data class NodeCoordinate(val hex: HexCoordinate, val nodeNumber: NodeNumber) {
    constructor(x: Int, y: Int, nodeNumber: Int) : this(HexCoordinate(x, y), NodeNumber(nodeNumber))
}

/** This Corresponds to a node on the org.jjk3.board where settlements and cities can be placed. */
class Node {
    var city: City? = null
    var port: Port? = null
    var hexes: Map<Hex, NodeNumber> = emptyMap()
    fun edges() = hexes.flatMap { it.key.adjecentEdges(it.value) }.toSet()
    fun coords() = hexes.map { NodeCoordinate(it.key.coords, it.value) }.first()
    /** The Array of adjecent nodes */
    fun getAdjecentNodes() = edges().flatMap(Edge::nodes).toSet().filter { it != this }

    fun hasCity(): Boolean = this.city != null
    fun hasPort(): Boolean = this.port != null
    override fun toString(): String = "Node(city=$city, port=$port, coords=$hexes)"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Node

        if (hexes != other.hexes) return false

        return true
    }

    override fun hashCode(): Int {
        return hexes.hashCode()
    }

}
