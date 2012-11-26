package org.jakrabbit.settlers;

import org.apache.log4j.Logger;

import scala.collection.immutable.List;
import core.Admin;
import core.Board;
import core.Hex;
import core.Player;
import core.PlayerInfo;
import core.Quote;
import core.Resource;

public class WicketPlayer extends Player {

	public WicketPlayer(String first_name, String last_name, Admin admin, Logger log, int cities, int settlements, int roads, Board _board) {
		super(first_name, last_name, admin, log, cities, settlements, roads, _board);
	}

	@Override
	public void chat_msg(PlayerInfo player, String msg) {
		super.chat_msg(player, msg);
		System.out.println("message:" + msg);

	}

	@Override
	public List<Quote> get_user_quotes(PlayerInfo arg0, List<Resource> arg1, List<Resource> arg2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Hex move_bandit(Hex arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PlayerInfo select_player(List<PlayerInfo> arg0, int arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Resource> select_resource_cards(List<Resource> arg0, int arg1, int arg2) {
		// TODO Auto-generated method stub
		return null;
	}

}
