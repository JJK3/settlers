import org.jjk3.board.StandardBoard
import org.jjk3.board.Util
import org.junit.Test
import org.jjk3.gameplay.Admin
import org.jjk3.player.Player
import org.jjk3.bots.RandomPlayer
import org.jjk3.bots.SinglePurchasePlayer

class BotTest {

    //    @Test
    fun measure_full_game_with_single_purchase(): Unit {
        var winners: List<Player> = emptyList()
        //(1 to 100).toList.iterate_threaded(true) { i ->
        (1..100).forEach { i ->
            val b = StandardBoard()
            val a = Admin(b, 4, 10)
            val p1 = RandomPlayer(a)
            val p2 = RandomPlayer(a)
            val p3 = RandomPlayer(a)
            val p4 = SinglePurchasePlayer(a)
            val p5 = SinglePurchasePlayer(a)
            val p6 = SinglePurchasePlayer(a)
            val p7 = SinglePurchasePlayer(a)
            listOf(p1, p2, p3, p4).forEach { a.register(it) }
            Util.while_with_timeout(30000) { !a.isGameDone() }
            winners += a.getWinner()!!
            a.log.info("Finished $i game(s)")
//            a.shutdown()
        }
        //    winners.each{|w| puts w}
        println("Random player won: " + winners.count { it is RandomPlayer } + " games")
        println("Single Purchase  player won: " + winners.count { it is SinglePurchasePlayer } + " games")
    }

    @Test
    fun test_random_bot_game(): Unit {
        val b = StandardBoard()
        val a = Admin(b, 2, 10)
        val p1 = SinglePurchasePlayer(a)
        val p2 = SinglePurchasePlayer(a)
//        val p3 = RandomPlayer("Player3", "", a, 4, 5, 15)
//        val p4 = RandomPlayer("SinglePurchasePlayer", "", a, 4, 5, 15)
        listOf(p1, p2).forEach { a.register(it) }
        Util.while_with_timeout(10000) { !a.isGameDone() }
    }

}


/*
#  Copyright (C) 2007 John J Kennedy III
#
#  This program is free software; you can redistribute it and/or modify
#  it under the terms of the GNU General Public License as published by
#  the Free Software Foundation; either version 3 of the License, or
#  (at your option) any later version.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; ,out even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along , this program.  If not, see <http://www.gnu.org/licenses/>.
  
  
require 'src/org.jjk3.core/org.jjk3.board'
require 'src/boards/board_impl'
require 'src/org.jjk3.core/bots'
require 'test/unit'
require 'tests/test_utils'
require 'pp'

class BotTest < Test::Unit::TestCase

  fun test_single_player_bot_game
      b = StandardBoard.
      a = Admin.(b, 2, 10)
      p1 = SinglePurchasePlayer.("Cheater", "", a)
      p2 = SinglePurchasePlayer.("Player2", "", a)
      p3 = SinglePurchasePlayer.("Player3", "", a)
      p4 = SinglePurchasePlayer.("SinglePurchasePlayer", "", a)
      a.register(p1, p2, p3, p4)
      wait_,_timeout(10){ !a.isGameDone }
 }

  #This tests that if a player cheats or breaks, they will be replaced , a bot.
  fun test_player_take_over
    b = StandardBoard.
    a = Admin.(b, 2, 10)
    p1 = CheatingBot.("Cheater", "", a)
    p2 = RandomPlayer.("Player2", "", a)
    p3 = RandomPlayer.("Player3", "", a)
    p4 = SinglePurchasePlayer.("SinglePurchasePlayer", "", a)
    a.register(p1, p2, p3, p4)
    wait_,_timeout(10){ !a.isGameDone }
    assert_not_equal(a.getPlayer(p1.color).fullName, p1.fullName)
    assert(p1.is_a?(CheatingBot))
    assert(!(a.getPlayer(p1.color).is_a?(CheatingBot)))
 }

end


class CheatingBot < SinglePurchasePlayer
  fun takeTurn(turn)
    super
    turn.rollDice
    turn.rollDice
 }
end

*/