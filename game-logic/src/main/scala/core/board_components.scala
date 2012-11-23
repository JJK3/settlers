package core
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import scala.collection.mutable.ArraySeq
import scala.util.Random

trait Purchaseable {}

class BoardPiece(var color: String) extends Purchaseable
class City(color: String) extends BoardPiece(color) {
    val points = 2
}
class Settlement(color: String) extends City(color) {
    override val points = 1
}
class Road(color: String) extends BoardPiece(color) {
    override def equals(that: Any): Boolean = {
        that match {
            case o: Road => o != null && this.color == o.color
            case _ => false
        }
    }
}

class Port(var kind: HexType, var rate: Int) {
    override def equals(that: Any): Boolean = {
        that match {
            case o: Port => o != null & o.kind == this.kind && o.rate == this.rate
            case _ => false
        }
    }
}

object HexType {
    val RESOURCE_TYPES = List[Resource](OreType, WoodType, SheepType, BrickType, WheatType)
}
trait HexType {}
trait Resource extends Card with HexType {}
object OreType extends HexType with Resource {}
object WoodType extends HexType with Resource {}
object SheepType extends HexType with Resource {}
object BrickType extends HexType with Resource {}
object WheatType extends HexType with Resource {}
object DesertType extends HexType {}

class RandomHexFromBag extends Hex(null, -1) {}
class RandomPortFromBag extends Port(null, -1) {}

abstract class RandomBag[A] extends {
    var items: List[A] = Nil
    protected val rand = new Random(System.currentTimeMillis());

    def add(item: A): Unit = {
        items = item :: items
    }

    def grab(): A = {
        if (items.size == 0) throw new IllegalStateException("Cannot grab from an empty bag")
        var i = rand.nextInt(items.size)
        pick_and_remove(i)
    }

    def next(): A = {
        pick_and_remove(0)
    }

    def pick_and_remove(i: Int): A = {
        var grabbedItem = items(i)
        val (start, _ :: end) = items.splitAt(i)
        items = start ::: end
        grabbedItem
    }
}

trait Card {

}

/** An Edge is basically just a collection of 2 nodes that belongs to a hex */
class Edge(var x: Int = -1, var y: Int = -1, var edgeNum: Int = -1) {
    var nodes = List[Node]()
    var hexes = List[Hex]()
    var road: Road = null;

    var coords: (Int, Int, Int) = { (x, y, edgeNum) }

    /**
     * A cache of the longest road lengths connected to this edge
     * This is used to speed up the longest road calculations.
     */
    var road_lengths = new Array[Int](2)

    /**
     * Get all the Edges touching this Edge.
     * This will return a List with size bewteen 2 and 4.
     */
    def get_adjecent_edges: List[Edge] = {
        nodes.map { _.edges }.flatten.filter { _ != this }.toList
    }

    def has_road: Boolean = this.road != null

    /**
     * An iterator to visit every connected road with the same color
     * yields the edge containing the road
     */
    def visit_road(visitor: (Edge) => Unit, visitedEdges: HashSet[Edge] = new HashSet[Edge]()): HashSet[Edge] = {
        if (this.has_road) {
            var newVisitedEdges = visitedEdges + this
            visitor.apply(this)
            get_adjecent_edges.foreach { e =>
                if (!newVisitedEdges.contains(e)) {
                    if (e.has_road && this.road.color == e.road.color) {
                        newVisitedEdges ++= e.visit_road(visitor, newVisitedEdges)
                    }
                }
            }
            return newVisitedEdges
        }
        new HashSet[Edge]()
    }

    override def equals(that: Any): Boolean = {
        if (that == null) return false;
        that match {
            case other: Edge => this.coords == other.coords
            case _ => false
        }
    }
    override def toString(): String = "<Edge coords=\"" + coords + "\" />"
}

/** This Corresponds to a node on the board where settlements and cities can be placed. */
class Node(var x: Int = -1, var y: Int = -1, var nodeNum: Int = -1) {
    var edges = List[Edge]()
    var hexes = List[Hex]()
    var city: City = null
    var port: Port = null
    var coords: (Int, Int, Int) = (x, y, nodeNum)

