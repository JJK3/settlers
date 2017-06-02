package core

enum class Direction {
    Up, Down, RightUp, RightDown, LeftUp, LeftDown
}

data class HexCoordinate(val x: Int, val y: Int) {

    fun up() = HexCoordinate(x, y - 1)
    fun down() = HexCoordinate(x, y + 1)
    fun right_up() = HexCoordinate(x + 1, Math.abs(x % 2) + y - 1)
    fun right_down() = HexCoordinate(x + 1, Math.abs(x % 2) + y)
    fun left_up() = HexCoordinate(x - 1, Math.abs(x % 2) + y - 1)
    fun left_down() = HexCoordinate(x - 1, Math.abs(x % 2) + y)
    fun move(direction: Direction) =
            when (direction) {
                Direction.Up -> up()
                Direction.Down -> down()
                Direction.LeftUp -> left_up()
                Direction.LeftDown -> left_down()
                Direction.RightUp -> right_up()
                Direction.RightDown -> right_down()
            }

    /** get the hex coordinate on the opposite side of the given edge. */
    fun getOppositeHex(edge_num: EdgeNumber): HexCoordinate = move(edge_num.direction())
}

class Hex(val resource: Resource?, var number: Int, var coords: HexCoordinate = HexCoordinate(-1, -1)) {
    var has_bandit: Boolean = false
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
        fun <T> replaceMe(hexes: Map<Hex, T>) = hexes.mapKeys { if (it.key == this) hex else it.key }
        hex.nodes = nodes.map { n ->
            val newNode = Node()
            newNode.port = n.port
            newNode.city = n.city
            n.hexes = replaceMe(n.hexes)
            n
        }.toTypedArray()
        hex.edges = edges.map { e ->
            val newEdge = Edge()
            newEdge.road = e.road
            newEdge.hexes = replaceMe(e.hexes)
            e
        }.toTypedArray()
        return hex
    }

    fun get_card(): Resource = resource ?: throw Exception("Cannot take card from a desert.")
    fun get_2_cards(): List<Resource> = listOf(get_card(), get_card())
    fun nodes_with_cities() = nodes.filter(Node::hasCity)
    fun nodes_with_cities(color: String) = nodes.filter { it.city?.color == color }
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
        if (!edges.contains(current)) {
            throw IllegalArgumentException("$current is not in $edges")
        }
        val otherHex = new.hexes.keys.first()
        val edgeNum = current.hexes[this]!!
        val otherEdgeNum = edgeNum.opposite()
        setEdge(new, edgeNum)

        setNode(otherHex.node(otherEdgeNum.prevNode()), edgeNum.nextNode())
        setNode(otherHex.node(otherEdgeNum.nextNode()), edgeNum.prevNode())
    }

    fun nextEdge(edge: Edge) = edge(getEdgeIndex(edge).next())
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

