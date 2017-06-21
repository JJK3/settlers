package org.jjk3.board

enum class Direction {
    Up, Down, RightUp, RightDown, LeftUp, LeftDown
}

data class HexCoordinate(val x: Int, val y: Int) {

    fun up() = HexCoordinate(x, y - 1)
    fun down() = HexCoordinate(x, y + 1)
    fun rightUp() = HexCoordinate(x + 1, Math.abs(x % 2) + y - 1)
    fun rightDown() = HexCoordinate(x + 1, Math.abs(x % 2) + y)
    fun leftUp() = HexCoordinate(x - 1, Math.abs(x % 2) + y - 1)
    fun leftDown() = HexCoordinate(x - 1, Math.abs(x % 2) + y)
    fun move(direction: Direction) =
            when (direction) {
                Direction.Up -> up()
                Direction.Down -> down()
                Direction.LeftUp -> leftUp()
                Direction.LeftDown -> leftDown()
                Direction.RightUp -> rightUp()
                Direction.RightDown -> rightDown()
            }

    /** get the hex coordinate on the opposite side of the given edge. */
    fun getOppositeHex(edge_num: EdgeNumber): HexCoordinate = move(edge_num.direction())
}

class Hex(val resource: Resource?, var number: Int, var coords: HexCoordinate = HexCoordinate(- 1, - 1)) {
    var hasBandit: Boolean = false
    var nodes = (0..5).map { Node() }.toTypedArray()
    var edges = (0..5).map { Edge() }.toTypedArray()

    init {
        EdgeNumber.ALL.forEach { i ->
            edge(i).hexes += Pair(this, i)
        }
        NodeNumber.ALL.forEach { i ->
            node(i).hexes += Pair(this, i)
        }
    }

    fun copy(): Hex {
        val hex = Hex(resource, number, coords)
        nodes.forEachIndexed { index, n ->
            val newNode = hex.nodes[index]
            newNode.port = n.port
            newNode.city = n.city
        }
        edges.forEachIndexed { index, e ->
            val newEdge = hex.edges[index]
            newEdge.road = e.road
        }
        hex.hasBandit = hasBandit
        return hex
    }

    fun getCard(): Resource = resource ?: throw Exception("Cannot take card from a desert.")
    fun get2Cards(): List<Resource> = listOf(getCard(), getCard())
    fun nodesWithCities() = nodes.filter(Node::hasCity)
    fun nodesWithCities(color: String) = nodes.filter { it.city?.color == color }
    /** is this hex on the outside edge of the map? */
    fun isOnOutside(): Boolean = edges.any(Edge::isOutsideEdge)

    fun node(number: NodeNumber): Node = nodes[number.n]
    fun edge(number: EdgeNumber): Edge = edges[number.n]
    fun adjecentEdges(node: NodeNumber) = listOf(edge(node.prevEdge())) + edge(node.nextEdge())
    fun adjecentNodes(edge: EdgeNumber) = listOf(node(edge.prevNode())) + node(edge.nextNode())
    fun setNode(node: Node, coord: NodeNumber) {
        nodes[coord.n] = node
        node.hexes += Pair(this, coord)
    }

    fun setEdge(edge: Edge, coord: EdgeNumber) {
        edges[coord.n] = edge
        edge.hexes += Pair(this, coord)
    }

    fun getNodeIndex(node: Node) = NodeNumber(nodes.indexOf(node))
    fun getEdgeIndex(edge: Edge) = EdgeNumber(edges.indexOf(edge))
    fun replaceEdge(current: Edge, new: Edge) {
        if (! edges.contains(current)) {
            throw IllegalArgumentException("$current is not in $edges")
        }
        val edgeNum = current.hexes[this] !!
        setEdge(new, edgeNum)
        val hexes = current.hexes.keys
        val otherHex = new.hexes.keys.first()
        val otherEdgeNum = edgeNum.opposite()
        replaceNode(node(edgeNum.nextNode()), otherHex.node(otherEdgeNum.prevNode()), hexes)
        replaceNode(node(edgeNum.prevNode()), otherHex.node(otherEdgeNum.nextNode()), hexes)
    }

    private fun replaceNode(oldNode: Node, newNode: Node, hexes: Collection<Hex>) {
        for (hex in hexes) {
            val nodeNumber = oldNode.hexes[hex]
            hex.setNode(newNode, nodeNumber !!)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Hex

        if (coords != other.coords) return false

        return true
    }

    override fun hashCode(): Int {
        return coords.hashCode()
    }

    override fun toString(): String {
        return "Hex(x=${coords.x}, y=${coords.y})"
    }

}

