package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.social.RemoteTextures;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** A player's face by Minecraft name: from the server's player list when they are here, otherwise from mc-heads. */
public final class Faces {

	private Faces() {
	}

	/** alpha below ~230 draws the initials tile instead (skins can't be faded). */
	public static void draw(DrawContext c, String name, int x, int y, int s, int alpha) {
		if (name == null) {
			name = "";
		}
		if (alpha >= 230) {
			MinecraftClient mc = MinecraftClient.getInstance();
			try {
				var nh = mc.getNetworkHandler();
				var e = nh == null || name.isEmpty() ? null : nh.getPlayerListEntry(name);
				if (e != null) {
					net.minecraft.client.gui.PlayerSkinDrawer.draw(c, e.getSkinTextures(), x, y, s);
					return;
				}
			} catch (Throwable ignored) {
			}
			RemoteTextures.Tex t = dev.solclient.social.FaceSkins.of(name);
			if (t != null) {
				AccountsScreen.face(c, t, x, y, s);
				return;
			}
		}
		initials(c, name, x, y, s, alpha);
	}

	/** The fallback: first letter on a grey tile. */
	public static void initials(DrawContext c, String name, int x, int y, int s, int alpha) {
		Gfx.rrect(c, x, y, s, s, Math.max(1, s / 5), Gfx.withAlpha(0x3A3D49, alpha));
		String l = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
		Gfx.textRaw(c, l, x + s / 2 - Gfx.twRaw(l) / 2, y + s / 2 - 4, Gfx.withAlpha(0xD6D3D1, alpha));
	}
}
