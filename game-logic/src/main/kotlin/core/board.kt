package core

import org.apache.log4j.Logger

/** This error denotes that something occurred against the rules. */
class RuleException(msg: String) : Exception(msg)

/**
 * The central data structure for the Settlers game. The board contains all info
 * regarding tiles, cities, settlements, bandits, etc.
 */
open class Board(val should_enforce_bandit: Boolean = true) {
    companion object {
        var log = Logger.getLogger(javaClass)
    }

    val tiles = HashMap<HexCoordinate, Hex>()
    val longestRoadAuthority = LongestRoadAuthority(this)
    fun all_nodes(): Set<Node> = tiles.values.flatMap { it.nodes.toList() }.toSet()
    fun nodes() = all_nodes()
    fun all_edges() = tiles.values.flatMap { it.edges.toList() }.toSet()
    fun edges() = all_edges()
    fun copy(): Board {
        val b = Board(should_enforce_bandit)
        b.tiles.putAll(this.tiles.mapValues { it.value.copy() })
        return b
    }

    /** Get the Hex object at the given coordinates */
    fun getTile(x: Int, y: Int): Hex? = tiles[HexCoordinate(x, y)]

    fun getTile(coord: HexCoordinate): Hex? = tiles[coord]
    fun getTileWithError(x: Int, y: Int): Hex = getTile(x, y) ?: throw IllegalArgumentException(
            "Hex not found: (" + x + "," + y + ")")

    /**
     * Get the Node object at the given coordinates
     * @param x The Cartesian x coordinate of the Hex
     * @param y The Cartesian y coordinate of the Hex
     * @param n The node number on that Hex
     */
    fun getNode(x: Int, y: Int, n: Int): Node? = getTile(x, y)?.node(NodeNumber(n))

    fun getNode(coord: NodeCoordinate): Node? = getTile(coord.hex)?.node(coord.nodeNumber)
    /**
     * Get the Edge object at the given coordinates
     * @param x The Cartesian x coordinate of the Hex
     * @param y The Cartesian y coordinate of the Hex
     * @param e The edge number on that Hex
     */
    fun getEdge(x: Int, y: Int, e: Int): Edge? = getTile(x, y)?.edges?.get(e)

    fun getEdge(coord: EdgeCoordinate): Edge? = getTile(coord.hex)?.edge(coord.edgeNumber)
    /** Get all complete roads.
     * @return A list of complete roads, where a complete road is represented by a set of edges */
    fun getAllRoadGroups(): List<Set<Edge>> {
        var result = emptyList<Set<Edge>>()
        var edgesToExamine: List<Edge> = edges().filter(Edge::has_road)
        while (edgesToExamine.isNotEmpty()) {
            val edge = edgesToExamine.first()
            val completeRoad = edge.getCompleteRoad()
            result = result.plusElement(completeRoad)
            edgesToExamine -= completeRoad
        }
        return result
    }

    /** Does the given player have longest road. */
    fun has_longest_road(color: String): Boolean = longestRoadAuthority.hasLongestRoad(color)

    fun getLongestRoad(edge: Edge): List<Edge> = longestRoadAuthority.getLongestRoad(edge)

    /** Port nodes controlled by the given player. */
    fun port_nodes(color: String) =
            all_nodes().filter { n -> n.has_city() && n.has_port() && n.city?.color == color }.toList()

    /** Ports controlled by the given player */
    fun get_ports(color: String): List<Port> = port_nodes(color).map { it.port }.filterNotNull().toList()

    /** Gets a list of cards, that the given player should receive */
    fun get_cards(number: Int, color: String): List<Resource> {
        var valid_hexes = tiles.values.filter { t ->
            t.number == number && !t.has_bandit && t.resource != null
        }
        return valid_hexes.flatMap { hex ->
            hex.nodes_with_cities(color).flatMap { n ->
                when (n.city) {
                    is Settlement -> listOf(hex.get_card())
                    is City -> hex.get_2_cards()
                    else -> emptyList()
                }
            }
        }.toList()
    }

    /** A helper to wrap edge mutations. */
    private fun edge_updater(tileX: Int, tileY: Int, edgeNum: Int, visitor: (Edge) -> Unit): Edge {
        synchronized(this) {
            var t = getTileWithError(tileX, tileY)
            var edge = t.edges[edgeNum]
            visitor.invoke(edge)
            return edge
        }
    }

    /**
     * This method mutates the board by placing a road on it.
     * NOTE: this method doesn't to any validation based upon rules.  It just modifies the board.
     */
    fun place_road(road: Road, tileX: Int, tileY: Int, edgeNum: Int): Edge =
            edge_updater(tileX, tileY, edgeNum, { edge ->
                edge.road = road
            })

    fun remove_road(tileX: Int, tileY: Int, edgeNum: Int): Edge =
            edge_updater(tileX, tileY, edgeNum, { edge ->
                if (!edge.has_road()) throw  IllegalStateException("Edge does not have a road on it")
                var adjecentEdges = edge.get_adjecent_edges().filter { it.has_road() }
                edge.road = null
            })

