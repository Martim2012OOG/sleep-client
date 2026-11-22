package dev.sleepclient.gui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sleepclient.social.LobbyData;
import dev.sleepclient.social.Party;
import dev.sleepclient.social.Social;
import dev.sleepclient.video.HtmlOverlay;
import dev.sleepclient.video.Mcef;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;

/**
 * The main menu while you are in a party: the 3D lobby (a web page inside the game, through the video engine). Your friends stand in
 * it with their real skins, you invite from its menu, the emotes you bought play on the characters, and Play sends the whole party to
 * a server. Leaving the lobby leaves the party. F6 switches to the plain 2D party screen.
 * Page -> game: commands travel in the page address (#c1:{json}); game -> page: JavaScript calls on window.OneMenu.
 */
public final class PartyLobbyScreen extends Screen {

	private static final Gson GSON = new Gson();
	private final Screen parent;
	private HtmlOverlay page;
	private boolean jsReady, closedByUs;
	private long openedAt, lastSync, joinAt;
	private int lastSeq;
	private long nextTry;
	private String lastFriends = "", lastEmotes = "", lastSkin = "";
	private boolean lastLeader = true, roleSent, serversSent, nameSent;
	private Party.Go joining;

	public PartyLobbyScreen(Screen parent) {
		super(Component.literal("Party"));
		this.parent = parent;
	}

	/** The page is in the jar: it is copied next to the config so the browser can open it as a file. */
	private static String pageUrl() throws Exception {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("sleep").resolve("lobby");
		Files.createDirectories(dir);
		Path f = dir.resolve("lobby.html");
		try (var in = PartyLobbyScreen.class.getResourceAsStream("/assets/sleep_client/lobby/lobby.html")) {
			if (in == null) {
				throw new IllegalStateException("lobby.html missing");
			}
			byte[] b = in.readAllBytes();
			if (!Files.exists(f) || Files.size(f) != b.length || !java.util.Arrays.equals(Files.readAllBytes(f), b)) {
				Files.write(f, b);
			}
		}
		return f.toUri().toString();
	}

	@Override
	protected void init() {
		tryOpen();
	}

	/** Opens the page as soon as the video engine is ready (it is downloaded the first time: ~100 MB). */
	private void tryOpen() {
		long now = System.currentTimeMillis();
		if (page != null || now < nextTry) {
			return;
		}
		nextTry = now + 1000L;
		if (!Mcef.installed()) {
			return;
		}
		Mcef.start();
		if (!HtmlOverlay.available()) {
			return;
		}
		try {
			page = HtmlOverlay.open(pageUrl());
		} catch (Throwable t) {
			System.err.println("[Sleep] 3D lobby: " + t);
		}
		if (page != null) {
			openedAt = now;
			Party.lobbyOpen = true;
			try {
				page.browser().focus();
			} catch (Throwable ignored) {
				// focus is only a hint
			}
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false; // Esc belongs to the page (closes the emote wheel / panels); leave with the exit button
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		Party.lobbyOpen = false;
		if (page != null) {
			page.close();
			page = null;
		}
	}

	// ------------------------------------------------------------------ frame
	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
		c.fill(0, 0, width, height, 0xFF0C1220);
		if (page == null) {
			tryOpen();
			String st = !Mcef.installed() ? "The video engine (MCEF) is missing from this build" : "Getting the 3D lobby ready... " + Mcef.status();
			dev.sleepclient.Gfx.textC(c, st, width / 2, height / 2 - 6, 0xFFB8C0D8);
			dev.sleepclient.Gfx.textC(c, "Esc = leave the party", width / 2, height / 2 + 10, 0xFF7C8294);
			return;
		}
		page.draw(c);
		poll();
		long now = System.currentTimeMillis();
		if (!jsReady) {
			String msg = now - openedAt > 12_000L ? "The 3D lobby is taking long to load..." : "Loading the 3D lobby...";
			dev.sleepclient.Gfx.textC(c, msg, width / 2, height - 24, 0xFFB8C0D8);
			return;
		}
		if (now - lastSync > 250L) {
			lastSync = now;
			sync();
		}
		if (joining != null && now >= joinAt) {
			Party.Go g = joining;
			joining = null;
			Party.joinNow(g);
		}
	}

