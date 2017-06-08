package org.jjk3.player

import org.jjk3.core.Resource

/**
 * Represents a quote for trading cards
 * This is basically the Bidder saying "I'll give you giveType for receiveType"
 * The Trader then accepts the quote afterwards
 */
class Quote(
        val bidder: PlayerReference?,
        val receiveType: Resource,
        val receiveNum: Int,
        val giveType: Resource,
        val giveNum: Int) {

    override fun toString() = "[Quote $receiveNum $receiveType for $giveNum $giveType from $bidder]"
    fun validate(admin: Admin): Unit {
        if (bidder != null) {
            val player = admin.getPlayer(bidder.color)!!
            if (player.countResources(giveType) < giveNum) {
                throw  IllegalStateException("Bidder $bidder does not have enough resources for this quote:${this} " +
                        "Bidder cards:${player.cards}")
            }
        }
    }

    /** Is another quote a better deal? (And also the same resources) */
    fun isBetterQuote(other: Quote): Boolean = other.receiveNum < this.receiveNum &&
            other.giveType == this.giveType && other.receiveType == this.receiveType

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as Quote

        if (bidder != other.bidder) return false
        if (receiveType != other.receiveType) return false
        if (receiveNum != other.receiveNum) return false
        if (giveType != other.giveType) return false
        if (giveNum != other.giveNum) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bidder?.hashCode() ?: 0
        result = 31 * result + receiveType.hashCode()
        result = 31 * result + receiveNum
        result = 31 * result + giveType.hashCode()
        result = 31 * result + giveNum
        return result
    }

}