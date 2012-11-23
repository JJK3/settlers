package org.jakrabbit.settlers;

import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;

public class SettlersWebSession extends WebSession {

	private static final long serialVersionUID = 1L;
	private User user;

	public SettlersWebSession(Request request) {
		super(request);
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public boolean isSignedIn() {
		return getUser() != null;
	}
}