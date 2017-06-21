package org.jjk3.bots

import org.jjk3.board.Hex
import org.jjk3.board.Node

object DiceProbabilities {

    /**
     * Probability distribution for dice rolls
     * dice_probs[die value] -> probability
     */
    private val probabilities: Map<Int, Double>

    init {
        var temp = emptyMap<Int, Double>()
        for (x in 1..6) {
            for (y in 1..6) {
                val index = x + y
                val value = temp.getOrElse(index) { 0.0 } + (1.0 / 36.0)
                temp += Pair(index, value)
            }
        }
        probabilities = temp
    }

    /** The probabability for 2 dice to roll the given randomNumber. */
    fun getProbability(number: Int) = probabilities.getOrElse(number) { 0.0 }

    fun getProbability(hex: Hex): Double = getProbability(hex.number)
    /** Gets the sum of hex probabilities touching this node */
    fun getProbability(node: Node): Double = node.hexes.keys.map { getProbability(it) }.sum()

}