	// ------------------------------------------------------------------ page -> game
	private void poll() {
		String url;
		try {
			url = page.browser().url();
		} catch (Throwable t) {
			return;
		}
		int h = url == null ? -1 : url.indexOf('#');
		if (h < 0) {
			return;
		}
		String frag = url.substring(h + 1);
		if (frag.equals("ready")) {
			if (!jsReady) {
				jsReady = true;
				lastFriends = lastEmotes = lastSkin = "";
				roleSent = serversSent = nameSent = false;
			}
			return;
		}
		if (!frag.startsWith("c")) {
			return;
		}
		int colon = frag.indexOf(':');
		if (colon < 0) {
			return;
		}
		int seq;
		try {
			seq = Integer.parseInt(frag.substring(1, colon));
		} catch (NumberFormatException e) {
			return;
		}
		if (seq == lastSeq) {
			return;
		}
		lastSeq = seq;
		try {
			JsonObject o = JsonParser.parseString(URLDecoder.decode(frag.substring(colon + 1), StandardCharsets.UTF_8)).getAsJsonObject();
			command(o);
		} catch (RuntimeException e) {
			System.err.println("[Sleep] 3D lobby command: " + e);
		}
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
	}

	private void command(JsonObject o) {
		switch (str(o, "t")) {
			case "invite" -> {
				Social.User u = friend(str(o, "n"));
				if (u != null) {
					Party.invite(u);
				}
			}
			case "cancel" -> {
				String n = str(o, "n");
				for (Party.Member m : Party.MEMBERS) {
					if (m.nick.equalsIgnoreCase(n) && Party.isLeader()) {
						Party.kick(m);
						return;
					}
				}
				Party.cancelInvite(friend(n));
			}
			case "emote" -> Party.sendEmote(str(o, "id"));
			case "ready" -> Party.toggleReady();
			case "exit" -> {
				Party.leave();
				leave();
			}
			case "play" -> {
				if (Party.isLeader()) {
					Party.playTogether(str(o, "name"), str(o, "ip"));
				}
			}
			case "cancelJoin" -> {
				joining = null;
				Party.go = null;
			}
			default -> {
			}
		}
	}

	private static Social.User friend(String name) {
		for (Social.User u : Social.friends) {
			if (name.equalsIgnoreCase(u.mcUsername) || name.equalsIgnoreCase(u.display())) {
				return u;
			}
		}
		return null;
	}

