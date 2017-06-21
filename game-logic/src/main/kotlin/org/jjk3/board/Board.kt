package org.jjk3.board

import com.google.common.base.Equivalence
import org.apache.log4j.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * The central data structure for the Settlers game. The org.jjk3.board contains all ref
 * regarding tiles, cities, settlements, bandits, etc.
 */
open class Board() {
    companion object {
        var log: Logger = Logger.getLogger(this::class.java)
    }

    val tiles = HashMap<HexCoordinate, Hex>()
    open var developmentCards = DevelopmentCardBag.create()
    val piecesForSale = ConcurrentHashMap<String, PiecesForSale>()
    private val longestRoadDetector by lazy { LongestRoadDetector(this) }
    open fun allNodes(): Set<Node> = tiles.values.flatMap { it.nodes.toList() }.toSet()
    fun allEdges() = tiles.values.flatMap { it.edges.toList() }.toSet()
    fun copy(): Board {
        val b = Board()
        tiles.values.forEach { b.addHex(it.copy()) }
        b.developmentCards = developmentCards
        b.piecesForSale.putAll(ConcurrentHashMap(piecesForSale))
        return b
    }

    private fun getNodeCount(b: Board): Int {
        val nodes = b.tiles.values.flatMap { it.nodes.toList() }
        val identity = Equivalence.identity()
        return nodes.map { identity.wrap(it) }.toSet().size
    }

    open fun getPiecesForSale(color: String) = piecesForSale[color] ?: throw IllegalArgumentException(
            "Color not found: $color")

    open fun getHex(coord: HexCoordinate): Hex = getHexOrNull(coord) ?: throw IllegalArgumentException(
            "Hex not found: $coord")

    fun getHexOrNull(coord: HexCoordinate): Hex? = tiles[coord]
    open fun getNode(coord: NodeCoordinate): Node = getHex(coord.hex).node(coord.nodeNumber)
    open fun getEdge(coord: EdgeCoordinate): Edge = getHex(coord.hex).edge(coord.edgeNumber)
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
    open fun hasLongestRoad(color: String): Boolean = longestRoadDetector.hasLongestRoad(color)

    fun getLongestRoad(edge: Edge): List<Edge> = longestRoadDetector.getLongestRoad(edge)
    /** Port nodes controlled by the given player. */
    fun portNodes(color: String) = allNodes().filter { n -> n.hasPort() && n.city?.color == color }.toList()

    /** Ports controlled by the given player */
    fun getPorts(color: String): List<Port> = portNodes(color).map(Node::port).filterNotNull().toList()

    /** Gets a list of cards, that the given player should receive */
    open fun getCards(number: Int, color: String): List<ResourceCard> {
        val valid_hexes = tiles.values.filter { t ->
            t.number == number && ! t.hasBandit && t.resource != null
        }
        return valid_hexes.flatMap { hex ->
            hex.nodesWithCities(color).flatMap { n ->
                when (n.city) {
                    is Settlement -> listOf(hex.getCard())
                    is City -> hex.get2Cards()
                    else -> emptyList()
                }
            }
        }.toList().map(::ResourceCard)
    }

    open fun placeRoad(road: Road, edgeCoordinate: EdgeCoordinate): Edge =
            synchronized(this) {
                getEdge(edgeCoordinate).let {
                    it.road = road
                    it
                }
            }

    fun removeRoad(edgeCoordinate: EdgeCoordinate): Edge =
            synchronized(this) {
                getEdge(edgeCoordinate).let { edge ->
                    if (! edge.hasRoad()) throw IllegalStateException("Edge does not have a road on it")
                    edge.road = null
                    edge
                }
            }

    /**
     * Mutates the board by placing a City or Settlement on it.
     */
    open fun placeCity(city: City, coord: NodeCoordinate): Node {
        synchronized(this) {
            val node = getNode(coord)
            node.city = city
            return node
        }
    }

    fun placeBanditOnDesert() = setBandit(findDesert() ?: throw IllegalStateException("Cannot find desert hex"))
    fun findDesert(): Hex? = tiles.values.find { it.resource == null }
    open fun findBandit(): Hex? = tiles.values.find(Hex::hasBandit)
    private fun setBandit(hex: Hex) = tiles.values.forEach { it.hasBandit = (it == hex) }
    /**
     * Move the bandit to a  hex
     * @return the old hex that the bandit was on.
     */
    open fun moveBandit(newLocation: HexCoordinate): Hex {
        synchronized(this) {
            val currentBanditHex = tiles.values.find(Hex::hasBandit) ?: throw RuleException(
                    "Board does not currently have a bandit ${this}")

            if (currentBanditHex.coords == newLocation) {
                throw RuleException("Bandit cannot be moved to the Tile it's already on")
            }
            val tile = getHex(newLocation)
            setBandit(tile)
            return currentBanditHex
        }
    }

    /**
     * Gets a list of Nodes that settlements can be placed on. Settlements can only be placed on pre-existing roads.
     * <color> The Player's color
     */
    open fun getValidSettlementSpots(color: String): List<Node> =
            synchronized(this) {
                allNodes().filter { n -> hasTouchingRoad(n, color) && is2AwayFromCity(n) && ! n.hasCity() }
            }

    /**
     * Gets a list of Nodes that settlements can be placed on regardless of existing cities or settlements.
     */
    fun getValidSettlementSpots(): Set<Node> =
            synchronized(this) {
                allNodes().filter { is2AwayFromCity(it) && ! it.hasCity() }.toSet()
            }

    private fun hasTouchingRoad(n: Node, roadColor: String) = n.edges().find { it.road?.color == roadColor } != null
    private fun is2AwayFromCity(n: Node) = n.getAdjecentNodes().none(Node::hasCity)
    /**
     * Get all the valid places to put a road.
     * NOTE: if someone else builds a settlement, you can't build a road through it.
     * @param touchingNode If specified, valid road spots MUST touch this node.
     * This is used during setup when players can only place a road touching the
     * settlement they just placed.
     */
    open fun getValidRoadSpots(color: String): List<Edge> {
        synchronized(this) {
            return allNodes().flatMap { n ->
                if (n.city?.color == color) {
                    n.edges().filterNot(Edge::hasRoad)
                } else if (! n.hasCity()) {
                    n.edges().flatMap { edge ->
                        if (edge.road?.color == color) {
                            n.edges().filterNot(Edge::hasRoad)
                        } else {
                            emptyList()
                        }
                    }
                } else {
                    emptyList()
                }
            }.distinct()
        }
    }

    open fun getValidRoadSpots(color: String, touchingNode: NodeCoordinate): List<Edge> =
            getValidRoadSpots(color).filter { it.nodes().any { n -> n.isAt(touchingNode) } }

    /** Get all the valid spots to place a city. */
    open fun getValidCitySpots(color: String): List<Node> {
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
     * Add a hex to the board.  We assume that the board is already in a correct state where there are not duplicate
     * edges or nodes.
     */
    fun addHex(hex: Hex) {
        if (tiles.contains(hex.coords)) {
            throw IllegalArgumentException("The given hex is already on the board: ${hex.coords}")
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



