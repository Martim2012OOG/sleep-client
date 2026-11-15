package dev.solclient.gui;

import dev.solclient.Gfx;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Plegumo pause menu (Esc), as drawn by the user: logo, Back to Game, Leave Server, and three icons
 * (normal game settings, the Right Shift menu, shop). Client logo + name in the bottom-right corner.
 */
public class PlegumoPauseScreen extends UiScreen {

	/** Kept for Menus (no longer set: the vanilla menu button was removed). */
	public static boolean allowVanilla;

	public PlegumoPauseScreen() {
		super("Game Menu");
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor c, float delta) {
		Gfx.gradientV(c, 0, 0, width, height, 0x20000000, 0x80000000);
		int cx = width / 2;
		int slide = 0; // no intro motion

		int bw = Math.min(200, width / 3), bh = 20;
		int logoSize = Math.max(44, Math.min(72, height / 6));
		int colH = logoSize + 14 + bh * 2 + 6 + 12 + 24;
		int top = height / 2 - colH / 2 - 10 + slide;
		logo(c, cx, top + logoSize / 2, logoSize);
		int by = top + logoSize + 14;

		button(c, "back", cx - bw / 2, by, bw, bh, "Back to Game", () -> dev.solclient.compat.Mc.setScreen(mc, null));
		boolean sp = mc.isLocalServer();
		button(c, "leave", cx - bw / 2, by + bh + 6, bw, bh, sp ? "Save and Quit" : "Leave Server", this::leave);

		int is = 20, ig = 6, iy = by + 2 * bh + 16;
		boolean host = sp && dev.solclient.mods.HostWorldMod.on();
		boolean mm = ModMenuLink.preferred();
		int icons = 3 + (host ? 1 : 0) + (mm ? 1 : 0);
		int ix = cx - (icons * is + (icons - 1) * ig) / 2;
		iconButton(c, "set", ix, iy, is, "settings", "Settings", () -> dev.solclient.compat.Mc.setScreen(mc, new OptionsScreen(this, mc.options, false)));
		iconButton(c, "mods", ix + is + ig, iy, is, "sol_client", "Sleep menu (Right Shift)", () -> dev.solclient.compat.Mc.setScreen(mc, SolMenuScreen.open(SolMenuScreen.VIEW_MODS)));
		iconButton(c, "shop", ix + 2 * (is + ig), iy, is, "shop", "Shop", () -> dev.solclient.compat.Mc.setScreen(mc, SolMenuScreen.open(SolMenuScreen.VIEW_SHOP)));
		int nx = 3;
		if (mm) {
			iconButton(c, "modmenu", ix + nx * (is + ig), iy, is, "mods", "Mod Menu", () -> ModMenuLink.open(this));
			nx++;
		}
		if (host) {
			iconButton(c, "host", ix + nx * (is + ig), iy, is, "host_world",
					dev.solclient.social.HostWorld.hosting() ? "Hosting - invite friends" : "Host World (play with friends)",
					() -> dev.solclient.compat.Mc.setScreen(mc, new HostWorldScreen(this, dev.solclient.social.HostWorld.hosting())));
		}

		// server line under the icons
		ServerData s = mc.getCurrentServer();
		String where = sp ? (dev.solclient.social.HostWorld.hosting() ? "Hosting · " + dev.solclient.social.HostWorld.address() : "Singleplayer")
				: s != null ? s.ip : "";
		Gfx.textC(c, fit(where, bw), cx, iy + is + 12, Theme.DIM);

		// bottom-right: client logo and name
		brand(c, width - Gfx.tw("SLEEP") - 30, height - 24);
	}

	private void leave() {
		boolean sp = mc.isLocalServer();
		if (mc.level != null) {
			mc.level.disconnect(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
		}
		if (sp) {
			mc.disconnectWithSavingScreen();
			dev.solclient.compat.Mc.setScreen(mc, new TitleScreen());
		} else {
			mc.disconnectWithProgressScreen();
			dev.solclient.compat.Mc.setScreen(mc, new JoinMultiplayerScreen(new TitleScreen()));
		}
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.key() == 256) {
			dev.solclient.compat.Mc.setScreen(mc, null);
			return true;
		}
		return super.keyPressed(input);
	}
}