    /** The Array of adjecent nodes */
    def get_adjecent_nodes: Seq[Node] = edges.map { _.nodes }.flatten.filter { _ != this }

    /** Gets the sum of hex probablities touching this node */
    def get_hex_prob: Double = hexes.map { _.get_prob }.sum
    def has_city: Boolean = this.city != null
    def has_port: Boolean = this.port != null

    override def toString: String = "<node coords=\"" + coords + "\" />"

    override def equals(that: Any): Boolean = {
        if (that == null) return false;
        that match {
            case other: Node => this.coords.equals(other.coords)
            case _ => false
        }
    }
}

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
class Hex(val hexType: HexType, var number: Int, var has_bandit: Boolean = false) {
    var card_type = hexType
    val nodes = new Array[Node](6)
    val edges = new Array[Edge](6)
    var coords: (Int, Int) = (-1, -1)

    def get_card(): Resource = {
        card_type match {
            case x: Resource => x
            case DesertType => throw new Exception("Cannot take card from a desert.")
        }
    }
    def get_2_cards(): List[Card] = List(get_card, get_card)

    def up = (coords._1, coords._2 - 1)
    def down = (coords._1, coords._2 + 1)
    def right_up = (coords._1 + 1, (coords._1 % 2).abs + coords._2 - 1)
    def right_down = (coords._1 + 1, (coords._1 % 2).abs + coords._2)
    def left_up = (coords._1 - 1, (coords._1 % 2).abs + coords._2 - 1)
    def left_down = (coords._1 - 1, (coords._1 % 2).abs + coords._2)

    def nodes_with_cities(color: String = null) = nodes.filter { n =>
        n.has_city && (color == null || n.city.color == color)
    }

    /** is this hex on the edge or the map? */
    def is_on_edge: Boolean = edges.exists { _.hexes.size == 1 }

    /** get the hex on the opposite side of the given edge. */
    def get_opposite_hex(edge_num: Int): Hex = {
        var opposite_edge: Int = ((edge_num + 3) % 6).abs
        return edges(opposite_edge).hexes.find { _ != this }.getOrElse(null)
    }

    /** on an edge tile, this will get the edge number that connects this tile to the next edge tile */
    def get_connecting_edge_num: Int = {
        edges.foreach { e =>
            var adj_edges = e.get_adjecent_edges
            if (adj_edges.size == 4) {
                val edges_with_2_hexes = adj_edges.count { _.hexes.size > 1 }
                if (edges_with_2_hexes == 2) {
                    return edges.indexOf(e)
                }
            }
        }
        throw new Exception("This is not an edge tile")
    }

    /** given a hex, this will return the edge number of the edge touching the next clockwise edge */
    def get_clockwise_connecting_edge(): Edge = {
        var outside_edges = edges.filter { _.hexes.size == 1 }
        var most_clockwise_edge = outside_edges(0)
        var next_edge: Edge = most_clockwise_edge
        while (next_edge.hexes.size == 1) {
            var next_edge_index: Int = (edges.indexOf(next_edge) + 1) % 6
            next_edge = edges(next_edge_index)
        }
        return next_edge
    }

    /** get the probability for this hex's number */
    def get_prob: Double = Hex.dice_probs(number)

    override def toString(): String = {
        if (coords == null) {
            "<Hex type=\"" + card_type + "\" number=\"" + number + "\" />"
        } else {
            "<Hex type=\"" + card_type + "\" number=\"" + number + "\" coords=\"" + coords + "\" />"
        }
    }

    override def equals(that: Any): Boolean = {
        that match {
            case other: Hex => other != null && this.coords == other.coords
            case _ => false
        }
    }
}

object Hex {
    /**
     * Probability distribution for dice rolls
     * dice_probs[die value] = probability
     */
    private var temp_dice_probs: Map[Int, Double] = Map().withDefaultValue(0)
    for (x <- 1 to 6) {
        for (y <- 1 to 6) {
            val new_value = temp_dice_probs(x + y) + (1.0 / 36.0)
            temp_dice_probs += (x + y -> new_value)
        }
    }
    val dice_probs = temp_dice_probs
}
