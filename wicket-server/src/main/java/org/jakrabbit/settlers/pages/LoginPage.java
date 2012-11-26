package org.jakrabbit.settlers.pages;

import java.util.Arrays;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.jakrabbit.settlers.SettlersWebSession;
import org.jakrabbit.settlers.User;
import org.jakrabbit.settlers.WicketPlayer;

import scala.collection.JavaConversions;
import board.StandardBoard;
import core.Admin;
import core.Board;
import core.Player;
import core.SinglePurchasePlayer;

public class LoginPage extends WebPage {
	private static final long serialVersionUID = 1L;

	public LoginPage(final PageParameters parameters) {
		super(parameters);
		// add(new SignInPanel("signInPanel"));
		@SuppressWarnings({ "serial" })
		Form form = new Form<User>("signin_form", new CompoundPropertyModel<User>(new User())) {
			@Override
			protected void onSubmit() {
				super.onSubmit();
				SettlersWebSession session = (SettlersWebSession) getSession();
				if (!session.isSignedIn()) {
					User user = (User) getModelObject();
					String username = user.getUsername();
					if (username != null && username.length() > 0) {
						session.setUser(user);

						// TODO: until I write an intermediate page.
						Board b = new StandardBoard();
						Admin gameAdmin = new Admin(b, 4, 10, 60, 30 * 60);
						for (int i = 0; i < 3; i++) {
							Player bot = new SinglePurchasePlayer("bot1", "", gameAdmin, User.log, 4, 5, 15, b);
							gameAdmin.register(JavaConversions.asScalaBuffer(Arrays.asList(bot)));
						}
						user.setPlayer(new WicketPlayer(username, "", gameAdmin, User.log, 4, 5, 15, b));
						gameAdmin.register(JavaConversions.asScalaBuffer(Arrays.asList((Player) user.getPlayer())));

						this.redirectToInterceptPage(new PlayPage(null));
					}
				}
			}
		};
		form.add(new TextField("username"));
		add(form);
	}
}
