package dev.sleepclient.mods;

import dev.sleepclient.Keys;
import dev.sleepclient.option.Option;
import dev.sleepclient.video.Mcef;
import dev.sleepclient.video.PanelBox;
import dev.sleepclient.video.Panels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Floating web browser panels in the world (Chromium): YouTube, Spotify Web, Discord, a stream, anything with a web page.
 * Press the key to put a panel in front of you. Middle button + turn your head moves it, right button on a bottom corner
 * scales it, left button clicks (and turns typing on), the title bar has back / forward / reload / address / close.
 * Client side only: nobody else sees your panels.
 */
public class BrowserMod extends Mod {

	public static BrowserMod instance;

	private final Option.Key openKey = key("New Panel Key", 66); // B
	private final Option.Key closeKey = key("Close All Key", -1);
	/** Open Apps: every app of this computer, pick one to use it on a panel. */
	private final Option.Key appsKey = key("Open Apps Key", 79); // O
	private final Option.Text home = text("Home Page", "https://www.youtube.com", 300, "https://...");
	private final Option.Num maxPanels = num("Max Panels", 4, 1, 8, 1, "");
	private final Option.Num distance = num("Spawn Distance", 3, 1.5F, 10, 0.5F, " blocks");
	private final Option.Num width = num("Spawn Width", 3.2F, 1.5F, 10, 0.1F, " blocks");
	private final Option.Col accent = colour("Handle Colour", 0xFF5B8CFF);
	private final Option.Action closeAll = action("Panels", "Close All", Panels::clear,
			() -> Panels.LIST.size() + " open | " + (Mcef.installed() ? "Engine: " + Mcef.status() : "Engine missing"));

	private final Option.Action apps = action("Open Apps (any app of this computer, live and usable)", "Choose", () -> mc.execute(() -> {
		if (!enabled) {
			setEnabled(true);
		}
		dev.sleepclient.compat.Mc.setScreen(mc, new dev.sleepclient.video.AppPickerScreen(dev.sleepclient.compat.Mc.screen(mc)));
	}), () -> "");
	private final Option.Header tips = header("Shift + scroll: tilt  |  Ctrl / Cmd + scroll: turn");

	private boolean openDown, closeDown, appsDown;

	public BrowserMod() {
		super("Browser", "browser", "Floating web browser panels in the world: videos, Spotify Web, Discord... move, scale, click and type on them.",
				Category.UTILITY);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}

	public static String home() {
		return instance == null || instance.home.get().isBlank() ? "https://www.youtube.com" : instance.home.get();
	}

	public static int accent() {
		return instance == null ? 0xFF5B8CFF : instance.accent.c();
	}

	public static double spawnDistance() {
		return instance == null ? 3.0 : instance.distance.f();
	}

	public static double spawnWidth() {
		return instance == null ? 3.2 : instance.width.f();
	}

	@Override
	protected void onEnable() {
		if (Mcef.installed()) {
			Mcef.start();
		}
	}

	@Override
	protected void onDisable() {
		Panels.clear();
	}

	@Override
	public void tick() {
		if (mc.player == null) {
			return;
		}
		boolean o = dev.sleepclient.compat.Mc.screen(mc) == null && openKey.code() > 0 && Keys.down(openKey.code());
		if (o && !openDown) {
			if (Panels.LIST.size() >= maxPanels.i()) {
				mc.player.sendOverlayMessage(Component.literal("Browser: " + maxPanels.i() + " panels already open"));
			} else {
				PanelBox p = Panels.create();
				if (p != null) {
					mc.player.sendOverlayMessage(Component.literal("Browser panel opened (" + Panels.LIST.size() + ")"));
				}
			}
		}
		openDown = o;
		boolean ap = dev.sleepclient.compat.Mc.screen(mc) == null && appsKey.code() > 0 && Keys.down(appsKey.code()) && !dev.sleepclient.video.PanelInput.typing();
		if (ap && !appsDown) {
			dev.sleepclient.compat.Mc.setScreen(mc, new dev.sleepclient.video.AppPickerScreen(null));
		}
		appsDown = ap;
		boolean c = dev.sleepclient.compat.Mc.screen(mc) == null && closeKey.code() > 0 && Keys.down(closeKey.code());
		if (c && !closeDown && !Panels.LIST.isEmpty()) {
			Panels.clear();
			mc.player.sendOverlayMessage(Component.literal("Browser panels closed"));
		}
		closeDown = c;
	}

	/** A small bar at the top while the browser engine downloads, so you can see that it is working. */
	public void drawHud(GuiGraphicsExtractor c) {
		if (!Mcef.installed() || !Mcef.started() || Mcef.ready() || dev.sleepclient.compat.Mc.hideGui(mc)) {
			return;
		}
		int w = 170, h = 22, x = mc.getWindow().getGuiScaledWidth() / 2 - w / 2, y = 6;
		c.fill(x, y, x + w, y + h, 0xD0121212);
		if (Mcef.failed()) {
			c.text(mc.font, "Browser engine failed: click a panel to retry", x + 6, y + 7, 0xFFFF8A8A, false);
			return;
		}
		float p = Mcef.progress();
		String t = Mcef.stageLabel() + (p >= 0 ? " " + Math.round(p) + "%" : "...");
		c.text(mc.font, t, x + 6, y + 3, 0xFFFFFFFF, false);
		c.fill(x + 6, y + 14, x + w - 6, y + 17, 0xFF3A3A3A);
		if (p >= 0) {
			c.fill(x + 6, y + 14, x + 6 + Math.round((w - 12) * p / 100F), y + 17, accent.c());
		} else {
			int seg = 40;
			int sx = x + 6 + (int) ((w - 12 - seg) * (0.5 - 0.5 * Math.cos((System.currentTimeMillis() % 1400) / 1400.0 * 2 * Math.PI)));
			c.fill(sx, y + 14, sx + seg, y + 17, accent.c());
		}
	}
}
