package org.jjk3.board

class LongestRoadDetector(val board: Board) {

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
        return longestRoad.first().road !!.color == color
    }

    fun getLongestRoad(edge: Edge): List<Edge> = getLongestRoadHelper(edge).longestRoad
    /** Get longest road for this edge. */
    private fun getLongestRoadHelper(edge: Edge): LongestRoadResult {
        val empty = LongestRoadResult(emptyList(), emptySet())
        if (! edge.hasRoad()) {
            return empty
        }
        val endsOfRoad = getRoadEnds(edge)
        val results = endsOfRoad.map { getLongestRoadHelper(it, empty) }
        return results.maxBy { it.longestRoad.size } ?: getLongestRoadHelper(edge, empty)
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
        var edgesToTraverseNext = previousEdge?.let { edge.getOppositeEdges(it) } ?: edge.getAdjecentEdges()
        edgesToTraverseNext -= existingResult.examinedEdges
        // TODO: Account for a city or settlement that breaks longest road
        edgesToTraverseNext = filterMatchingColor(edgesToTraverseNext, edge).toSet()
        return edgesToTraverseNext
    }

    /** get all LongestRoadResults for the entire board. */
    private fun getLongestRoads(): List<LongestRoadResult> {
        var edgesToExamine = board.allEdges().filter(Edge::hasRoad)
        var longestRoads = emptyList<LongestRoadResult>()
        while (edgesToExamine.isNotEmpty()) {
            val longestRoad = getLongestRoadHelper(edgesToExamine.first())
            edgesToExamine -= edgesToExamine.first()
            edgesToExamine -= longestRoad.examinedEdges
            longestRoads += longestRoad
        }
        return longestRoads
    }

    /** Find all the ends of the road. */
    private fun getRoadEnds(edge: Edge) = edge.getCompleteRoad().filter(Edge::isEndOfRoad)

    private fun filterMatchingColor(edges: Iterable<Edge>, edgeToMatch: Edge) =
            edges.filter { it.road?.color == edgeToMatch.road !!.color }

    private fun <T, C : Comparable<C>> getMaxes(items: Iterable<T>, property: (T) -> C): List<T> {
        val max = items.map { property.invoke(it) }.max()
        return items.filter { property.invoke(it) == max }
    }

}