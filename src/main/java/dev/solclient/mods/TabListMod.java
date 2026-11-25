package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.gui.Faces;
import dev.solclient.gui.Theme;
import dev.solclient.mixin.cosmetic.InventoryScreenAccessor;
import dev.solclient.option.Option;
import dev.solclient.social.Social;
import dev.solclient.social.SolarBadge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Replacement tab list (vanilla one is hidden by PlayerListHudMixin). Player heads, rounded corners, no header / footer.
 *
 * Hold Tab and click the left mouse button N times quickly (Settings: "Clicks to unlock", default 2): the mouse is
 * released while Tab is still held. Then click a player to open a card with his stats (game mode, ping, average ping +
 * jitter, time online, ping graph, 3D model); click him again, or an empty spot, to close it. Letting go of Tab (or
 * opening any screen) puts the mouse back.
 */
public class TabListMod extends Mod {

	public static TabListMod instance;

	private final Option.Choice ping = choice("Ping", 0, "Bars", "Number", "Off");
	private final Option.Bool heads = bool("Tab Heads", true);
	private final Option.Num headSize = num("Head Size", 8F, 6F, 16F, 1F, " px");
	private final Option.Col listBackground = colour("List Background Colour", 0x80000000);
	private final Option.Col entryBackground = colour("Entry Background Colour", 0x20FFFFFF);
	private final Option.Num listRadius = num("List Corner Radius", 4F, 0F, 12F, 1F, " px");
	private final Option.Num entryRadius = num("Entry Corner Radius", 2F, 0F, 6F, 1F, " px");
	private final Option.Num rowGap = num("Row Spacing", 0F, 0F, 4F, 1F, " px");
	private final Option.Bool shadow = bool("Text Shadow", true);

	private final Option.Bool clickUnlock = bool("Click To Unlock Mouse", true);
	private final Option.Num unlockClicks = num("Clicks To Unlock", 2, 1, 6, 1, "x");
	private final Option.Num clickSpeed = num("Max Time Between Clicks", 500, 200, 1000, 50, " ms");
	private final Option.Bool statsCard = bool("Player Stats On Click", true);