	private void leave() {
		closedByUs = true;
		dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), parent == null ? new net.minecraft.client.gui.screens.TitleScreen() : parent);
	}

	// ------------------------------------------------------------------ game -> page
	private void js(String code) {
		if (page != null) {
			page.js(code);
		}
	}

	private void sync() {
		if (!Party.active()) {
			// the party ended (disbanded, removed, leader gone)
			if (!closedByUs) {
				leave();
			}
			return;
		}
		boolean leader = Party.isLeader();
		if (!roleSent || leader != lastLeader) {
			roleSent = true;
			lastLeader = leader;
			js("OneMenu.setRole(" + leader + ")");
		}
		if (!nameSent) {
			nameSent = true;
			js("OneMenu.setUsername(" + GSON.toJson(Minecraft.getInstance().getUser().getName()) + ")");
		}
		String skin = LobbyData.mySkin();
		if (skin != null && !skin.equals(lastSkin)) {
			lastSkin = skin;
			js("OneMenu.setSkin(" + GSON.toJson(skin) + ")");
		}
		String fr = LobbyData.people().toString();
		if (!fr.equals(lastFriends)) {
			lastFriends = fr;
			js("OneMenu.setFriends(" + fr + ")");
		}
		JsonArray wheel = LobbyData.wheel();
		String we = wheel.toString();
		if (!we.equals(lastEmotes) && wheel.size() > 0) {
			lastEmotes = we;
			js("OneMenu.setEmotes(" + we + ")");
		}
		if (!serversSent) {
			serversSent = true;
			js("OneMenu.setServers(" + servers() + ")");
		}
		// emotes the others played
		String[] em;
		while ((em = Party.EMOTES.poll()) != null) {
			String id = em[1];
			if (id.startsWith("emoji:")) {
				js("OneMenu.remoteEmote(" + GSON.toJson(em[0]) + ",null,null," + GSON.toJson(id.substring(6)) + ")");
			} else {
				JsonObject a = LobbyData.animFor(id);
				if (a != null) {
					js("OneMenu.remoteEmote(" + GSON.toJson(em[0]) + "," + GSON.toJson(id) + "," + a + ",null)");
				} else {
					js("OneMenu.remoteEmote(" + GSON.toJson(em[0]) + "," + GSON.toJson(id) + ",null," + GSON.toJson("🎭") + ")");
				}
			}
		}
		// the leader started: show the "joining" screen, then connect with the party
		Party.Go g = Party.go;
		if (g != null && joining == null && !Party.isLeader()) {
			joining = g;
			joinAt = System.currentTimeMillis() + (Party.autoJoin ? 2600L : Long.MAX_VALUE);
			js("OneMenu.joining(" + GSON.toJson(g.name()) + ")");
		}
	}

	private String servers() {
		JsonArray out = new JsonArray();
		int hue = 200;
		if (dev.sleepclient.social.HostWorld.hosting()) {
			JsonObject o = new JsonObject();
			o.addProperty("name", dev.sleepclient.social.HostWorld.worldName());
			o.addProperty("ip", dev.sleepclient.social.HostWorld.address());
			o.addProperty("online", "");
			o.addProperty("ping", 0);
			o.addProperty("hue", 120);
			out.add(o);
		}
		try {
			ServerList sl = new ServerList(Minecraft.getInstance());
			sl.load();
			for (int i = 0; i < sl.size(); i++) {
				var s = sl.get(i);
				if (s == null || s.ip == null || s.ip.isBlank()) {
					continue;
				}
				JsonObject o = new JsonObject();
				o.addProperty("name", s.name == null || s.name.isBlank() ? "Minecraft Server" : s.name);
				o.addProperty("ip", s.ip);
				o.addProperty("online", "");
				o.addProperty("ping", 0);
				o.addProperty("hue", (hue + i * 47) % 360);
				out.add(o);
			}
		} catch (Throwable ignored) {
			// no server list
		}
		return out.toString();
	}

	// ------------------------------------------------------------------ input -> page
	private int px(double v) {
		var w = minecraft.getWindow();
		return (int) Math.round(v * w.getWidth() / (double) Math.max(1, w.getGuiScaledWidth()));
	}

	private int py(double v) {
		var w = minecraft.getWindow();
		return (int) Math.round(v * w.getHeight() / (double) Math.max(1, w.getGuiScaledHeight()));
	}

	private static int awt(int glfwButton) {
		return glfwButton == 0 ? 1 : glfwButton == 1 ? 3 : 2;
	}

	@Override
	public void mouseMoved(double mx, double my) {
		if (page != null) {
			page.browser().mouse(503, px(mx), py(my), 0, 0);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (page != null) {
			page.browser().focus();
			page.browser().mouse(501, px(click.x()), py(click.y()), awt(click.button()), doubled ? 2 : 1);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (page != null) {
			page.browser().mouse(502, px(click.x()), py(click.y()), awt(click.button()), 1);
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (page != null) {
			page.browser().mouse(506, px(click.x()), py(click.y()), awt(click.button()), 0);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		if (page != null && v != 0) {
			page.browser().wheel(px(mx), py(my), v > 0 ? 1 : -1);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (page == null) {
			if (input.key() == 256) { // nothing to show yet: Esc leaves the party
				Party.leave();
				leave();
			}
			return true;
		}
		if (page != null) {
			page.browser().keyMc(input, true);
		}
		return true;
	}

	@Override
	public boolean keyReleased(KeyEvent input) {
		if (page != null) {
			page.browser().keyMc(input, false);
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (page != null) {
			page.browser().charMc(input);
		}
		return true;
	}
}
