package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.parkour.ParkourLive;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * "LEVEL X / 100" of the Parkour Paradise 3 map (Parkour Live), with the JAIL timer under it while you are locked up.
 * A normal HUD element: move it and resize it in the HUD editor. Only shows in the parkour map.
 */
public class ParkourLevelMod extends HudMod {

	/** Size of the level box inside the element (the HUD editor's Scale goes on top). */
	private static final float K = 1.5F;
	private static final String WIDEST = "LEVEL 100 / 100";

	public ParkourLevelMod() {
		super("Parkour Level", "parkour_level", "LEVEL X / 100 and the JAIL timer in the Parkour Paradise 3 map (Parkour Live). Move it in the HUD editor.",
				0.40F, 0.06F);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public boolean visible() {
		return ParkourLive.showing();
	}

	private int boxW() {
		return mc.textRenderer.getWidth(WIDEST) + 10;
	}

	@Override
	public int width() {
		return Math.round(boxW() * K);
	}

	@Override
	public int height() {
		return Math.round(13 * K) + 26; // level box + room for the JAIL timer
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		var tr = mc.textRenderer;
		long now = System.currentTimeMillis();
		int lv = edit && !ParkourLive.showing() ? 35 : ParkourLive.level();
		String s = "LEVEL " + lv + " / 100";
		int bw = boxW(), bh = 13, tw = tr.getWidth(s);
		float age = (now - ParkourLive.levelChangedAt()) / 450F;
		float pop = age >= 0 && age < 1 ? 0.15F * (float) Math.sin(Math.PI * age) : 0F;
		float sc = K * (1 + pop);
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(width() / 2F, 13 * K / 2F);
		c.getMatrices().scale(sc, sc);
		c.getMatrices().translate(-bw / 2F, -bh / 2F);
		Gfx.fill(c, 0, 0, bw, bh, 0x99181C24);
		Gfx.fill(c, 0, 0, 2, bh, 0xFF35D0F5); // the blue edge: left side only
		// a shine passes over the box when the level changes
		float shine = (now - ParkourLive.levelChangedAt()) / 700F;
		if (shine >= 0 && shine < 1) {
			int sx = (int) ((bw + 16) * shine) - 8;
			for (int i = 0; i < 6; i++) {
				int a = (int) (90 * (1 - Math.abs(i - 2.5F) / 3F));
				Gfx.fill(c, Math.max(0, sx + i), 0, Math.min(bw, sx + i + 1), bh, Gfx.withAlpha(0xFFFFFF, Math.max(0, a)));
			}
		}
		c.drawText(tr, Text.literal(s), (bw - tw) / 2 + 1, 3, 0xFFD8DCE4, true);
		c.getMatrices().popMatrix();
		// JAIL: just the text, no background
		int jail = edit && !ParkourLive.showing() ? 14 : ParkourLive.jailLeft();
		if (jail > 0) {
			String j = "JAIL " + jail + "s";
			Text t = Text.literal(j).styled(st -> st.withBold(true));
			int jw = tr.getWidth(t);
			c.getMatrices().pushMatrix();
			c.getMatrices().translate(width() / 2F, 13 * K + 5);
			c.getMatrices().scale(2F, 2F);
			c.drawText(tr, t, -jw / 2, 0, 0xFFFF5555, true);
			c.getMatrices().popMatrix();
		}
	}
}
