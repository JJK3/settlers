package org.jakrabbit.settlers.pages;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.jakrabbit.settlers.SettlersWebSession;
import org.jakrabbit.settlers.User;

public class LoginPage extends WebPage {
	private static final long serialVersionUID = 1L;

	public LoginPage(final PageParameters parameters) {
		super(parameters);
		// add(new SignInPanel("signInPanel"));
		@SuppressWarnings({ "serial" })
		Form form = new Form<User>("signin_form",
				new CompoundPropertyModel<User>(new User())) {
			@Override
			protected void onSubmit() {
				super.onSubmit();
				User user = (User) getModelObject();
				String username = user.getUsername();
				if (username != null && username.length() > 0) {
					((SettlersWebSession) getSession()).setUser(user);
					this.redirectToInterceptPage(new HomePage(null));
				}
			}
		};
		form.add(new TextField("username"));
		add(form);
	}

}
