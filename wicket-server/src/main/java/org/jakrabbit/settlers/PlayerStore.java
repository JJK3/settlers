package org.jakrabbit.settlers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStore {

	private static Map<UUID, WicketPlayer> players = new HashMap<UUID, WicketPlayer>();

	public static WicketPlayer getPlayer(UUID id) {
		return players.get(id);
	}

	public static void addPlayer(UUID id, WicketPlayer player) {
		PlayerStore.players.put(id, player);
	}

}
