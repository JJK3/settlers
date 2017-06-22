package org.jjk3.gameplay.actions

import org.apache.log4j.Logger
import org.jjk3.board.*
import org.jjk3.gameplay.Turn

abstract class PlayDevelopmentCardAction(val card: DevelopmentCard) : TurnAction<Unit>() {

    companion object {
        val log: Logger = Logger.getLogger(PlayDevelopmentCardAction::class.java)
    }

    override fun run() {
        assertCanPlayeDevelopmentCard(card)
        use()
        player.takeCards(listOf(card), Turn.ReasonToTakeCards.Other)
        player.playedDevCard(card)
        admin.checkForWinner()
    }

    abstract fun use()
    protected open fun assertCanPlayeDevelopmentCard(card: DevelopmentCard) {
        assertRule(! admin.isGameDone(), "Game is already over")
        assertRule(player.cards.contains(card),
                "Player does not own the card being played. cards:" + player.cards)
        assertRule(! turn.isDone(), "Turn is done")
        assertRule(card is SoldierCard || turn.hasRolled(),
                "$card played before dice were rolled. Current State: $turn.state")
    }
}

class PlaySoldierCardAction(card: SoldierCard, val newBanditLocation: HexCoordinate) : PlayDevelopmentCardAction(card) {
    override fun use() {
        // TODO: should the player that you take the card from, also be a constructor arg? a nullable Player?
        moveBandit(newBanditLocation)
    }
}

class PlayRoadBuildingCardAction(card: RoadBuildingCard) : PlayDevelopmentCardAction(card) {
    override fun use() {
        turn.player.giveFreeRoads(2)
        log.debug("Giving 2 roads to ${turn.player}: ${turn.player.freeRoads()}")
    }
}

class PlayResourceMonopolyCardAction(card: ResourceMonopolyCard, val resource: Resource) : PlayDevelopmentCardAction(
        card) {
    override fun use() {
        turn.admin.otherPlayers(turn.player.ref()).forEach { p ->
            val cards: List<ResourceCard> = (1..p.countResources(resource)).map { ResourceCard(resource) }
            if (cards.isNotEmpty()) {
                p.takeCards(cards, Turn.ReasonToTakeCards.Other)
                turn.player.giveCards(cards)
            }
        }
    }
}

class PlayYearOfPlentyCardAction(card: YearOfPlentyCard, val resource: Resource) : PlayDevelopmentCardAction(card) {
    override fun use() {
        turn.player.giveCards((1..2).map { ResourceCard(resource) })
    }
}

class PlayVictoryPointCardAction(card: VictoryPointCard) : PlayDevelopmentCardAction(card) {
    override fun use() {

    }
}