package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.SolClient;
import dev.solclient.cosmetic.PlayerPreview;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Classic + Caos" pause menu (Settings > Menus > Pause Menu Style). It is Minecraft's real pause screen, so everything
 * other mods add to it (Essential's Invite / Social / Wardrobe..., Mod Menu's button) is still there, drawn in the Caos
 * style. Caos adds on top: your 3D player on the left (with a Locker button), a column of Caos buttons on the right
 * (or a row of icon buttons under the menu when Essential already uses the right side) and the logo in the corner.
 */
public final class PauseClassic {

	private PauseClassic() {
	}

	private record Item(String label, String icon, Runnable run) {
	}

	/** Called at the end of GameMenuScreen.init (see PauseClassicMixin). */
	public static void build(Screen screen, int width, int height, List<? extends GuiEventListener> children,
			Consumer<Button> addButton, Consumer<Renderable> addDrawable) {
		Minecraft mc = Minecraft.getInstance();
		var st = SolClient.settings;
		// the Multiplayer button goes in first (above Mods): everything below it moves down one row before the Caos icons are placed
		PauseMultiplayerButton.insert(screen, children, addButton);
		if (st == null || !st.customPause.on() || !st.classicPause() || mc.level == null) {
			return;
		}

		// where the vanilla column is (only the buttons in the middle: Essential / Mod Menu sit on the sides)
		int cx = width / 2, minY = Integer.MAX_VALUE, maxY = 0, minX = cx - 102, maxX = cx + 102;
		for (Object o : children) {
			if (o instanceof AbstractButton w && w.getX() >= cx - 110 && w.getX() + w.getWidth() <= cx + 110) {
				minY = Math.min(minY, w.getY());
				maxY = Math.max(maxY, w.getY() + w.getHeight());
			}
		}
		if (minY == Integer.MAX_VALUE) {
			return; // no menu (F3 + Esc)
		}

		boolean sp = mc.isLocalServer();
		List<Item> items = new ArrayList<>();
		items.add(new Item(Lang.tr("Caos Menu", "Menu Caos"), "sol_client", () -> dev.solclient.compat.Mc.setScreen(mc, SolMenuScreen.open(SolMenuScreen.VIEW_MODS))));
		items.add(new Item(Lang.tr("Shop", "Loja"), "shop", () -> dev.solclient.compat.Mc.setScreen(mc, SolMenuScreen.open(SolMenuScreen.VIEW_SHOP))));
		items.add(new Item(Lang.tr("Capes & Skins", "Capas e Skins"), "skin_editor", () -> dev.solclient.compat.Mc.setScreen(mc, SolMenuScreen.open(SolMenuScreen.VIEW_CAPES))));
		if (sp && dev.solclient.mods.HostWorldMod.on()) {
			items.add(new Item(dev.solclient.social.HostWorld.hosting() ? Lang.tr("Hosting", "A partilhar") : Lang.tr("Host World", "Partilhar mundo"), "host_world",
					() -> dev.solclient.compat.Mc.setScreen(mc, new HostWorldScreen(screen, dev.solclient.social.HostWorld.hosting()))));
		}
		if (ModMenuLink.preferred() && !modMenuButtonExists(children)) {
			items.add(new Item(Lang.tr("Mods", "Mods"), "mods", () -> ModMenuLink.open(screen)));
		}

		List<int[]> iconSpots = new ArrayList<>(); // x, y, w, h, item index
		boolean essential = ModMenuLink.essentialPresent();
		int bh = 22, gap = 4;
		int rightFree = width - 8 - (maxX + 24);
		final int panel = (UiScreen.TILE & 0xFFFFFF) | 0xCC000000;
		boolean column = !essential && rightFree >= 96;
		if (column) {
			// a tidy card on the right: KUBO title, then one button per line (icon, name)
			int kw = Math.min(120, rightFree - 16), kx = maxX + 24 + 8, ky = minY + 20;
			final int cardX = kx - 8, cardY = minY - 4, cardW = kw + 16, cardH = 24 + items.size() * (bh + gap) + 6;
			addDrawable.accept((GuiGraphicsExtractor c, int mx, int my, float delta) -> {
				Gfx.rrectBorder(c, cardX, cardY, cardW, cardH, 8, panel, Theme.PANEL_BORDER);
				Gfx.tex(c, Theme.icon2("sol_client"), cardX + 9, cardY + 6, 12, 12, 64, 64, 0xFFFFFFFF);
				Gfx.text(c, "CAOS", cardX + 25, cardY + 9, Theme.TEXT);
				c.fill(cardX + 8, cardY + 22, cardX + cardW - 8, cardY + 23, 0x1CFFFFFF);
			});
			for (int i = 0; i < items.size(); i++) {
				Item it = items.get(i);
				int y = ky + i * (bh + gap);
				addButton.accept(Button.builder(Component.empty(), b -> it.run().run()).bounds(kx, y, kw, bh).build());
				iconSpots.add(new int[] {kx + 7, y + (bh - 14) / 2, 14, 14, i, kx + 27, kw - 34, y + (bh - 8) / 2});
			}
		} else {
			// icon row under the vanilla buttons (never touches what other mods put on the sides)
			int n = items.size(), bs = 22, total = n * bs + (n - 1) * gap, x0 = cx - total / 2, y = maxY + 22; // clearly under the buttons
			addDrawable.accept((GuiGraphicsExtractor c, int mx, int my, float delta) ->
					Gfx.rrectBorder(c, x0 - 7, y - 6, total + 14, bs + 12, 8, panel, Theme.PANEL_BORDER));
			for (int i = 0; i < n; i++) {
				Item it = items.get(i);
				int x = x0 + i * (bs + gap);
				addButton.accept(Button.builder(Component.empty(), b -> it.run().run()).bounds(x, y, bs, bs)
						.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(it.label()))).build());
				iconSpots.add(new int[] {x + 4, y + 4, 14, 14, i, 0, 0, 0});
			}
		}
		final List<Item> fItems = items;
		addDrawable.accept((GuiGraphicsExtractor c, int mx, int my, float delta) -> {
			for (int[] s : iconSpots) {
				Gfx.tex(c, Theme.icon2(fItems.get(s[4]).icon()), s[0], s[1], s[2], s[3], 64, 64, 0xFFFFFFFF);
				if (s[5] != 0) {
					Gfx.text(c, fitText(fItems.get(s[4]).label(), s[6]), s[5], s[7], 0xFFFFFFFF);
				}
			}
			// logo + name, bottom-right
			float ts = 1.9F;
			int logo = 28, tw = Math.round(Gfx.tw("CAOS") * ts), bx = width - tw - logo - 22, by = height - logo - 10;
			Gfx.tex(c, Theme.icon2("sol_client"), bx, by, logo, logo, 64, 64, 0xFFFFFFFF);
			var mm = c.pose();
			mm.pushMatrix();
			mm.translate(bx + logo + 6, by + (logo - 8 * ts) / 2F + 1);
			mm.scale(ts, ts);
			Gfx.text(c, "CAOS", 0, 0, Theme.TEXT);
			mm.popMatrix();
		});

		// your player on the left, in a card, with the Locker button inside it
		int leftFree = minX - 12;
		if (st.pausePlayer.on() && leftFree >= 70 && !essential) { // with Essential the sides belong to Essential: Caos only adds its icon row
			int pw = Math.min(90, leftFree - 20), px = Math.max(10, minX / 2 - pw / 2 - 6);
			int top = Math.max(46, minY + 14), feet = Math.min(height - 70, top + Math.min(190, Math.round((height - top - 90) * 0.9F)));
			if (feet - top >= 70) {
				final int fx = px, fp = pw, ft = top, ff = feet;
				final int cardX = Math.max(4, fx - 18), cardW = Math.min(fp + 36, minX - 8 - Math.max(4, fx - 18)), cardY = ft - 36, cardH = ff - ft + 36 + 40;
				final String nick = mc.getUser().getName();
				addDrawable.accept((GuiGraphicsExtractor c, int mx, int my, float delta) -> {
					Gfx.rrectBorder(c, cardX, cardY, cardW, cardH, 8, panel, Theme.PANEL_BORDER);
					Gfx.textFit(c, nick, cardX + cardW / 2, cardW - 12, cardY + 9, Theme.TEXT);
					c.fill(cardX + 8, cardY + 22, cardX + cardW - 8, cardY + 23, 0x1CFFFFFF);
					float yaw = Math.max(-40F, Math.min(40F, (mx - (cardX + cardW / 2F)) * 0.35F));
					PlayerPreview.draw(c, cardX + 4, ft - 8, cardX + cardW - 4, ff, yaw, null);
				});
				addButton.accept(Button.builder(Component.literal(Lang.tr("Locker", "Cacifo")), b -> dev.solclient.compat.Mc.setScreen(mc, SolMenuScreen.open(SolMenuScreen.VIEW_SHOP)))
						.bounds(cardX + 8, ff + 8, cardW - 16, 22).build());
			}
		}
	}

	private static String fitText(String t, int maxW) {
		if (Gfx.tw(t) <= maxW) {
			return t;
		}
		while (t.length() > 1 && Gfx.tw(t + "...") > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "...";
	}

	/** Mod Menu already put its own button in this screen (its "icon" / "below buttons" style): then Caos doesn't add a second one. */
	private static boolean modMenuButtonExists(List<? extends GuiEventListener> children) {
		for (Object o : children) {
			if (o.getClass().getName().startsWith("com.terraformersmc.modmenu")) {
				return true;
			}
			if (o instanceof AbstractButton w && w.getMessage() != null && w.getMessage().getString().toLowerCase().startsWith("mods")) {
				return true;
			}
		}
		return false;
	}
}
