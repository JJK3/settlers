package org.jakrabbit.settlers.pages;

import org.apache.wicket.authroles.authorization.strategies.role.annotations.AuthorizeInstantiation;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.jakrabbit.settlers.SettlersWebSession;
import org.jakrabbit.settlers.User;

@AuthorizeInstantiation("USER")
public class HomePage extends WebPage {
	private static final long serialVersionUID = 1L;

	public HomePage(final PageParameters parameters) {
		super(parameters);
		User u = ((SettlersWebSession) getSession()).getUser();
		if (u == null) {
			redirectToInterceptPage(new LoginPage(null));
		} else {
			add(new Label("version", getApplication().getFrameworkSettings()
					.getVersion()));

			// TODO Add your page's components here
		}
	}
}
