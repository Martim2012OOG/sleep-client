package dev.sleepclient.mods;

import dev.sleepclient.Config;
import dev.sleepclient.Gfx;
import dev.sleepclient.mixin.social.MultiplayerScreenAccessor;
import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import dev.sleepclient.Lang;

/**
 * Server Favourites: select a server in the Multiplayer list and press the star button on the left of "Join" to
 * favourite / unfavourite it. Favourites go to the top of the list and stay there. Saved by address, so renaming a
 * server keeps its star.
 */
public class ServerFavouritesMod extends Mod {

	public static ServerFavouritesMod instance;

	private static final Identifier STAR_OUT = Identifier.fromNamespaceAndPath("sleep_client", "textures/gui/icon/star_outline.png");
	private static final Identifier STAR_FILL = Identifier.fromNamespaceAndPath("sleep_client", "textures/gui/icon/star_filled.png");

	private final Option.Bool toTop = bool("Favourites On Top", true);
	private static Button currentStar;
	private final Option.Col starColour = colour("Star Colour", 0xFFFFC83D);
	private final Option.Text list = text("Favourites", "", 6000, "addresses, separated by commas");

	public ServerFavouritesMod() {
		super("Server Favourites", "server_favourites", "A star button next to Join in the Multiplayer list: favourite a server and keep it at the top.", Category.UTILITY);
		instance = this;
		list.showIf(() -> false);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	private static boolean listening;

	/** Adds the star button to the Multiplayer screen (Fabric screen events: no mixins into the vanilla rows). */
	@Override
	public void init() {
		super.init();
		if (listening) {
			return;
		}
		listening = true;
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof JoinMultiplayerScreen ms) || !active()) {
				return;
			}
			try {
				sort(ms);
				int bx = Math.max(2, w / 2 - 154 - 24), by = h - 52;
				for (var wd : net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen)) {
					if (wd instanceof Button bw && bw.getMessage().getContents() instanceof TranslatableContents t && "selectServer.select".equals(t.getKey())) {
						bx = Math.max(2, bw.getX() - 24);
						by = bw.getY();
						break;
					}
				}
				final ServerData[] cur = new ServerData[1];
				Button star = Button.builder(Component.empty(), b -> {
					if (cur[0] != null) {
						toggle(cur[0]);
					}
				}).bounds(bx, by, 20, 20).build();
				star.active = false;
				currentStar = star;
				net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(star);
				final boolean[] lastFav = {false};
				final boolean[] first = {true};
				net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register((sc, ctx, mx, my, delta) -> {
					if (star != currentStar) {
						return; // the screen was rebuilt (resize): this is a stale button, drawing it leaves a ghost square
					}
					ServerData sel = selected(ms);
					cur[0] = sel;
					star.active = sel != null;
					boolean fav = sel != null && isFav(sel);
					if (first[0] || fav != lastFav[0]) {
						first[0] = false;
						lastFav[0] = fav;
						star.setTooltip(Tooltip.create(Component.literal(fav ? Lang.tr("Remove from favourites", "Tirar dos favoritos") : Lang.tr("Add to favourites", "Adicionar aos favoritos"))));
					}
					int col = !star.active ? 0x55FFFFFF : fav ? (instance.starColour.c() | 0xFF000000) : star.isHovered() ? 0xFFFFFFFF : 0xDDFFFFFF;
					Gfx.tex(ctx, fav ? STAR_FILL : STAR_OUT, star.getX() + 2, star.getY() + 2, 16, 16, 64, 64, col);
					markFavourites(ms, ctx);
				});
			} catch (Throwable t) {
				System.err.println("[Sleep] Server Favourites button failed: " + t);
			}
		});
		System.out.println("[Sleep] Server Favourites: star button ready");
	}

	/** A gold star to the left of every favourite server in the list (only the favourites get one). */
	private static void markFavourites(JoinMultiplayerScreen ms, net.minecraft.client.gui.GuiGraphicsExtractor ctx) {
		try {
			var lw = ((MultiplayerScreenAccessor) (Object) ms).kubo$serverListWidget();
			int col = instance.starColour.c() | 0xFF000000;
			for (Object o : lw.children()) {
				if (!(o instanceof net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.OnlineServerEntry e) || !isFav(e.getServerData())) {
					continue;
				}
				int x = e.getContentX(), y = e.getContentY(), h = e.getContentHeight();
				if (y < lw.getY() || y + h > lw.getBottom()) {
					continue; // scrolled out of the list
				}
				Gfx.tex(ctx, STAR_FILL, Math.max(2, x - 20), y + (h - 14) / 2, 14, 14, 64, 64, col);
			}
		} catch (Throwable ignored) {
		}
	}

	private static ServerData selected(JoinMultiplayerScreen ms) {
		try {
			var lw = ((MultiplayerScreenAccessor) (Object) ms).kubo$serverListWidget();
			if (lw.getSelected() instanceof net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.OnlineServerEntry e) {
				return e.getServerData();
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	public static boolean active() {
		return instance != null && instance.enabled;
	}

	private static String key(ServerData s) {
		return s == null || s.ip == null ? "" : s.ip.trim().toLowerCase(Locale.ROOT);
	}

	private Set<String> set() {
		Set<String> out = new LinkedHashSet<>();
		for (String p : list.get().split(",")) {
			if (!p.isBlank()) {
				out.add(p.trim().toLowerCase(Locale.ROOT));
			}
		}
		return out;
	}

	public static boolean isFav(ServerData s) {
		return active() && !key(s).isEmpty() && instance.set().contains(key(s));
	}

	public static void toggle(ServerData s) {
		if (!active() || key(s).isEmpty()) {
			return;
		}
		Set<String> f = instance.set();
		if (!f.remove(key(s))) {
			f.add(key(s));
		}
		instance.list.set(String.join(",", f));
		Config.save();
		if (dev.sleepclient.compat.Mc.screen(net.minecraft.client.Minecraft.getInstance()) instanceof JoinMultiplayerScreen ms) {
			sort(ms);
		}
	}

	/** Moves the starred servers to the top of the list (keeps the order inside each group) and refreshes the widget. */
	public static void sort(JoinMultiplayerScreen screen) {
		if (!active() || !instance.toTop.on() || instance.list.get().isBlank()) {
			return;
		}
		try {
			ServerList sl = screen.getServers();
			int n = sl.size();
			List<ServerData> now = new ArrayList<>(), want = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				now.add(sl.get(i));
			}
			for (ServerData s : now) {
				if (isFav(s)) {
					want.add(s);
				}
			}
			for (ServerData s : now) {
				if (!isFav(s)) {
					want.add(s);
				}
			}
			boolean changed = false;
			for (int i = 0; i < n; i++) {
				int j = -1;
				for (int k = i; k < n; k++) {
					if (sl.get(k) == want.get(i)) {
						j = k;
						break;
					}
				}
				if (j > i) {
					sl.swap(i, j);
					changed = true;
				}
			}
			if (changed) {
				sl.save();
				((MultiplayerScreenAccessor) (Object) screen).kubo$serverListWidget().updateOnlineServers(sl);
			}
		} catch (Throwable e) {
			System.err.println("[Sleep] server favourites: " + e);
		}
	}
}
