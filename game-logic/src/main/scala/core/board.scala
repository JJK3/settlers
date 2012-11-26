/**
 * For ruby to scala conversions:
 * (\w+)\.new   => new $1
 * (def\W?[^\){]+\)) => $1{
 * \Wend\W => }
 * \Wand\W => &&
 * \.nil\? => == null
 * nil => null
 *
 */

package core

import scala.collection.mutable.HashMap
import org.apache.log4j._
import scala.util.Random
import UtilList._

/**
 * The central data structure for the Settlers game. The board contains all info 
 * regarding tiles, cities, settlements, bandits, etc.
 */
abstract class Board(randomize: Boolean = true, should_enforce_bandit: Boolean = true) {
    val tiles = new HashMap[(Int, Int), Hex]()
    var log = Logger.getLogger(classOf[Board])
    /** A list of edges that contain roads.  For performance. */
    var roadEdges = Set[Edge]()

    var card_pile = new DevelopmentCardBag()
    val modify_mutex = new Mutex()
    subclass_init
    if (tiles.values.isEmpty)
        throw new IllegalStateException("Creating a board with no tiles")
    if (randomize) randomize_board()

    // Initialize the bandit on the desert
    if (should_enforce_bandit) {
        enforce_bandit
    }

    var desert_count = tiles.values.count { _.card_type == DesertType }
    if (desert_count != 1) {
        //	throw new IllegalStateException("This board has "+desert_count + " deserts")
    }

    all_edges.filter { _.nodes.forall { _.has_port } }.foreach { port_edge =>
        if (port_edge.hexes.size != 1) {
            var coords = port_edge.coords
            throw new IllegalStateException("Attempting to build a board with an inland port at (" +
                coords + ") adjecent hexes:" + port_edge.hexes.size)
        }
    }

    //TODO: I *may* want to cache this like in the ruby version.
    def all_nodes: Set[Node] = tiles.values.flatMap { _.nodes }.toSet
    def nodes = all_nodes
    def all_edges: Set[Edge] = tiles.values.flatMap { _.edges }.toSet
    def edges = all_edges

    def copy(): Board = {
        new BoardCopy(this)
    }

    /** Get the Hex object at the given coordinates */
    def getTile(x: Int, y: Int): Hex = if (tiles.contains((x, y))) { tiles((x, y)) } else { null }

    def getTileWithError(x: Int, y: Int): Hex = {
        var t = getTile(x, y)
        if (t == null)
            throw new IllegalArgumentException("Hex not found: (" + x + "," + y + ")")
        t
    }

    /**
     * Get the Node object at the given coordinates
     * @param x The Cartesian x coordinate of the Hex
     * @param y The Cartesian y coordinate of the Hex
     * @param n The node number on that Hex
     */
    def getNode(x: Int, y: Int, n: Int): Node = getTile(x, y).nodes(n)

    /**
     * Get the Edge object at the given coordinates
     * @param x The Cartesian x coordinate of the Hex
     * @param y The Cartesian y coordinate of the Hex
     * @param e The edge number on that Hex
     */
    def getEdge(x: Int, y: Int, e: Int): Edge = getTile(x, y).edges(e)

    /**
     * Traverses a road and calculates the length of the longest road touching it.
     * @param edge The edge to start on.  If the Edge has no road, it returns 0.
     * [visitedNodes] Is a list used in the recursion of this method.
     * It should not be used by anyone calling it.
     */
    def longest_road(edge: Edge, visitedNodes: List[Node] = Nil): Int = {
        if (edge.has_road) {
            var color = edge.road.color
            var length = 0
            (edge.nodes.filterNot { visitedNodes.contains(_) }).foreach { n =>
                var max = 0
                n.edges.foreach { e =>
                    if (e != edge && e.has_road && e.road.color == color) {
                        var sub_length = longest_road(e, n :: visitedNodes)
                        if (sub_length > max) max = sub_length
                    }
                }
                length += max
            }
            length + 1
        } else {
            0
        }
    }

