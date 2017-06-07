package org.jjk3.player

import org.jjk3.core.Hex
import org.jjk3.core.Node
import java.util.*

object Dice {

    private val r: Random = Random()

    /**
     * Probability distribution for dice rolls
     * dice_probs[die value] -> probability
     */
    private val probabilities: Map<Int, Double>

    init {
        var temp_dice_probs = emptyMap<Int, Double>()
        for (x in 1..6) {
            for (y in 1..6) {
                val index = x + y
                val value = temp_dice_probs.getOrElse(index) { 0.0 } + (1.0 / 36.0)
                temp_dice_probs += Pair(index, value)
            }
        }
        probabilities = temp_dice_probs
    }

    /** The probabability for 2 dice to roll the given randomNumber. */
    fun getProbability(number: Int) = probabilities.getOrElse(number) { 0.0 }

    fun getProbability(hex: Hex): Double = getProbability(hex.number)
    /** Gets the sum of hex probabilities touching this node */
    fun getProbability(node: Node): Double = node.hexes.keys.map { getProbability(it) }.sum()

    fun roll(): Pair<Int, Int> = Pair(r.nextInt(5) + 1, r.nextInt(5) + 1)
}