	public TabListMod() {
		super("Tab List", "tab_list", "Customise the tab list.", Category.HUD);
		instance = this;
		unlockClicks.showIf(clickUnlock::on);
		clickSpeed.showIf(clickUnlock::on);
		headSize.showIf(heads::on);
		statsCard.showIf(clickUnlock::on);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	/** Set when our own drawing threw: from then on the vanilla tab list is used again, so Tab never shows nothing. */
	private static boolean broken;
	private static boolean warned;
	private static boolean bodyBroken;

	public static boolean replacesVanilla() {
		return instance != null && instance.enabled && !broken;
	}

	/** A name for the list that never throws (extensions that decorate names must not be able to hide the tab list). */
	private static Text safeName(PlayerListHud hud, PlayerListEntry e) {
		try {
			Text t = hud.getPlayerName(e);
			if (t != null) {
				return t;
			}
		} catch (Throwable ignored) {
		}
		try {
			if (e.getDisplayName() != null) {
				return e.getDisplayName();
			}
		} catch (Throwable ignored) {
		}
		return Text.literal("?");
	}

	// ------------------------------------------------------------------------------------------ mouse unlock
	/** True while the mouse is released over the tab list. */
	private boolean cursorMode;
	private int clicks;
	private long lastClick;
	/** The press of a left click was eaten (while Tab is held), so its release must be eaten too (no stuck attack key). */
	private boolean swallowedPress;
	private UUID selected;

	/**
	 * Called first thing by MouseMixin for every mouse button. Returns true when the click belongs to the tab list and must
	 * not reach the game (no attacking / placing while you click on the list).
	 */
	public static boolean onMouseButton(int button, int action) {
		TabListMod t = instance;
		if (t == null || !t.enabled || broken) {
			return false;
		}
		try {
			MinecraftClient m = MinecraftClient.getInstance();
			if (t.cursorMode) {
				if (button == 0 && action == 1) {
					t.click();
				} else if (button == 0 && action == 0) {
					t.swallowedPress = false;
				}
				return true; // everything is ours while the mouse is free (also stops vanilla from re-locking it on a click)
			}
			boolean tabHeld = t.clickUnlock.on() && replacesVanilla() && m.currentScreen == null && m.player != null
					&& m.options.playerListKey.isPressed();
			if (button != 0) {
				return false;
			}
			if (!tabHeld) {
				if (action == 0 && t.swallowedPress) {
					t.swallowedPress = false;
					return true;
				}
				return false;
			}
			if (action == 1) {
				long now = System.currentTimeMillis();
				t.clicks = now - t.lastClick <= t.clickSpeed.i() ? t.clicks + 1 : 1;
				t.lastClick = now;
				t.swallowedPress = true;
				if (t.clicks >= t.unlockClicks.i()) {
					t.clicks = 0;
					t.enterCursorMode();
				}
				return true;
			}
			if (t.swallowedPress) {
				t.swallowedPress = false;
				return true;
			}
		} catch (Throwable th) {
			t.exitCursorMode(true);
		}
		return false;
	}

	private void enterCursorMode() {
		cursorMode = true;
		selected = null;
		popValid = false;
		mc.mouse.unlockCursor();
	}

	private void exitCursorMode(boolean relock) {
		boolean was = cursorMode;
		cursorMode = false;
		selected = null;
		popValid = false;
		clicks = 0;
		swallowedPress = false;
		if (was && relock) {
			try {
				mc.mouse.lockCursor(); // also does setScreen(null): only when no screen is open
			} catch (Throwable ignored) {
			}
		}
	}

	@Override
	protected void onDisable() {
		exitCursorMode(mc.currentScreen == null);
	}

	/** Every frame: the mouse goes back as soon as Tab is released, a screen opens or the list is replaced. */
	@Override
	public void frame() {
		if (!cursorMode) {
			return;
		}
		if (mc.player == null || mc.world == null || !replacesVanilla() || !clickUnlock.on() || mc.currentScreen != null
				|| !mc.options.playerListKey.isPressed()) {
			exitCursorMode(mc.currentScreen == null);
		}
	}

	private void click() {
		double[] p = cursor();
		int idx = indexAt(p[0], p[1]);
		if (idx >= 0 && statsCard.on()) {
			UUID id = SolarBadge.idOf(gList.get(idx).getProfile());
			selected = id != null && id.equals(selected) ? null : id;
		} else if (!(popValid && p[0] >= popX && p[0] < popX + popW && p[1] >= popY && p[1] < popY + popH)) {
			selected = null; // click on nothing: close the card
		}
	}

	/** The cursor in GUI units. */
	private static double[] cursor() {
		MinecraftClient m = MinecraftClient.getInstance();
		var w = m.getWindow();
		return new double[] { m.mouse.getX() * w.getScaledWidth() / (double) w.getWidth(),
				m.mouse.getY() * w.getScaledHeight() / (double) w.getHeight() };
	}

	// ------------------------------------------------------------------------------------------ ping history
	private static final class Track {
		final long since = System.currentTimeMillis();
		final int[] s = new int[60];
		int n;

		void push(int v) {
			s[n % 60] = v;
			n++;
		}

		int size() {
			return Math.min(n, 60);
		}

		/** i = 0 is the oldest sample. */
		int get(int i) {
			int start = n > 60 ? n % 60 : 0;
			return s[(start + i) % 60];
		}
	}

	private static final Map<UUID, Track> TRACK = new HashMap<>();
	private static Object trackWorld;
	private int sampleTick;

	/** Once a second: remember everybody's ping (for the average, the jitter and the graph) and since when they are here. */
	@Override
	public void tick() {
		ClientPlayNetworkHandler h = mc.getNetworkHandler();
		if (mc.world == null || h == null) {
			TRACK.clear();
			trackWorld = null;
			return;
		}
		if (trackWorld != mc.world) {
			TRACK.clear();
			trackWorld = mc.world;
		}
		if (++sampleTick < 20) {
			return;
		}
		sampleTick = 0;
		try {
			TRACK.keySet().removeIf(id -> h.getPlayerListEntry(id) == null);
			for (PlayerListEntry e : h.getListedPlayerListEntries()) {
				UUID id = SolarBadge.idOf(e.getProfile());
				if (id == null) {
					continue;
				}
				Track t = TRACK.get(id);
				if (t == null) {
					if (TRACK.size() > 500) {
						continue;
					}
					t = new Track();
					TRACK.put(id, t);
				}
				t.push(e.getLatency());
			}
		} catch (Throwable ignored) {
		}
	}

	// ------------------------------------------------------------------------------------------ drawing
	public void draw(DrawContext c) {
		try {
			drawList(c);
		} catch (Throwable t) {
			broken = true;
			if (!warned) {
				warned = true;
				System.err.println("[Caos] Tab List failed, using the vanilla tab list instead: " + t);
			}
		}
	}

	// where the list was drawn last (for clicking)
	private int gx0, gy0, gColW, gRows, gN, gTotal, gRowH = 9;
	private List<PlayerListEntry> gList = List.of();
	private int popX, popY, popW, popH;
	private boolean popValid;

	private int indexAt(double mx, double my) {
		if (gN == 0 || gRows == 0) {
			return -1;
		}
		int col = (int) Math.floor((mx - gx0) / (gColW + 1));
		int row = (int) Math.floor((my - gy0) / gRowH);
		if (col < 0 || row < 0 || row >= gRows) {
			return -1;
		}
		int x = gx0 + col * (gColW + 1);
		if (mx < x || mx >= x + gColW) {
			return -1;
		}
		int idx = col * gRows + row;
		return idx < gN ? idx : -1;
	}

	private void drawList(DrawContext c) {
		ClientPlayNetworkHandler handler = mc.getNetworkHandler();
		if (mc.world == null || mc.player == null || handler == null) {
			return;
		}
		List<PlayerListEntry> list = new ArrayList<>(handler.getListedPlayerListEntries());
		if (mc.isInSingleplayer() && list.size() <= 1) {
			return;
		}
		PlayerListHud hud = mc.inGameHud.getPlayerListHud();
		list.sort(Comparator.comparing(e -> safeName(hud, e).getString().toLowerCase()));
		if (list.size() > 80) {
			list = new ArrayList<>(list.subList(0, 80));
		}
		TextRenderer tr = mc.textRenderer;
		int n = list.size();
		int cols = 1;
		int rows = n;
		while (rows > 20) {
			cols++;
			rows = (n + cols - 1) / cols;
		}
		List<Text> names = new ArrayList<>();
		int nameW = 0;
		for (PlayerListEntry e : list) {
			Text t = safeName(hud, e);
			names.add(t);
			nameW = Math.max(nameW, tr.getWidth(t));
		}
		int pingW = ping.idx() == 0 ? 12 : ping.idx() == 1 ? tr.getWidth("999") + 3 : 0;
		int hs = heads.on() ? headSize.i() : 0;
		int headW = heads.on() ? hs + 2 : 0;
		int rowH = Math.max(9, hs + 1) + rowGap.i();
		int textOff = (rowH - 9) / 2;
		int colW = Math.min(nameW + pingW + headW + 4, mc.getWindow().getScaledWidth() / cols - 4);
		int total = cols * colW + (cols - 1);
		int x0 = mc.getWindow().getScaledWidth() / 2 - total / 2;
		int y0 = 10;
		gx0 = x0;
		gy0 = y0;
		gColW = colW;
		gRows = rows;
		gN = n;
		gTotal = total;
		gRowH = rowH;
		gList = list;
		int hover = -1;
		if (cursorMode) {
			double[] p = cursor();
			hover = indexAt(p[0], p[1]);
		}
		if (listRadius.i() > 0) {
			Gfx.rrectFree(c, x0 - 2, y0 - 2, total + 4, rows * rowH + 4, listRadius.f(), listBackground.c());
		} else {
			c.fill(x0 - 1, y0 - 1, x0 + total + 1, y0 + rows * rowH + 1, listBackground.c());
		}
		int eh = rowH - 1 - rowGap.i();
		int er = entryRadius.i();
		for (int i = 0; i < n; i++) {
			int col = i / rows, row = i % rows;
			int x = x0 + col * (colW + 1);
			int y = y0 + row * rowH;
			if (er > 0) {
				Gfx.rrectFree(c, x, y, colW, eh, er, entryBackground.c());
			} else {
				c.fill(x, y, x + colW, y + eh, entryBackground.c());
			}
			if (cursorMode && i == hover) {
				if (er > 0) {
					Gfx.rrectFree(c, x, y, colW, eh, er, 0x30FFFFFF);
				} else {
					c.fill(x, y, x + colW, y + eh, 0x30FFFFFF);
				}
			}
			UUID id = selected == null ? null : SolarBadge.idOf(list.get(i).getProfile());
			if (id != null && id.equals(selected)) {
				c.fill(x, y, x + colW, y + eh, (Theme.accent() & 0xFFFFFF) | 0x50000000);
				Gfx.outline(c, x, y, colW, eh, Theme.accent());
			}
			if (heads.on()) {
				try {
					net.minecraft.client.gui.PlayerSkinDrawer.draw(c, list.get(i).getSkinTextures(), x + 1, y + (eh - hs) / 2, hs);
				} catch (Throwable ignored) { // a missing skin must never break the list
				}
			}
			c.drawText(tr, names.get(i), x + 1 + headW, y + textOff, 0xFFFFFFFF, shadow.on());
			int latency = list.get(i).getLatency();
			if (ping.idx() == 1) {
				String s = Integer.toString(latency);
				int col2 = latency < 150 ? 0xFF55FF55 : latency < 300 ? 0xFFFFFF55 : 0xFFFF5555;
				c.drawText(tr, s, x + colW - tr.getWidth(s) - 2, y + textOff, col2, shadow.on());
			} else if (ping.idx() == 0) {
				int lit = latency < 150 ? 5 : latency < 300 ? 4 : latency < 600 ? 3 : latency < 1000 ? 2 : 1;
				for (int b = 0; b < 5; b++) {
					int bx = x + colW - 11 + b * 2;
					int h = 2 + b;
					c.fill(bx, y + textOff + 8 - h, bx + 1, y + textOff + 8, b < lit ? 0xFF55FF55 : 0xFF404040);
				}
			}
		}
		popValid = false;
		if (selected != null && statsCard.on()) {
			try {
				drawCard(c, list);
			} catch (Throwable t) { // a bug in the card must never take the whole tab list down
				selected = null;
				popValid = false;
				System.err.println("[Caos] Tab List player card failed: " + t);
			}
		}
	}

	// ------------------------------------------------------------------------------------------ player card
	private static int pingColour(int ms) {
		return ms < 150 ? Theme.GREEN : ms < 300 ? 0xFFFFD866 : Theme.RED;
	}

	private static String dur(long ms) {
		long s = Math.max(0, ms / 1000);
		if (s < 60) {
			return s + "s";
		}
		long m = s / 60;
		if (m < 60) {
			return m + "m";
		}
		return (m / 60) + "h " + (m % 60) + "m";
	}

	private static boolean isFriend(String name) {
		try {
			for (Social.User u : Social.friends) {
				if (name.equalsIgnoreCase(u.mcUsername)) {
					return true;
				}
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	private void drawCard(DrawContext c, List<PlayerListEntry> list) {
		int idx = -1;
		for (int i = 0; i < list.size(); i++) {
			if (selected.equals(SolarBadge.idOf(list.get(i).getProfile()))) {
				idx = i;
				break;
			}
		}
		if (idx < 0) {
			selected = null; // he left
			return;
		}
		PlayerListEntry e = list.get(idx);
		String name = SolarBadge.nameOf(e.getProfile());
		if (name == null) {
			name = "?";
		}
		Track tk = TRACK.get(selected);
		int lat = e.getLatency();

		// ---- text lines
		List<String> lines = new ArrayList<>();
		List<Integer> colours = new ArrayList<>();
		lines.add(name);
		colours.add(Theme.TEXT);
		if (isFriend(name)) {
			lines.add("Friend");
			colours.add(Theme.GREEN);
		} else if (SolarBadge.uses(selected, name)) {
			lines.add("Uses Caos");
			colours.add(Theme.accent());
		}
		String gm = "?";
		try {
			gm = e.getGameMode().getTranslatableName().getString();
		} catch (Throwable ignored) {
		}
		lines.add("Game mode: " + gm);
		colours.add(Theme.DIM);
		lines.add("Ping: " + lat + " ms");
		colours.add(pingColour(lat));
		if (tk != null && tk.size() >= 2) {
			long sum = 0, jit = 0;
			for (int i = 0; i < tk.size(); i++) {
				sum += tk.get(i);
				if (i > 0) {
					jit += Math.abs(tk.get(i) - tk.get(i - 1));
				}
			}
			lines.add("Average " + (sum / tk.size()) + " ms, jitter " + (jit / (tk.size() - 1)) + " ms");
			colours.add(Theme.DIM);
		}
		if (tk != null) {
			lines.add("Online for " + dur(System.currentTimeMillis() - tk.since));
			colours.add(Theme.DIM);
		}

		int bodyW = 40, pad = 8, lh = 11;
		int textW = 0;
		for (String s : lines) {
			textW = Math.max(textW, Gfx.tw(s));
		}
		int graphH = 16;
		int w = pad + bodyW + 8 + Math.max(110, textW) + pad;
		int h = Math.max(pad + 76 + pad, pad + lines.size() * lh + 6 + graphH + pad);

		// ---- where: right of the list, else left of it; next to the clicked row
		int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
		int x = gx0 + gTotal + 8;
		if (x + w > sw - 4) {
			x = gx0 - w - 8;
		}
		x = Math.max(4, Math.min(x, sw - w - 4));
		int rowY = gy0 + (idx % gRows) * gRowH;
		int y = Math.max(4, Math.min(rowY - 6, sh - h - 4));
		popX = x;
		popY = y;
		popW = w;
		popH = h;
		popValid = true;

		Gfx.rrectBorder(c, x, y, w, h, 4, Theme.PANEL, Theme.PANEL_BORDER);

		// ---- body (3D when he is near enough to be loaded), else his face
		int bx = x + pad, by = y + pad;
		PlayerEntity pe = mc.world == null ? null : mc.world.getPlayerByUuid(selected);
		if (!drawBody(c, pe, bx, by, bx + bodyW, by + h - pad * 2)) {
			Faces.draw(c, name, bx + (bodyW - 32) / 2, by, 32, 255);
		}

		// ---- text
		int tx = x + pad + bodyW + 8;
		for (int i = 0; i < lines.size(); i++) {
			Gfx.text(c, lines.get(i), tx, y + pad + i * lh, colours.get(i));
		}

		// ---- ping graph (last 60 s, newest on the right)
		int gx = tx, gw = w - (tx - x) - pad, gy = y + h - pad - graphH;
		c.fill(gx, gy, gx + gw, gy + graphH, 0x40000000);
		if (tk != null && tk.size() >= 2) {
			int max = 50;
			for (int i = 0; i < tk.size(); i++) {
				max = Math.max(max, tk.get(i));
			}
			int cw = Math.max(1, gw / 60);
			int acc = Theme.accent();
			for (int i = 0; i < tk.size(); i++) {
				int hh = Math.max(1, tk.get(i) * (graphH - 2) / max);
				int cx = gx + gw - (tk.size() - i) * cw;
				if (cx < gx) {
					continue;
				}
				c.fill(cx, gy + graphH - 1 - hh, cx + cw, gy + graphH - 1, (acc & 0xFFFFFF) | 0x50000000);
				c.fill(cx, gy + graphH - 1 - hh, cx + cw, gy + graphH - hh, acc);
			}
		}
	}

	/** The same renderer as the world / your Locker preview, for another player. False = not loaded or something differs. */
	private static boolean drawBody(DrawContext c, PlayerEntity p, int x1, int y1, int x2, int y2) {
		if (bodyBroken || p == null) {
			return false;
		}
		try {
			EntityRenderState st = InventoryScreenAccessor.plegumo$state(p);
			st.displayName = null;
			st.shadowPieces.clear();
			if (st instanceof LivingEntityRenderState l) {
				l.bodyYaw = 180F + 20F;
				l.relativeHeadYaw = 0F;
				l.pitch = 0F;
				l.limbSwingAmplitude = 0F;
				l.limbSwingAnimationProgress = 0F;
			}
			float entScale = p.getScale();
			float size = (y2 - y1) / 2.5F;
			float mid = (y1 + y2) / 2F;
			Vector3f tr = new Vector3f(0F, (y2 - mid) / size, 0F);
			Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).mul(new Quaternionf().rotateX((float) Math.toRadians(-8)));
			Quaternionf cam = new Quaternionf().rotateX((float) Math.toRadians(-8));
			cam.conjugate();
			c.addEntity(st, size / entScale, tr, rot, cam, x1, y1, x2, y2);
			return true;
		} catch (Throwable t) {
			bodyBroken = true;
			System.err.println("[Caos] Tab List 3D model unavailable, using the face: " + t);
			return false;
		}
	}
}
