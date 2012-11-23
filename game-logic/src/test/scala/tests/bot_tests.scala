import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.AssertionsForJUnit
import scala.collection.mutable.ListBuffer
import org.junit.Assert._
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.apache.log4j._

import core._
import board._
import UtilList._

class BotTest extends AssertionsForJUnit {

    @Test
    def measure_full_game_with_single_purchase: Unit = {
        var winners: List[Player] = Nil
        //(1 to 100).toList.iterate_threaded(true) { i =>
        (1 to 100).foreach { i =>
            val b = new StandardBoard()
            val a = new Admin(b, 4, 10)
            val p1 = new RandomPlayer("Player1", "", a, a.log, 4, 5, 15, b)
            val p2 = new RandomPlayer("Player2", "", a, a.log, 4, 5, 15, b)
            val p3 = new RandomPlayer("Player3", "", a, a.log, 4, 5, 15, b)
            val p4 = new SinglePurchasePlayer("SinglePurchasePlayer", "", a, a.log, 4, 5, 15, b)
            val p5 = new SinglePurchasePlayer("SinglePurchasePlayer2", "", a, a.log, 4, 5, 15, b)
            val p6 = new SinglePurchasePlayer("SinglePurchasePlayer3", "", a, a.log, 4, 5, 15, b)
            val p7 = new SinglePurchasePlayer("SinglePurchasePlayer4", "", a, a.log, 4, 5, 15, b)
            a.register(p5, p4, p6, p7)
            Util.while_with_timeout(30000) { () => !a.is_game_done }
            winners = winners :+ a.has_winner
            a.log.info("Finished " + i + " game(s)");
            a.shutdown();
        }
        //    winners.each{|w| puts w}
        println("Random player won: " + winners.count { _.isInstanceOf[RandomPlayer] } + " games")
        println("Single Purchase  player won: " + winners.count { _.isInstanceOf[SinglePurchasePlayer] } + " games")
    }

    //@Test
    def test_random_bot_game: Unit = {
        val b = new StandardBoard()
        val a = new Admin(b, 2, 10)
        val p1 = new RandomPlayer("Cheater", "", a, a.log, 4, 5, 15, b)
        val p2 = new RandomPlayer("Player2", "", a, a.log, 4, 5, 15, b)
        val p3 = new RandomPlayer("Player3", "", a, a.log, 4, 5, 15, b)
        val p4 = new RandomPlayer("SinglePurchasePlayer", "", a, a.log, 4, 5, 15, b)
        a.register(p1, p2, p3, p4)
        Util.while_with_timeout(10) { () => a.is_game_done }
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
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with this program.  If not, see <http://www.gnu.org/licenses/>.
  
  
require 'src/core/board'
require 'src/boards/board_impl'
require 'src/core/bots'
require 'test/unit'
require 'tests/test_utils'
require 'pp'

class BotTest < Test::Unit::TestCase

  def test_single_player_bot_game
      b = StandardBoard.new
      a = Admin.new(b, 2, 10)
      p1 = SinglePurchasePlayer.new("Cheater", "", a)
      p2 = SinglePurchasePlayer.new("Player2", "", a)
      p3 = SinglePurchasePlayer.new("Player3", "", a)
      p4 = SinglePurchasePlayer.new("SinglePurchasePlayer", "", a)
      a.register(p1, p2, p3, p4)
      wait_with_timeout(10){ !a.is_game_done }
 }

  #This tests that if a player cheats or breaks, they will be replaced with a bot.
  def test_player_take_over
    b = StandardBoard.new
    a = Admin.new(b, 2, 10)
    p1 = CheatingBot.new("Cheater", "", a)
    p2 = RandomPlayer.new("Player2", "", a)
    p3 = RandomPlayer.new("Player3", "", a)
    p4 = SinglePurchasePlayer.new("SinglePurchasePlayer", "", a)
    a.register(p1, p2, p3, p4)
    wait_with_timeout(10){ !a.is_game_done }
    assert_not_equal(a.get_player(p1.color).full_name, p1.full_name)
    assert(p1.is_a?(CheatingBot))
    assert(!(a.get_player(p1.color).is_a?(CheatingBot)))
 }

end


class CheatingBot < SinglePurchasePlayer
  def take_turn(turn)
    super
    turn.roll_dice
    turn.roll_dice
 }
end

*/