package org.jakrabbit.settlers.widgets;

import java.util.List;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.util.time.Duration;
import org.jakrabbit.settlers.SettlersWebSession;

import scala.collection.JavaConversions;
import core.Admin;
import core.Message;
import core.PlayerInfo;

public class ChatBox extends Panel {

	private static final long serialVersionUID = 1L;
	private String message;

	@SuppressWarnings("serial")
	public ChatBox(String id) {
		super(id);
		final ChatBox self = this;
		final SettlersWebSession session = (SettlersWebSession) getWebSession();
		final Form<String> form = new Form<String>("form") {
			protected void onSubmit() {
				Admin admin = session.getUser().getPlayer().admin();
				if (message != null && message.length() > 0) {
					admin.chat_msg(session.getUser().getPlayer(), message);
				}
				message = "";
			};
		};
		form.add(new TextField<String>("message", new PropertyModel<String>(this, "message")));
		form.add(new AjaxFormSubmitBehavior("onsubmit") {
			@Override
			protected void onSubmit(AjaxRequestTarget target) {
				target.add(self);
				self.add(new AjaxSelfUpdatingTimerBehavior(Duration.seconds(5)) {
					@Override
					protected void onPostProcessTarget(AjaxRequestTarget target) {
						target.appendJavaScript("scrollMessagesToBottom();");
					}
				});
				target.appendJavaScript("scrollMessagesToBottom();");
			}
		});
		add(form);
		IModel<List<Message>> model = new AbstractReadOnlyModel<List<Message>>() {
			@Override
			public List<Message> getObject() {
				return JavaConversions.seqAsJavaList(session.getUser().getPlayer().msgLog());
			}
		};
		add(new ListView<Message>("message_list", model) {
			@Override
			protected void populateItem(ListItem<Message> item) {
				Message m = item.getModelObject();
				item.add(new Label("msg_text", m.message()));
				PlayerInfo sender = m.sender();
				Label l = new Label("msg_sender", sender == null ? "Admin" : m.sender().first_name());

				String color = sender == null ? "black" : m.sender().color();
				if (color.equals("white")) {
					color = "black";
				}
				l.add(new AttributeAppender("style", "color:" + color + ";"));
				item.add(l);
			}
		});
		add(new AjaxSelfUpdatingTimerBehavior(Duration.seconds(5)) {
			@Override
			protected void onPostProcessTarget(AjaxRequestTarget target) {
				target.appendJavaScript("scrollMessagesToBottom();");
			}
		});
	}
}