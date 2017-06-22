package server

import org.jjk3.board.Board
import org.jjk3.board.pickRandom
import org.jjk3.board.remove_random
import org.jjk3.bots.Bot
import org.jjk3.bots.SinglePurchasePlayer
import org.jjk3.gameplay.Admin
import org.jjk3.gameplay.Turn
import org.jjk3.player.*
import java.io.Serializable
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException


object SettlersExecutor {
    val executor = Executors.newFixedThreadPool(50)
}

class Message(val message: String, val sender: PlayerReference?) : Serializable
class PublicAdmin(board: Board, max_players: Int, max_points: Int = 10, turn_timeout_seconds: Int = 240,
                  game_timeout: Int = 1800) : Admin(board, max_players, max_points, turn_timeout_seconds,
        game_timeout) {

    private fun isBot(p: Player) = p is Bot || (p is TrustedPlayer && p.original is Bot)
    private fun bots() = players.filter { isBot(it) }
    override fun register(registrant: Player): Unit {
        //Players can only register before the game starts or if there are bots playing.
        var taking_over_bot: Player? = null
        val can_join = ! isGameInProgress() || bots().isNotEmpty()
        if (can_join) {
            //Wrap the player in a trusted version of a player.  This makes sure that there is a local copy keeping track of points, pieces, etc.
            val trustedPlayer = TrustedPlayer(registrant)
            //if (initial_player.isInstanceOf<ProxyObject>){
            //	initial_player.json_connection.player = registrant
            //}
            trustedPlayer.updateBoard(board)

            //Assign a color
            if (isGameWaiting()) {
                if (registrant is HasColorPreference) {
                    val preferred_color = registrant.preferredColor
                    if (preferred_color != null && available_colors.contains(preferred_color)) {
                        trustedPlayer.color = preferred_color
                        available_colors -= preferred_color
                    }
                }
                if (trustedPlayer.color == null) {
                    val (chosen_color, _available_colors) = available_colors.remove_random()
                    available_colors = _available_colors
                    trustedPlayer.color = chosen_color
                }
            } else {
                val bot_colors = bots().map { it.color }
                if (registrant is HasColorPreference) {
                    val preferred_color = registrant.preferredColor
                    if (preferred_color != null && bot_colors.contains(preferred_color)) {
                        taking_over_bot = getPlayer(preferred_color)
                        trustedPlayer.color = preferred_color
                    }
                }
                if (trustedPlayer.color == null) {
                    val color = bot_colors.pickRandom()!!
                    trustedPlayer.color = color
                    taking_over_bot = getPlayer(color)
                }
            }
            //tell the player how many pieces they have
            /*registrant.copy_pieces_left.forEach { (entry) ->
                initial_player.addPiecesLeft(entry._1, entry._2)
            }*/

            if (taking_over_bot != null) {
                //TODO replace_player(taking_over_bot, registrant)
                players.forEach { p ->
                    trustedPlayer.playerJoined(p.ref())
                } //Tell the  player about the other players
            } else {
                players.forEach { p ->
                    trustedPlayer.playerJoined(p.ref())
                } //Tell the  player about the other players
                registerObserver(trustedPlayer)
                players += trustedPlayer
                //tell the  player about all the other players
                send_observer_msg { it.playerJoined(trustedPlayer.ref()) }
            }
            log.info("Player joined: $trustedPlayer")
            if (players.size == max_players) {
                //game_mutex.synchronize { () ->
                val future = playGame()
                //}
                return
            }
        } else {
            log.info("Player cannot join the game at this time")
        }
    }

    /** This is called by the client to easily plus bots */
    fun add_bot(name: String, last_name: String = "") {
        if (! isGameInProgress()) {
            val bot: Bot = SinglePurchasePlayer(this)
            bot.delay = 2
            register(bot)
        }
    }

    fun shutdown() {
        game_future?.cancel(true)
        all_futures.forEach { f ->
            f.cancel(true)
        }
    }

    override fun giveTurn(turn: Turn, player: Player): Turn {
        try {
            super.giveTurn(turn, player)
        } catch (err: TimeoutException) {
            val skipped = times_skipped.incrementAndGet(player.color)
            log.error("Player's Turn Timed-out. turn:$turn Time skipped:$skipped player:$player", err)
            /*admin_msg(player.fullName() + " took too long.  A bot is filling in.")*/
            if (skipped == 3L) {
                kickOut(player, IllegalStateException("Your turn was skipped too many times"))
            } else {
                // TOOD:Finish the bots Make a temporary bot to take over for a turn.
                /*            	val tmpBot = SinglePurchasePlayer.copy(player, "Tempbot","The Robot", self)
        val actingBot =  TrustedPlayer(this,  ActingBot(tmpBot, player), log, player.color, player.piecesLeft(City), player.piecesLeft(Settlement), player.piecesLeft(Road), player.cards, player.getPlayedDevCards)
        currentTurn().player = actingBot
        actingBot.takeTurn(currentTurn, currentTurn().is_setup)
        tmpBot = nil
        actingBot = nil
        */
                kickOut(player, err) //for now
            }
        } catch (err: Exception) {
            try {
                log.error(err, err) //until i implement the kick out
                kickOut(player, err)
            } catch (e: Exception) {
                log.error(e, e)
            }
        }
        return turn
    }

}