package dev.solclient.mods;

import dev.solclient.Config;
import dev.solclient.Gfx;
import dev.solclient.mixin.social.MultiplayerScreenAccessor;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import dev.solclient.Lang;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;

/**
 * Server Favourites: select a server in the Multiplayer list and press the star button on the left of "Join" to
 * favourite / unfavourite it. Favourites go to the top of the list and stay there. Saved by address, so renaming a
 * server keeps its star.
 */
public class ServerFavouritesMod extends Mod {

	public static ServerFavouritesMod instance;

	private static final Identifier STAR_OUT = Identifier.of("sol_client", "textures/gui/icon/star_outline.png");
	private static final Identifier STAR_FILL = Identifier.of("sol_client", "textures/gui/icon/star_filled.png");

	private final Option.Bool toTop = bool("Favourites On Top", true);
	private static ButtonWidget currentStar;
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
			if (!(screen instanceof MultiplayerScreen ms) || !active()) {
				return;
			}
			try {
				sort(ms);
				int bx = Math.max(2, w / 2 - 154 - 24), by = h - 52;
				for (var wd : net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen)) {
					if (wd instanceof ButtonWidget bw && bw.getMessage().getContent() instanceof TranslatableTextContent t && "selectServer.select".equals(t.getKey())) {
						bx = Math.max(2, bw.getX() - 24);
						by = bw.getY();
						break;
					}
				}
				final ServerInfo[] cur = new ServerInfo[1];
				ButtonWidget star = ButtonWidget.builder(Text.empty(), b -> {
					if (cur[0] != null) {
						toggle(cur[0]);
					}
				}).dimensions(bx, by, 20, 20).build();
				star.active = false;
				currentStar = star;
				net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(star);
				final boolean[] lastFav = {false};
				final boolean[] first = {true};
				net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((sc, ctx, mx, my, delta) -> {
					if (star != currentStar) {
						return; // the screen was rebuilt (resize): this is a stale button, drawing it leaves a ghost square
					}
					ServerInfo sel = selected(ms);
					cur[0] = sel;
					star.active = sel != null;
					boolean fav = sel != null && isFav(sel);
					if (first[0] || fav != lastFav[0]) {
						first[0] = false;
						lastFav[0] = fav;
						star.setTooltip(Tooltip.of(Text.literal(fav ? Lang.tr("Remove from favourites", "Tirar dos favoritos") : Lang.tr("Add to favourites", "Adicionar aos favoritos"))));
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
	private static void markFavourites(MultiplayerScreen ms, net.minecraft.client.gui.DrawContext ctx) {
		try {
			var lw = ((MultiplayerScreenAccessor) (Object) ms).kubo$serverListWidget();
			int col = instance.starColour.c() | 0xFF000000;
			for (Object o : lw.children()) {
				if (!(o instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget.ServerEntry e) || !isFav(e.getServer())) {
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

	private static ServerInfo selected(MultiplayerScreen ms) {
		try {
			var lw = ((MultiplayerScreenAccessor) (Object) ms).kubo$serverListWidget();
			if (lw.getSelectedOrNull() instanceof net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget.ServerEntry e) {
				return e.getServer();
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	public static boolean active() {
		return instance != null && instance.enabled;
	}

	private static String key(ServerInfo s) {
		return s == null || s.address == null ? "" : s.address.trim().toLowerCase(Locale.ROOT);
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

	public static boolean isFav(ServerInfo s) {
		return active() && !key(s).isEmpty() && instance.set().contains(key(s));
	}

	public static void toggle(ServerInfo s) {
		if (!active() || key(s).isEmpty()) {
			return;
		}
		Set<String> f = instance.set();
		if (!f.remove(key(s))) {
			f.add(key(s));
		}
		instance.list.set(String.join(",", f));
		Config.save();
		if (net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof MultiplayerScreen ms) {
			sort(ms);
		}
	}

	/** Moves the starred servers to the top of the list (keeps the order inside each group) and refreshes the widget. */
	public static void sort(MultiplayerScreen screen) {
		if (!active() || !instance.toTop.on() || instance.list.get().isBlank()) {
			return;
		}
		try {
			ServerList sl = screen.getServerList();
			int n = sl.size();
			List<ServerInfo> now = new ArrayList<>(), want = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				now.add(sl.get(i));
			}
			for (ServerInfo s : now) {
				if (isFav(s)) {
					want.add(s);
				}
			}
			for (ServerInfo s : now) {
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
					sl.swapEntries(i, j);
					changed = true;
				}
			}
			if (changed) {
				sl.saveFile();
				((MultiplayerScreenAccessor) (Object) screen).kubo$serverListWidget().setServers(sl);
			}
		} catch (Throwable e) {
			System.err.println("[Sleep] server favourites: " + e);
		}
	}
}
