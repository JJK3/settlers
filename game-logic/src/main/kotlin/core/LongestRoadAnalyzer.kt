package core

class LongestRoadAnalyzer(val board: Board) {

    private data class LongestRoadResult(val longestRoad: List<Edge>, val examinedEdges: Set<Edge>) {
        fun plus(edge: Edge) = LongestRoadResult(longestRoad + edge, examinedEdges + edge)
    }

    /** Does the given player have longest road. */
    fun hasLongestRoad(color: String): Boolean {
        val allLongestRoads = getLongestRoads()
        val maxes = getMaxes(allLongestRoads) { it.longestRoad.size }
        if (maxes.size != 1) {
            // There can be only one
            return false
        }
        val longestRoad = maxes.first().longestRoad
        return longestRoad.first().road!!.color == color
    }

    fun getLongestRoad(edge: Edge): List<Edge> = getLongestRoadHelper(edge).longestRoad
    /** Get longest road for this edge. */
    private fun getLongestRoadHelper(edge: Edge): LongestRoadResult {
        val empty = LongestRoadResult(emptyList(), emptySet())
        if (!edge.has_road() || !isEndOfRoad(edge)) {
            return empty
        }
        return getLongestRoadHelper(edge, empty)
    }

    private fun getLongestRoadHelper(edge: Edge, existingResult: LongestRoadResult): LongestRoadResult {
        val currentResult = existingResult.plus(edge)
        val edgesToTraverseNext = getNextEdges(edge, existingResult, existingResult.longestRoad.lastOrNull())
        if (edgesToTraverseNext.isEmpty()) {
            return currentResult
        }
        val longestRoads = edgesToTraverseNext.map { getLongestRoadHelper(it, currentResult) }
        return getMaxes(longestRoads) { it.longestRoad.size }.first()
    }

    private fun getNextEdges(edge: Edge, existingResult: LongestRoadResult, previousEdge: Edge?): Set<Edge> {
        var edgesToTraverseNext = previousEdge?.let { edge.getOppositeEdges(it) } ?: edge.get_adjecent_edges()
        edgesToTraverseNext -= existingResult.examinedEdges
        // TODO: Account for a city or settlement that breaks longest road
        edgesToTraverseNext = filterMatchingColor(edgesToTraverseNext, edge).toSet()
        return edgesToTraverseNext
    }

    /** get all LongestRoadResults for the entire board. */
    private fun getLongestRoads(): List<LongestRoadResult> {
        var edgesToExamine = board.edges().filter(Edge::has_road)
        var longestRoads = emptyList<LongestRoadResult>()
        while (edgesToExamine.isNotEmpty()) {
            val longestRoad = getLongestRoadHelper(edgesToExamine.first())
            edgesToExamine -= edgesToExamine.first()
            edgesToExamine -= longestRoad.examinedEdges
            longestRoads += longestRoad
        }
        return longestRoads
    }

    /** Does this edge have an open size (i.e. not connected to a road) ? */
    private fun isEndOfRoad(edge: Edge): Boolean = filterMatchingColor(edge.get_adjecent_edges(), edge).size <= 1

    private fun filterMatchingColor(edges: Iterable<Edge>, edgeToMatch: Edge) =
            edges.filter { it.road?.color == edgeToMatch.road!!.color }

    private fun <T, C : Comparable<C>> getMaxes(items: Iterable<T>, property: (T) -> C): List<T> {
        val max = items.map { property.invoke(it) }.max()
        return items.filter { property.invoke(it) == max }
    }

}