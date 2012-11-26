package org.jakrabbit.settlers.pages;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.jakrabbit.settlers.SettlersWebSession;
import org.jakrabbit.settlers.User;
import org.jakrabbit.settlers.WicketPlayer;
import org.jakrabbit.settlers.widgets.ChatBox;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import scala.Tuple2;
import scala.collection.JavaConversions;
import core.Admin;
import core.Board;
import core.BrickType$;
import core.City;
import core.DesertType$;
import core.Edge;
import core.Hex;
import core.HexType;
import core.Node;
import core.OreType$;
import core.Port;
import core.SheepType$;
import core.WheatType$;
import core.WoodType$;

public class PlayPage extends WebPage {
	private static final long serialVersionUID = 1L;

	public PlayPage(final PageParameters parameters) {
		super(parameters);
		User u = ((SettlersWebSession) getSession()).getUser();
		if (u == null) {
			redirectToInterceptPage(new LoginPage(null));
		} else {
			@SuppressWarnings("serial")
			AbstractReadOnlyModel<String> boardModel = new AbstractReadOnlyModel<String>() {
				@Override
				public String getObject() {
					JSONObject boardData;
					User u = ((SettlersWebSession) getSession()).getUser();
					WicketPlayer player = u.getPlayer();
					Admin admin = u.getPlayer().admin();
					Board b = player.board();
					boardData = new JSONObject();
					try {
						JSONArray tiles = new JSONArray();
						for (Tuple2<Object, Object> t : JavaConversions.mapAsJavaMap(b.tiles()).keySet()) {
							int x = (Integer) t._1();
							int y = (Integer) t._2();
							Hex hex = b.tiles().get(t).get();
							JSONObject tile = new JSONObject();
							tile.put("x", x);
							tile.put("y", y);
							tile.put("number", hex.number());
							tile.put("type", getHexType(hex.card_type()));
							tiles.put(tile);
						}
						boardData.put("tiles", tiles);

						JSONArray nodes = new JSONArray();
						for (Node n : JavaConversions.setAsJavaSet(b.nodes())) {
							if (n.has_city()) {
								nodes.put(nodeToJSON(n));
							}
						}
						boardData.put("nodes", nodes);

						JSONArray edges = new JSONArray();
						for (Edge e : JavaConversions.setAsJavaSet(b.edges())) {
							if (e.has_road() || e.has_port()) {
								edges.put(edgeToJSON(e));
							}
						}
						boardData.put("edges", edges);

					} catch (JSONException e) {
						e.printStackTrace();
					}
					return "var boardData = " + boardData.toString() + ";";
				}
			};

			add(new ChatBox("chat"));
			Label boardData = new Label("board_data", boardModel);
			boardData.setEscapeModelStrings(false);
			add(boardData);
		}
	}

	private JSONObject edgeToJSON(Edge e) throws JSONException {
		JSONObject edge = new JSONObject();
		edge.put("x", e.x());
		edge.put("y", e.y());
		edge.put("n", e.edgeNum());
		JSONArray edgeNodes = new JSONArray();
		edgeNodes.put(nodeToJSON(e.nodes().apply(0)));
		edgeNodes.put(nodeToJSON(e.nodes().apply(1)));
		edge.put("nodes", edgeNodes);
		if (e.has_road()) {
			edge.put("road", e.road().color());
		}
		if (e.has_port()) {
			Port port = e.nodes().apply(0).port();
			JSONObject portNode = new JSONObject();
			portNode.put("type", getHexType(port.kind()));
			portNode.put("rate", port.rate());
			edge.put("port", portNode);
		}
		return edge;
	}

	private JSONObject nodeToJSON(Node n) throws JSONException {
		JSONObject node = new JSONObject();
		node.put("x", n.x());
		node.put("y", n.y());
		node.put("n", n.nodeNum());
		if (n.has_city()) {
			JSONObject city = new JSONObject();
			city.put("type", ((n.city() instanceof City) ? "city" : "settlement"));
			city.put("color", n.city().color());
			node.put("city", city);
		}
		if (n.has_port()) {
			JSONObject port = new JSONObject();
			port.put("type", getHexType(n.port().kind()));
			port.put("rate", n.port().rate());
			node.put("port", port);
		}
		return node;
	}

	private String getHexType(HexType type) {
		if (type == BrickType$.MODULE$) {
			return "brick";
		} else if (type == OreType$.MODULE$) {
			return "ore";
		} else if (type == WoodType$.MODULE$) {
			return "wood";
		} else if (type == SheepType$.MODULE$) {
			return "sheep";
		} else if (type == WheatType$.MODULE$) {
			return "wheat";
		} else if (type == DesertType$.MODULE$) {
			return "desert";
		}
		return "unknown";
	}
}