    /**
     * Finds the longest road in a specific direction
     * @param initial_direction should be 1 or 0
     * In 99.9% of the time, this method is fast.  But if you have a huge board
     * with 50+ connected roads of the same color, this method could take up to a
     * minute to finish.
     */
    def find_longest_road(edge: Edge, initial_direction: Int = -1, visitedNodes: List[Node] = Nil): Int = {
        if (edge.has_road) {
            var color = edge.road.color
            var nodes_to_visit = edge.nodes
            if (initial_direction != -1)
                nodes_to_visit = List(edge.nodes(initial_direction))

            var length = 0
            (nodes_to_visit.filterNot { visitedNodes.contains(_) }).foreach { n =>
                length += n.edges.map { e =>
                    if (e != edge && e.has_road && e.road.color == color) {
                        find_longest_road(e, -1, n :: visitedNodes)
                    } else {
                        0
                    }
                }.max
            }
            length + 1
        } else {
            //This edge doesn't have a road at all
            0
        }
    }

    /** Does the given player have longest road. */
    def has_longest_road(color: String): Boolean = {
        var maxs = new HashMap[String, Int]()
        //maxs.default = 0

        roadEdges.foreach { e =>
            e.road_lengths.foreach { road_length =>
                var color2 = e.road.color

                if (road_length > maxs.getOrElseUpdate(color2, { 0 }) && road_length >= 5)
                    maxs(color2) = road_length
            }
        }
        var winners: List[String] = maxs.keys.filter { maxs(_) == maxs.values.max }.toList

        /* 
        * only 1 player can have longest road
        * if more than 1 player has the longest road, than neither of them count
        */
        winners.size == 1 && winners(0) == color
    }

    /** Port nodes controlled by the given player. */
    def port_nodes(color:String) = all_nodes.filter { n => n.has_city && n.has_port && n.city.color == color }.toList

    /** Ports controlled by the given player */
    def get_ports(color: String): List[Port] = port_nodes(color).map { _.port }.toList

    /** Gets a list of cards, that the given player should receive */
    def get_cards(number: Int, color: String): List[Card] = {
        var valid_hexes = tiles.values.filter { t =>
            t.number == number && !t.has_bandit && t.card_type != DesertType
        }
        valid_hexes.flatMap { hex =>
            hex.nodes_with_cities(color).flatMap { n =>
                n.city match {
                    case x: Settlement => List(hex.get_card)
                    case x: City => hex.get_2_cards
                }
            }
        }.toList
    }

    /** A helper to wrap edge mutations. */
    private def edge_updater(tileX: Int, tileY: Int, edgeNum: Int, visitor: (Edge) => Unit): Edge = {
        modify_mutex.synchronize[Edge] { () =>
            var t = getTileWithError(tileX, tileY)
            var edge = t.edges(edgeNum)
            visitor.apply(edge)

            // update the longest road markers
            (edge.get_adjecent_edges :+ edge).filter(_.has_road).foreach { e =>
                e.visit_road { e =>
                    e.road_lengths(0) = find_longest_road(e, 0)
                    e.road_lengths(1) = find_longest_road(e, 1)
                }
            }
            edge
        }
    }

    /**
     * This method mutates the board by placing a road on it.
     * NOTE: this method doesn't to any validation based upon rules.  It just modifies the board.
     */
    def place_road(road: Road, tileX: Int, tileY: Int, edgeNum: Int): Edge = {
        edge_updater(tileX, tileY, edgeNum, { edge =>
            edge.road = road
            roadEdges += edge
        })
    }

    def remove_road(tileX: Int, tileY: Int, edgeNum: Int): Edge = {
        edge_updater(tileX, tileY, edgeNum, { edge =>
            if (!edge.has_road) throw new IllegalStateException("Edge does not have a road on it")
            var adjecentEdges = edge.get_adjecent_edges.filter { _.has_road }
            edge.road = null
            roadEdges -= edge
        })
    }

    def enforce_bandit {
        tiles.values.foreach { _.has_bandit = false }
        var desert = tiles.values.find { _.card_type == DesertType }
        if (desert != None) {
            desert.get.has_bandit = true
        } else {
            log.warn("Could not find desert tile, placing bandit on first hex")
            tiles.values.head.has_bandit = true
        }
    }

