package org.jjk3.gameplay.actions

import org.jjk3.board.*
import org.jjk3.gameplay.Admin
import org.jjk3.gameplay.Turn
import org.jjk3.gameplay.TurnState
import org.jjk3.player.GameObserver
import org.jjk3.player.Player
import org.jjk3.player.PlayerReference

abstract class TurnAction<Result> {

    var result: Result? = null
    @Transient lateinit var admin: Admin
    @Transient lateinit var turn: Turn
    @Transient lateinit var board: Board
    @Transient lateinit var player: Player
    fun run(admin: Admin, turn: Turn, board: Board, player: Player): Result {
        this.admin = admin
        this.turn = turn
        this.board = board
        this.player = player
        val result = run()
        this.result = result
        return result
    }

    abstract fun run(): Result
    fun observers(consumer: (GameObserver) -> Unit) = admin.observers.forEach { consumer.invoke(it) }
    /**
     * Makes a player pay for a piece
     * Throws an exception if the player doesn't have enough cards,
     * but doesn't mutate the player if an exception is thrown.
     */
    open fun payFor(piece: Purchaseable): Unit {
        val reason = when (piece) {
            is Settlement -> Turn.ReasonToTakeCards.PurchasedSettlement
            is City -> Turn.ReasonToTakeCards.PurchasedCity
            is Road -> Turn.ReasonToTakeCards.PurchasedRoad
            is DevelopmentCard -> Turn.ReasonToTakeCards.PurchasedDevelopmentCard
            else -> throw IllegalArgumentException("Unknown piece: $piece")
        }
        player.takeCards(piece.price.map(::ResourceCard), reason)
    }

    fun assertRule(condition: Boolean, errorMsg: String) {
        if (! condition) {
            breakRule(errorMsg)
        }
    }

    /** The user has broken a rule.  They going to be kicked out */
    fun breakRule(msg: String) {
        val error = RuleException(msg)
        turn.state = TurnState.DoneWithError
        throw error
    }

    fun assertGameIsNotDone() {
        if (admin.isGameDone()) {
            breakRule("Game is already over")
        }
    }

    /** A helper method to get a player based on a color */
    fun getPlayer(color: String) = admin.getPlayer(color) ?: throw IllegalArgumentException("Unknown player: $color")

    /**
     * Move the bandit to a new tile.
     */
    fun moveBandit(newLocation: HexCoordinate) {
        //TODO: implement rule checking here so people can't move the bandit whenever they want.
        board.moveBandit(newLocation)
        observers { it.playerMovedBandit(player.ref(), newLocation) }
        // Take a card from a player the colors of the cities touching the tile
        val touchingColors = board.getHex(newLocation).nodes.map { it.city?.color }.filterNotNull().distinct()
        val touchingPlayers = touchingColors.map { getPlayer(it) }.filterNot { it == player }.toList().map(Player::ref)
        if (touchingPlayers.isNotEmpty()) {
            val playerToTakeFrom: PlayerReference = touchingPlayers.firstOrNull() ?: player.selectPlayer(
                    touchingPlayers, 1)
            takeRandomCard(playerToTakeFrom)
            observers { it.playerStoleCard(player.ref(), playerToTakeFrom, 1) }
        }
    }

    /**
     * Take a random card from another player and plus it to your own cards
     * If player has no cards, do nothing
     */
    private fun takeRandomCard(victimRef: PlayerReference): Unit {
        val victim = getPlayer(victimRef.color)
        if (victim.resourceCards().isEmpty()) {
            Turn.log.debug("Could not take a random card from $victimRef")
        } else {
            val resource = listOf(victim.resourceCards().pickRandom())
            victim.takeCards(resource, Turn.ReasonToTakeCards.MovedBandit)
            player.giveCards(resource)
        }
    }

}