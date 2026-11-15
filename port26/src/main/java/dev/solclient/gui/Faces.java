package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.social.RemoteTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A player's face by Minecraft name: from the server's player list when they are here, otherwise from mc-heads. */
public final class Faces {

	private Faces() {
	}

	/** alpha below ~230 draws the initials tile instead (skins can't be faded). */
	public static void draw(GuiGraphicsExtractor c, String name, int x, int y, int s, int alpha) {
		if (name == null) {
			name = "";
		}
		if (alpha >= 230) {
			Minecraft mc = Minecraft.getInstance();
			try {
				var nh = mc.getConnection();
				var e = nh == null || name.isEmpty() ? null : nh.getPlayerInfo(name);
				if (e != null) {
					net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(c, e.getSkin(), x, y, s);
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

	/**
	 * The player in 3D (the whole body, standing) inside x..x+w / y..y+h, from the same skin source as the face. False when there is no skin
	 * yet (the caller draws the face instead). Each name has its own model: the menu draws later in the frame.
	 */
	public static boolean body(GuiGraphicsExtractor c, String name, int x, int y, int w, int h) {
		if (name == null || name.isEmpty()) {
			return false;
		}
		try {
			net.minecraft.resources.Identifier skin = null;
			boolean slim = false;
			var nh = Minecraft.getInstance().getConnection();
			var e = nh == null ? null : nh.getPlayerInfo(name);
			if (e != null) {
				skin = e.getSkin().body().texturePath();
				slim = String.valueOf(e.getSkin().model()).toLowerCase(java.util.Locale.ROOT).contains("slim");
			} else {
				RemoteTextures.Tex t = dev.solclient.social.FaceSkins.of(name);
				if (t != null) {
					skin = t.id();
				}
			}
			return skin != null && dev.solclient.cosmetic.SkinPreview.drawWearCard(c, "lb:" + name.toLowerCase(java.util.Locale.ROOT), skin, slim, java.util.List.of(), x, y, w, h);
		} catch (Throwable t) {
			return false;
		}
	}

	/** The fallback: first letter on a grey tile. */
	public static void initials(GuiGraphicsExtractor c, String name, int x, int y, int s, int alpha) {
		Gfx.rrect(c, x, y, s, s, Math.max(1, s / 5), Gfx.withAlpha(0x3A3D49, alpha));
		String l = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
		Gfx.textRaw(c, l, x + s / 2 - Gfx.twRaw(l) / 2, y + s / 2 - 4, Gfx.withAlpha(0xD6D3D1, alpha));
	}
}
