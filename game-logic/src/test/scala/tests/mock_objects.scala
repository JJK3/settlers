import core._
import org.apache.log4j._
import core.UtilList._
import java.util.concurrent._

class MockPlayer(
    first_name: String,
    last_name: String,
    admin: MockAdmin,
    log: Logger = null,
    cities: Int,
    settlements: Int,
    roads: Int,
    board: Board) extends Player(first_name, last_name, admin, log, cities, settlements, roads, board) {

    //    include CallCounter
    //extend CallCounter::ClassMethods
    //    attr_accessor :move_bandit_to, :select_resources_num, :select_nil_resources, :should_offer, :preferred_color
    //    watch(:game_end, :add_cards, :player_rolled, :move_bandit)

    var move_bandit_to: Hex = null

    //Tell the player to only select a set number of resources    
    var select_resources_num: Int = -1

    //Tell the player to select a specific set of resources, or nil
    var select_nil_resources = false

    //used to tell this player to offer specific quotes
    var should_offer: List[Quote] = Nil

    //   should_receive(:prices, :inform, :update_board, :placed_road, :placed_settlement, :placed_city)
    //   should_receive(:take_turn, :get_user_quotes, :color=)
    //   should_receive(:add_cards, :del_cards, :can_afford?).and_return(true)

    //var next_turns: List[((Turn) => Unit, Map[String, Object])] = Nil
    var next_turns: List[(CallableTurn, Future[Unit])] = Nil

    def on_next_turn(func: (Turn) => Unit, params: Map[String, Object] = null): Future[Unit] = {
        val callable = new CallableTurn(params) {
            def call(): Unit = {
                try {
                    this.synchronized {
                        wait(3000)
                        func(turn)
                    }
                } catch {
                    case e =>
                        log.error(e, e)
                        throw e
                }
            }
        }
        val future = SettlersExecutor.executor.submit(callable)
        next_turns = next_turns :+ (callable, future)
        future
    }

    abstract class CallableTurn(val params: Map[String, Object]) extends Callable[Unit] {
        var turn: Turn = null
    }

    /*   def wait_for_next_turn_and_run(params: Map[String, Object], func: (Turn) => Unit) = {
        //wait_for_turn
        func.apply(admin.current_turn)
        try {
            admin.current_turn.done
        } catch {
            case e: Exception => log.error(e, e)
        }
    }
    */

    /*
    def wait_for_scheduled_turns={
      while (next_turns.isEmpty){
        @next_turns.wait
     }      
    sleep(0.3)
   }  
      */

    override def take_turn(turn: Turn, is_setup: Boolean) = {
        super.take_turn(turn, is_setup)
        //notify_all
        Util.while_with_timeout(5000) { () =>
            next_turns.isEmpty
        }

        val (first, rest) = next_turns.splitAt(1)
        next_turns = rest
        val (callable, future) = first(0)

        if (callable.params != null && callable.params.isDefinedAt("should_roll")) {
            val should_roll = callable.params("should_roll").asInstanceOf[Int]
            val die1: Int = should_roll / 2
            val die2: Int = should_roll - die1
            admin.should_roll = (die1, die2)
            admin.current_turn.roll_dice
        }
        callable.synchronized {
            callable.turn = turn
            callable.notifyAll
        }
        future.get
        try {
            if (!admin.current_turn.isDone())
                admin.current_turn.done
        } catch {
            case _ =>
            //don't care
        }
        //}
        //@next_turns.notify_all
    }

    def wait_for_turn = {
        while (admin.current_turn == null || admin.current_turn != this.current_turn) {
            //wait
            Thread.sleep(500)
        }
    }

    //This should be overidden in the implementations
    override def get_user_quotes(player_info: PlayerInfo, wantList: List[Resource], giveList: List[Resource]): List[Quote] = {
        val quotes = should_offer
        should_offer = Nil
        return quotes
    }

    override def move_bandit(old_hex: Hex): Hex = {
        if (board == null) throw new IllegalStateException("board is null")
        if (move_bandit_to != null) {
            val loc = move_bandit_to
            move_bandit_to = null
            return loc
        }
        board.tiles.values.find { !_.has_bandit }.getOrElse(null)
    }

    //Ask the player to select some cards from a list.
    //This is used when a player must discard
    override def select_resource_cards(cards: List[Resource], count: Int, reason: Int): List[Resource] = {
        var real_count = if (select_resources_num > -1) select_resources_num else count

        if (select_nil_resources)
            return null
        var selection: List[Resource] = Nil
        var temp_list = cards
        (1 to real_count).foreach { x =>
            val (chosen_card, new_list) = temp_list.remove_random()
            temp_list = new_list
            selection = selection :+ chosen_card
        }
        selection
    }

    override def select_player(players: List[PlayerInfo], reason: Int) = {
        val other = players.filter { _ != this }
        if (other.isEmpty)
            throw new Exception("I'm being forced to select myself")
        other(0)
    }

    def get_current_turn = current_turn

}

