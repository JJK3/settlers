package server

import org.jjk3.core.Board
import org.jjk3.core.pick_random
import org.jjk3.core.remove_random
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

    private fun isBot(p: Player) = p is Bot || (p is TrustedPlayer && p.original_player is Bot)
    private fun bots() = players.filter { isBot(it) }
    override fun register(registrant: Player): Unit {
        //Players can only register before the game starts or if there are bots playing.
        var taking_over_bot: Player? = null
        val can_join = !is_game_in_progress() || bots().isNotEmpty()
        if (can_join) {
            //Wrap the org.jjk3.player in a trusted version of a org.jjk3.player.  This makes sure that there is a local copy keeping track of points, pieces, etc.
            val registrant = TrustedPlayer(registrant, 4, 5, 15)
            //if (initial_player.isInstanceOf<ProxyObject>){
            //	initial_player.json_connection.org.jjk3.player = registrant
            //}
            registrant.board = board
            val preferred_color = registrant.preferred_color //Only ask the org.jjk3.player once

            //Assign a color
            if (is_game_waiting()) {
                if (preferred_color != null && available_colors.contains(preferred_color)) {
                    registrant.color = preferred_color
                    available_colors -= preferred_color
                } else {
                    //instead of being random, users should have a choice here
                    val (chosen_color, _available_colors) = available_colors.remove_random()
                    available_colors = _available_colors
                    registrant.color = chosen_color
                }
            } else {
                val bot_colors = bots().map { it.color }
                if (preferred_color != null && bot_colors.contains(preferred_color)) {
                    taking_over_bot = getPlayer(preferred_color)
                    registrant.color = preferred_color
                } else {
                    val color = bot_colors.pick_random()
                    registrant.color = color
                    taking_over_bot = getPlayer(color)
                }
            }
            //tell the org.jjk3.player how many pieces they have
            /*registrant.copy_pieces_left.forEach { (entry) ->
                initial_player.addPiecesLeft(entry._1, entry._2)
            }*/
            registrant.update_board(board)

            if (taking_over_bot != null) {
                //TODO replace_player(taking_over_bot, registrant)
                players.forEach { p ->
                    registrant.player_joined(p.info())
                } //Tell the  org.jjk3.player about the other players
            } else {
                players.forEach { p ->
                    registrant.player_joined(p.info())
                } //Tell the  org.jjk3.player about the other players
                registerObserver(registrant)
                players += registrant
                //tell the  org.jjk3.player about all the other players
                send_observer_msg { it.player_joined(registrant.info()) }
            }
            log.info("Player joined: $registrant")
            if (players.size == max_players) {
                //game_mutex.synchronize { () ->
                val future = play_game()
                //}
                return
            }
        } else {
            log.info("Player cannot join the game at this time")
        }
    }

    /** This is called by the client to easily plus bots */
    fun add_bot(name: String, last_name: String = "") {
        if (!is_game_in_progress()) {
            val bot: Bot = SinglePurchasePlayer(name, last_name, this)
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

    override fun give_turn(turn: Turn, player: Player): Turn {
        try {
            super.give_turn(turn, player)
        } catch (err: TimeoutException) {
            val skipped = times_skipped.incrementAndGet(player.color)
            log.error("Player's Turn Timed-out. turn:$turn Time skipped:$skipped org.jjk3.player:$player", err)
            /*admin_msg(org.jjk3.player.full_name() + " took too long.  A bot is filling in.")*/
            if (skipped == 3L) {
                kickOut(player, IllegalStateException("Your turn was skipped too many times"))
            } else {
                // TOOD:Finish the bots Make a temporary bot to take over for a turn.
                /*            	val tmpBot = SinglePurchasePlayer.copy(org.jjk3.player, "Tempbot","The Robot", self)
        val actingBot =  TrustedPlayer(this,  ActingBot(tmpBot, org.jjk3.player), log, org.jjk3.player.color, org.jjk3.player.piecesLeft(City), org.jjk3.player.piecesLeft(Settlement), org.jjk3.player.piecesLeft(Road), org.jjk3.player.cards, org.jjk3.player.get_played_dev_cards)
        currentTurn().org.jjk3.player = actingBot
        actingBot.take_turn(currentTurn, currentTurn().is_setup)
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