    /**
     * Called ONLY by a Turn object, this method mutates the board by placing a
     * City or Settlement on it.
     */
    def place_city(city: City, x: Int, y: Int, nodeNum: Int): Node = {
        modify_mutex.synchronize[Node] { () =>
            var node = getTileWithError(x, y).nodes(nodeNum)
            node.city = city
            node
        }
    }

    /**
     * Move the bandit to a new hex
     * @return the old hex that the bandit was on.
     */
    def move_bandit(new_hex: Hex): Hex = {
        modify_mutex.synchronize[Hex] { () =>
            if (new_hex == null)
                throw new IllegalArgumentException("Cannot move bandit to null hex")
            var current_bandit_hex = tiles.values.find { _.has_bandit }
            if (current_bandit_hex == None)
                throw new RuleException("Board does not currently have a bandit *{self}")
            if (current_bandit_hex.get == new_hex)
                throw new RuleException("Bandit cannot be moved to the Tile it's already on")
            var local_tile = tiles.values.find { _.coords == new_hex.coords }
            if (local_tile.isDefined) {
                local_tile.get.has_bandit = true
                current_bandit_hex.get.has_bandit = false
                return current_bandit_hex.get
            } else {
                throw new IllegalArgumentException("New Hex was not found on the Board:" + new_hex)
            }
        }
    }

    /**
     * Gets a list of Nodes that settlements can be placed on.
     * [roadConstraint] A boolean that ensures that settlements can only be placed
     * on pre-existing roads. For SetupTurns, this should be false.
     * (Players don't need to connect settlements to roads during setup. )
     * [roadColor] The color of the road to constrain against.  For a normal turn,
     * you need to ask which spots are valid for your player's color.
     * returns a list of Node objects
     */
    def get_valid_settlement_spots(roadConstraint: Boolean, roadColor: String): List[Node] = {
        modify_mutex.synchronize[List[Node]] { () =>
            if (roadColor == null) throw new IllegalArgumentException("roadColor cannot be null")
            all_nodes.filter { n =>
                // make sure there are no cities in the adjecent nodes
                var is2AwayFromCities: Boolean = n.get_adjecent_nodes.find { _.has_city }.isEmpty

                if (roadConstraint) {
                    var hasAdjecentRoad = n.edges.find { e => e.has_road && e.road.color == roadColor }.isDefined
                    hasAdjecentRoad && is2AwayFromCities && !n.has_city
                } else {
                    is2AwayFromCities && !n.has_city
                }
            }.toSet.toList
        }
    }

    /**
     * Get all the valid places to put a road.
     * NOTE: if someone else builds a settlement, you can't build a road through it.
     * @param touching_node If specified, valid road spots MUST touch this node.
     * This is used during setup when players can only place a road touching the
     * settlement they just placed.
     */
    def get_valid_road_spots(road_color: String, touching_node: Node = null): List[Edge] = {
        modify_mutex.synchronize[List[Edge]] { () =>
            if (road_color == null) throw new IllegalArgumentException("road_color cannot be null")
            var result: List[Edge] = Nil
            all_nodes.foreach { n =>
                if (n.has_city && n.city.color == road_color) {
                    result = result ++ n.edges.filterNot { _.has_road }
                } else if (!n.has_city) {
                    n.edges.foreach { edge =>
                        if (edge.has_road && edge.road.color == road_color) {
                            result = result ++ n.edges.filterNot { _.has_road }
                        }
                    }
                }
            }
            if (touching_node != null) result = result.filter { _.nodes.contains(touching_node) }
            result.distinct
        }
    }

    /** Get all the valid spots to place a city. */
    def get_valid_city_spots(color: String): List[Node] = {
        modify_mutex.synchronize[List[Node]] { () =>
            if (color == null) throw new IllegalArgumentException("color cannot be null")
            all_nodes.filter { node =>
                node.has_city &&
                    node.city.getClass == classOf[Settlement] &&
                    node.city.color == color
            }.toList
        }
    }