class MockAdmin(
    board: Board,
    max_players: Int,
    max_points: Int = 10,
    turn_timeout: Int = 240) extends Admin(board, max_players, max_points, turn_timeout) {

    var should_roll: (Int, Int) = null

    def set_current_turn(turn: Turn) = {
        this.current_turn_obj = turn
    }

    override def start_game = {
        val r = super.start_game

        //Add a delay here because we don't want the mock player calling wait before we call notify
        Util.while_with_timeout(3000) { () => current_turn == null }
        r
    }

    /** make the default to NOT roll a 7, for testing. */
    override def roll_dice: (Int, Int) = {
        if (should_roll != null) {
            val result = roll_set_dice(should_roll)
            should_roll = null
            return result
        } else {
            val dice = dice_handler.get_dice
            var roll = (dice._1.roll, dice._2.roll)
            while (roll._1 + roll._2 == 7) {
                roll = (dice._1.roll, dice._2.roll)
            }

            super.roll_set_dice(roll)
        }
    }

    //Make a player rich
    def make_rich(player: Player) = {
        val cards: List[Resource] = Nil.padTo(50, OreType) ++
            Nil.padTo(50, WheatType) ++
            Nil.padTo(50, BrickType) ++
            Nil.padTo(50, SheepType) ++
            Nil.padTo(50, WoodType)
        give_cards(player, cards)
    }

    def give_cards(player: Player, cards: List[Resource]) = {
        val color = player.color
        get_player(color).add_cards(cards)
    }

    def set_cards(player: Player, new_cards: Map[Card, Int]) = {
        val trusted_player = get_player(player.color)
        val cards = trusted_player.get_cards
        new_cards.foreach { kv =>
            trusted_player.del_cards(Nil.padTo(cards(kv._1), kv._1), 1)
            if (kv._2 > 0) {
                trusted_player.add_cards(Nil.padTo(kv._2, kv._1))
            }
        }

    }
}

class MockCard extends DevelopmentCard {
    var was_used = false

    override def use(turn: Turn) = {
        super.use(turn)
        was_used = true
    }
}

/*
//  Copyright (C) 2007 John J Kennedy III
//
//  This program is free software; you can redistribute it and/or modify
//  it under the terms of the GNU General Public License as published by
//  the Free Software Foundation; either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.
  
require 'src/core/player'
require 'src/core/admin'
require 'flexmock'

module RubySettlers
  
  module CallCounter
    def initialize_counter
      @calls = {}
      @calls.default = 0
  //    watch(*(methods - Object.methods - ['record_call', 'was_called', 'watch', 'initialize_counter']))
   }  
    def record_call(method_name)
      @calls[method_name.to_s] += 1
  //    puts "recorded call to //{method_name} total://{@calls[method_name]}"
   }    
    def was_called(method_name)
  //    puts "//{method_name} //{@calls[method_name.to_s]}"
      @calls[method_name.to_s] > 0
   }  
    def reset_counter(method_name)
      @calls[method_name.to_s] = 0
   }  
    def times_called(method_name)
      @calls[method_name.to_s]
   }  
    module ClassMethods
      def watch(*method_names)
        for method_name in method_names
          watch_single(method_name)
       }     }  
      def watch_single(method_name)
        if (method_defined?(method_name))
          self.send(:alias_method, method_name.to_s + '_old', method_name)
       }        self.send(:define_method, method_name, Proc.new {|*args|
                    record_call(method_name)  
                    if self.class.method_defined?(method_name.to_s+'_old')
                      self.send(method_name.to_s+'_old', *args)
                    else
                      super(*args)
                   }                  })
     }  
   } }  
  
  
  module MockAdminModule
    include CallCounter
    attr_accessor :currentTurn, :should_roll, :maxPoints, :players
    
  
   
 }  
  class MockAdmin < Admin
    include MockAdminModule
  
    def initialize(*args)
      super(*args)
      @should_roll = nil
      initialize_counter
   } }  
  class MockBoard
  
 }end
*/ 