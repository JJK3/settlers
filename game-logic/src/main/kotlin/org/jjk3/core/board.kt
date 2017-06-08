package org.jjk3.core

import org.apache.log4j.Logger
import java.util.concurrent.ConcurrentHashMap

/** This error denotes that something occurred against the rules. */
class RuleException(msg: String) : Exception(msg)

/**
 * The central data structure for the Settlers game. The org.jjk3.board contains all ref
 * regarding tiles, cities, settlements, bandits, etc.
 */
open class Board(val should_enforce_bandit: Boolean = true) {
    companion object {
        var log = Logger.getLogger(this::class.java)
    }

    val tiles = HashMap<HexCoordinate, Hex>()

    var developmentCards = DevelopmentCardBag.create()
    val piecesForSale = ConcurrentHashMap<String, PiecesForSale>()

    private val longestRoadAuthority = LongestRoadDetector(this)
    fun allNodes(): Set<Node> = tiles.values.flatMap { it.nodes.toList() }.toSet()
    fun allEdges() = tiles.values.flatMap { it.edges.toList() }.toSet()
    fun copy(): Board {
        val b = Board(should_enforce_bandit)
        b.tiles.putAll(this.tiles.mapValues { it.value.copy() })
        b.developmentCards = developmentCards
        b.piecesForSale.putAll(ConcurrentHashMap(piecesForSale))
        return b
    }

    fun getPiecesForSale(color: String) = piecesForSale[color] ?: throw IllegalArgumentException(
            "Color not found: $color")

    fun getHex(coord: HexCoordinate): Hex = getHexOrNull(coord) ?: throw IllegalArgumentException(
            "Hex not found: $coord")

    fun getHexOrNull(coord: HexCoordinate): Hex? = tiles[coord]
    fun getNode(coord: NodeCoordinate): Node = getHex(coord.hex).node(coord.nodeNumber)
    fun getEdge(coord: EdgeCoordinate): Edge = getHex(coord.hex).edge(coord.edgeNumber)
    /**
     * Get all complete roads.
     * @return A list of complete roads, where a complete road is represented by a set of edges
     * */
    fun getAllRoadGroups(): List<Set<Edge>> {
        var result = emptyList<Set<Edge>>()
        var edgesToExamine: List<Edge> = allEdges().filter(Edge::hasRoad)
        while (edgesToExamine.isNotEmpty()) {
            val edge = edgesToExamine.first()
            val completeRoad = edge.getCompleteRoad()
            result = result.plusElement(completeRoad)
            edgesToExamine -= completeRoad
        }
        return result
    }

    /** Does the given player have longest road. */
    fun hasLongestRoad(color: String): Boolean = longestRoadAuthority.hasLongestRoad(color)

    fun getLongestRoad(edge: Edge): List<Edge> = longestRoadAuthority.getLongestRoad(edge)
    /** Port nodes controlled by the given player. */
    fun portNodes(color: String) = allNodes().filter { n -> n.hasPort() && n.city?.color == color }.toList()

    /** Ports controlled by the given player */
    fun getPorts(color: String): List<Port> = portNodes(color).map(Node::port).filterNotNull().toList()

    /** Gets a list of cards, that the given player should receive */
    fun getCards(number: Int, color: String): List<Resource> {
        val valid_hexes = tiles.values.filter { t ->
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

    fun placeRoad(road: Road, edgeCoordinate: EdgeCoordinate): Edge =
            synchronized(this) {
                getEdge(edgeCoordinate).let {
                    it.road = road
                    it
                }
            }

    fun removeRoad(edgeCoordinate: EdgeCoordinate): Edge =
            synchronized(this) {
                getEdge(edgeCoordinate).let { edge ->
                    if (!edge.hasRoad()) throw IllegalStateException("Edge does not have a road on it")
                    edge.road = null
                    edge
                }
            }

    /**
     * Called ONLY by a Turn object, this method mutates the org.jjk3.board by placing a
     * City or Settlement on it.
     */
    fun placeCity(city: City, coord: NodeCoordinate): Node {
        synchronized(this) {
            val node = getNode(coord)
            node.city = city
            return node
        }
    }

    fun placeBanditOnDesert() = setBandit(findDesert() ?: throw IllegalStateException("Cannot find desert hex"))
    private fun findDesert() = tiles.values.find { it.resource == null }
    private fun setBandit(hex: Hex) = tiles.values.forEach { it.has_bandit = (it == hex) }
    /**
     * Move the bandit to a  hex
     * @return the old hex that the bandit was on.
     */
    fun moveBandit(hex: Hex): Hex {
        synchronized(this) {
            val currentBanditHex = tiles.values.find(Hex::has_bandit) ?: throw RuleException(
                    "Board does not currently have a bandit ${this}")

            if (currentBanditHex == hex) {
                throw RuleException("Bandit cannot be moved to the Tile it's already on")
            }
            val tile = getHex(hex.coords)
            setBandit(tile)
            return currentBanditHex
        }
    }

    /**
     * Gets a list of Nodes that settlements can be placed on. Settlements can only be placed on pre-existing roads.
     * <color> The Player's color
     */
    fun getValidSettlementSpots(color: String): List<Node> =
            synchronized(this) {
                allNodes().filter { n -> hasTouchingRoad(n, color) && is2AwayFromCity(n) && !n.hasCity() }
            }

    /**
     * Gets a list of Nodes that settlements can be placed on regardless of existing cities or settlements.
     */
    fun getValidSettlementSpots(): List<Node> =
            synchronized(this) {
                allNodes().filter { is2AwayFromCity(it) && !it.hasCity() }
            }

    private fun hasTouchingRoad(n: Node, roadColor: String) = n.edges().find { it.road?.color == roadColor } != null
    private fun is2AwayFromCity(n: Node) = n.getAdjecentNodes().none(Node::hasCity)
    /**
     * Get all the valid places to put a road.
     * NOTE: if someone else builds a settlement, you can't build a road through it.
     * @param touching_node If specified, valid road spots MUST touch this node.
     * This is used during setup when players can only place a road touching the
     * settlement they just placed.
     */
    fun getValidRoadSpots(road_color: String, touching_node: Node? = null): List<Edge> {
        synchronized(this) {
            var result: List<Edge> = emptyList()
            allNodes().forEach { n ->
                if (n.hasCity() && n.city!!.color == road_color) {
                    result += (n.edges().filterNot(Edge::hasRoad))
                } else if (!n.hasCity()) {
                    n.edges().forEach { edge ->
                        if (edge.hasRoad() && edge.road!!.color == road_color) {
                            result += (n.edges().filterNot(Edge::hasRoad))
                        }
                    }
                }
            }
            if (touching_node != null) result = result.filter { it.nodes().contains(touching_node) }
            return result.distinct()
        }
    }

    /** Get all the valid spots to place a city. */
    fun getValidCitySpots(color: String): List<Node> {
        synchronized(this) {
            return allNodes().filter { node ->
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
     * Add a  hex to the org.jjk3.board.  We assume that the org.jjk3.board is already in a correct state where there are not duplicate edges or nodes.
     */
    fun addHex(hex: Hex) {
        if (tiles.contains(hex.coords)) {
            throw IllegalArgumentException("The given hex is already on the org.jjk3.board: ${hex.coords}")
        }
        tiles[hex.coords] = hex
        EdgeNumber.ALL.forEach { i ->
            val old = hex.edge(i)
            getHexOrNull(hex.coords.getOppositeHex(i))?.let { opposing_hex ->
                val new = opposing_hex.edge(i.opposite())
                hex.replaceEdge(old, new)
            }
        }
    }
}



