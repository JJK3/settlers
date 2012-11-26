package org.jakrabbit.settlers;

import java.io.Serializable;
import java.util.UUID;

import org.apache.log4j.Logger;

public class User implements Serializable {

	private static final long serialVersionUID = 1L;
	private String username = null;
	private UUID playerID;
	public static Logger log = Logger.getLogger(User.class);

	public User() {
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public WicketPlayer getPlayer() {
		return PlayerStore.getPlayer(playerID);
	}

	public void setPlayer(WicketPlayer player) {
		this.playerID = UUID.randomUUID();
		PlayerStore.addPlayer(playerID, player);
	}

}