    fun enforce_bandit() {
        val desert = findDesert()
        return when (desert) {
            null -> {
                log.warn("Could not find desert tile, placing bandit on first hex")
                setBandit(tiles.values.first())
            }
            else -> setBandit(desert)
        }
    }

    private fun findDesert() = tiles.values.find { it.resource == null }
    private fun setBandit(hex: Hex) = tiles.values.forEach { it.has_bandit = (it == hex) }
    /**
     * Called ONLY by a Turn object, this method mutates the board by placing a
     * City or Settlement on it.
     */
    fun place_city(city: City, x: Int, y: Int, nodeNum: Int): Node {
        synchronized(this) {
            val node = getTileWithError(x, y).nodes[nodeNum]
            node.city = city
            return node
        }
    }

    /**
     * Move the bandit to a  hex
     * @return the old hex that the bandit was on.
     */
    fun move_bandit(_hex: Hex): Hex {
        synchronized(this) {
            val current_bandit_hex = tiles.values.find { it.has_bandit } ?: throw RuleException(
                    "Board does not currently have a bandit ${this}")

            if (current_bandit_hex == _hex) {
                throw RuleException("Bandit cannot be moved to the Tile it's already on")
            }
            val local_tile = getTile(_hex.coords.x, _hex.coords.y) ?:
                    throw  IllegalArgumentException(" Hex was not found on the Board:" + _hex)
            setBandit(local_tile)
            return current_bandit_hex
        }
    }

    /**
     * Gets a list of Nodes that settlements can be placed on.
     * <roadConstraint> A boolean that ensures that settlements can only be placed
     * on pre-existing roads. For SetupTurns, this should be false.
     * (Players don't need to connect settlements to roads during setup. )
     * <roadColor> The color of the road to constrain against.  For a normal turn,
     * you need to ask which spots are valid for your player's color.
     * returns a list of Node objects
     */
    fun get_valid_settlement_spots(roadConstraint: Boolean, roadColor: String): List<Node> {
        synchronized(this) {
            return all_nodes().filter { n ->
                val is2AwayFromCities: Boolean = n.get_adjecent_nodes().none { it.has_city() }
                if (roadConstraint) {
                    val hasTouchingRoad = n.edges().find { e -> e.has_road() && e.road?.color == roadColor } != null
                    hasTouchingRoad && is2AwayFromCities && !n.has_city()
                } else {
                    is2AwayFromCities && !n.has_city()
                }
            }.toSet().toList()
        }
    }

    /**
     * Get all the valid places to put a road.
     * NOTE: if someone else builds a settlement, you can't build a road through it.
     * @param touching_node If specified, valid road spots MUST touch this node.
     * This is used during setup when players can only place a road touching the
     * settlement they just placed.
     */
    fun get_valid_road_spots(road_color: String, touching_node: Node? = null): List<Edge> {
        synchronized(this) {
            var result: List<Edge> = emptyList()
            all_nodes().forEach { n ->
                if (n.has_city() && n.city!!.color == road_color) {
                    result = result + (n.edges().filterNot { it.has_road() })
                } else if (!n.has_city()) {
                    n.edges().forEach { edge ->
                        if (edge.has_road() && edge.road!!.color == road_color) {
                            result = result + (n.edges().filterNot { it.has_road() })
                        }
                    }
                }
            }
            if (touching_node != null) result = result.filter { it.nodes().contains(touching_node) }
            return result.distinct()
        }
    }

    /** Get all the valid spots to place a city. */
    fun get_valid_city_spots(color: String): List<Node> {
        synchronized(this) {
            return all_nodes().filter { node ->
                node.city?.let { city ->
                    when (city) {
                        is Settlement -> city.color == color
                        else -> false
                    }
                } ?: false
            }.toList()
        }
    }

    /**
     * Add a  hex to the board.  We assume that the board is already in a correct state where there are not duplicate edges or nodes.
     */
    fun add_hex(hex: Hex) {
        if (tiles.contains(hex.coords)) {
            throw IllegalArgumentException("The given hex is already on the board:" + hex.coords)
        }
        tiles[hex.coords] = hex

        //Take the exising edges
        EdgeNumber.ALL.forEach { i ->
            val old = hex.edge(i)
            get_opposite_hex(hex, i.n)?.let { opposing_hex ->
                val new = opposing_hex.edge(i.opposite())
                hex.replaceEdge(old, new)
            }
        }
    }

    /**
     * Given a hex and an edge number, this will return the opposite hex, or null if there isn't one.
     */
    fun get_opposite_hex(hex: Hex, edge: Int): Hex? {
        val coords = when (edge) {
            0 -> hex.coords.up()
            1 -> hex.coords.right_up()
            2 -> hex.coords.right_down()
            3 -> hex.coords.down()
            4 -> hex.coords.left_down()
            5 -> hex.coords.left_up()
            else -> throw IllegalArgumentException("bad edge: ${edge}")
        }
        return getTile(coords.x, coords.y)
    }
}