    /** this will traverse the board in a counter-clockwise spiral like fashion */
    def spiral_tile_iterator(visitor: (Hex => Unit)): Unit = {
        // first, pick a random edge tile
        var visited_hexes: List[Hex] = Nil
        var non_desert_tiles = tiles.values.filterNot { _.card_type == DesertType }.toList
        var starting_edge_tiles = non_desert_tiles.filter { _.is_on_edge }
        var starting_tile = starting_edge_tiles.pick_random()

        var starting_edge = starting_tile.get_clockwise_connecting_edge
        var starting_edge_num = starting_tile.edges.indexOf(starting_edge)
        if (starting_edge_num == -1)
            throw new IllegalStateException("starting_edge_num cannot be -1")

        try {
            // then, start traversing the board    
            def iterate(tile: Hex, edge: Int) {
                if (tile != null) {
                    visitor.apply(tile)
                    visited_hexes = tile :: visited_hexes

                    //We add 1 to the edge, because this makes an attempt to get more-outer tiles.  That way, we don't miss any.
                    var (next_tile, next_edge) = get_next_tile_in_a_spiral(tile, (edge + 1 + 6) % 6, Nil)
                    iterate(next_tile, next_edge)
                }
            }
            iterate(starting_tile, starting_edge_num);
        } catch {
            case e: Exception =>
                throw new Exception("starting tile:" + starting_tile.coords, e)

        }

        //At this point, the only unvisited tiles should be desert tiles
        var desert_count = tiles.values.count { _.card_type != DesertType }
        if (visited_hexes.size != desert_count)
            throw new IllegalStateException("there are " + desert_count + " non deserts and " + visited_hexes.size + " visited hexes")
    }

    /**
     * This will find the next tile in a spiral pattern.
     * @param tile The current tile.
     * @param edge_num The edge indicating the direction of the last tile.
     * @param tried_edges a list containing the numbers of attempted edge.  This is used to prevent infiniate loops.
     */
    def get_next_tile_in_a_spiral(tile: Hex, edge_num: Int, tried_edges: List[Int], recursion_count: Int = 0): (Hex, Int) = {
        if (recursion_count > 50)
            throw new IllegalStateException("Recursion limit reached")

        if (tried_edges.contains(edge_num)) {
            return (null, -1) // We tried all possible edges now && found nothing, so we're done.
        }
        var next_tile = tile.get_opposite_hex(edge_num)
        if (next_tile == null || next_tile.number > 0) {
            var next_edge_num = (edge_num - 1 + 6) % 6
            return get_next_tile_in_a_spiral(tile, next_edge_num, edge_num :: tried_edges, recursion_count + 1)
        } else {
            if (next_tile.card_type == DesertType) {
                var result = get_next_tile_in_a_spiral(next_tile, edge_num, Nil, recursion_count + 1)
                if (result._1 == null) {
                    var next_edge_num = (edge_num - 1 + 6) % 6
                    result = get_next_tile_in_a_spiral(tile, next_edge_num, edge_num :: tried_edges, recursion_count + 1)
                }
                return result
            } else {
                return (next_tile, edge_num)
            }
        }
    }

    /**
     * Add a new hex to the board.  We assume that the board is already in a correct state where there are not duplicate edges or nodes.
     */
    def add_hex(hex: Hex) {
        if (tiles.contains(hex.coords))
            throw new IllegalArgumentException("The given hex is already on the board:" + hex.coords)
        tiles(hex.coords) = hex
        (0 to 5).foreach { i => hex.edges(i) = null }
        (0 to 5).foreach { i => hex.nodes(i) = null }

        //Take the exising edges 
        for (i <- 0 to 5) {
            var opposing_hex = get_opposite_hex(hex, i)
            if (opposing_hex != null) {
                var opposing_edge = (i + 3) % 6

                //my edge becomes the existing edge
                hex.edges(i) = opposing_hex.edges(opposing_edge)
                hex.edges(i).hexes = hex.edges(i).hexes :+ hex

                //Add the existing nodes to my hex
                hex.nodes(i) = opposing_hex.nodes((opposing_edge + 1) % 6)
                hex.nodes((i + 1) % 6) = opposing_hex.nodes(opposing_edge)
                hex.nodes.filter { _ != null }.foreach { n =>
                    n.hexes = (n.hexes :+ hex).distinct
                }
            }
        }

        //Create new nodes, if they don't exist
        for (i <- 0 to 5) {
            if (hex.nodes(i) == null) {
                hex.nodes(i) = new Node(hex.coords._1, hex.coords._2, i)
                hex.nodes(i).hexes = hex.nodes(i).hexes :+ hex
            }
        }

        //Create new edges, if they don't exist
        for (i <- 0 to 5) {
            if (hex.edges(i) == null) {
                var edge = new Edge(hex.coords._1, hex.coords._2, i)
                hex.edges(i) = edge
                edge.hexes = edge.hexes :+ hex
                var n1 = hex.nodes(i)
                var n2 = hex.nodes((i + 1) % 6)
                edge.nodes = List[Node](n1, n2)
                n1.edges = n1.edges :+ edge
                n2.edges = n2.edges :+ edge
            }
        }

    }

