package org.jjk3.gameplay

import java.util.*

interface DiceRoll {
    fun sum(): Int
}

open class NormalDiceRoll : DiceRoll {
    val r: Random = Random()
    open val die1 = r.nextInt(5) + 1
    open val die2 = r.nextInt(5) + 1
    override fun sum() = die1 + die2
}