    /**
     * Given a hex and an edge number, this will return the opposite hex, or null if there isn't one.
     */
    def get_opposite_hex(hex: Hex, edge: Int): Hex = {
        var coords = edge match {
            case 0 => hex.up
            case 1 => hex.right_up
            case 2 => hex.right_down
            case 3 => hex.down
            case 4 => hex.left_down
            case 5 => hex.left_up
        }
        getTile(coords._1, coords._2)
    }

    /**
     * Initializes all the sub-class specific data.
     * i.e. name, expansion, tile-locations, tile-numbers etc.
     * Should be overriden by subclass
     */
    def subclass_init(): Unit

    def make_tile_bag(): RandomBag[Hex]

    def make_number_bag(): RandomBag[Int]

    def make_port_bag(): RandomBag[Port]

    /**
     * Randomize any Hexes that need to be taken from the Hex bag
     * Or randomize any hex numbers or ports.
     * i.e. The standard board is all random, so this should randomize the whole board.
     */
    def randomize_board(spiral_numbers: Boolean = true) {
        //Clear any bandits
        tiles.values.foreach { _.has_bandit = false }
        var set_bandit = false

        var tilebag = this.make_tile_bag
        //Randomize any random tiles.
        tiles.values.foreach { t =>
            if (t.isInstanceOf[RandomHexFromBag]) {
                var temp_hex: Hex = tilebag.grab
                t.card_type = temp_hex.card_type

                //Set the first desert hex to have the bandit.
                if (t.card_type == DesertType && !set_bandit) {
                    t.has_bandit = true
                    set_bandit = false
                }
            }
        }

        var number_bag = this.make_number_bag
        tiles.values.foreach { _.number = 0 }
        //Randomize the Numbers
        if (spiral_numbers) {
            spiral_tile_iterator { tile =>
                if (tile.card_type != DesertType)
                    tile.number = number_bag.next
            }
        } else {
            tiles.values.filterNot { _.card_type == DesertType }.foreach { tile =>
                tile.number = number_bag.grab
            }
        }

        var port_bag = this.make_port_bag
        // Randomize the ports
        all_edges.foreach { e =>
            if (!e.nodes.isEmpty && e.nodes.forall { _.has_port }) {
                var port = e.nodes(0).port
                if (port.isInstanceOf[RandomPortFromBag]) {
                    port = port_bag.grab
                    e.nodes.foreach { n =>
                        n.port.kind = port.kind
                        n.port.rate = port.rate
                    }
                }
            }
        }
        enforce_bandit
    }

}

class BoardCopy(board: Board) extends Board(false, false) {
    this.roadEdges ++ board.roadEdges
    this.card_pile = board.card_pile

    def subclass_init() = {
        board.tiles.keys.foreach { k =>
            this.tiles.put(k, board.tiles(k))
        }
    }
    def make_port_bag() = { null }
    def make_number_bag() = { null }
    def make_tile_bag() = { null }

}

/*
  class Board
    attr_accessor :modify_mutex
    *The game definition that this board applies to
    attr_reader :expansion
    *The number of recomended players for this board. (Range){
    attr_reader :recomended_players
    *The human readable name for this board
    attr_reader :name
    *The RandomBag of Hex tiles.
*/
