package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.SleepClient;
import dev.sleepclient.emote.EmoteLibrary;
import dev.sleepclient.emote.EmoteManager;
import dev.sleepclient.emote.EmoteSlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Emote wheel: the ring with your 8 equipped emotes, each shown in 3D (playing while the mouse is over it) (equip / swap them in the Locker: Right Shift > Locker > Emotes).
 * Opens / closes with the Emote Wheel key (B by default) or Esc. Keys 1-8 play a slot; an empty slot opens the Locker.
 */
public class EmoteWheelScreen extends Screen {

	public static final int SLOTS = 8;

	private static final int TEX = 1024;
	private static final float TEX_HALF = 512F, TEX_R = 504F;
	private static final Identifier[] SEG = new Identifier[SLOTS], ARC = new Identifier[SLOTS];

	static {
		for (int i = 0; i < SLOTS; i++) {
			SEG[i] = Gfx.id("textures/gui/wheel/seg" + i + ".png");
			ARC[i] = Gfx.id("textures/gui/wheel/arc" + i + ".png");
		}
	}

	public EmoteWheelScreen() {
		super(Component.literal("Emote Wheel"));
	}

	private static EmoteLibrary.Info slotInfo(int slot) {
		return EmoteSlots.get(slot);
	}

	private void play(int slot) {
		EmoteLibrary.Info info = slotInfo(slot);
		if (info != null) {
			closeWheel();
			EmoteManager.play(info);
		} else {
			// empty slot: pick an emote for it in the Locker
			SleepClient.emoteArmed = false;
			dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), SolMenuScreen.openEmoteSlot(slot));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void closeWheel() {
		SleepClient.emoteArmed = false;
		dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), null);
	}

	// ---------------------------------------------------------------- geometry
	private float cx() {
		return width / 2F;
	}

	private float cy() {
		return height / 2F + height * 0.02F;
	}

	private float rOut() {
		return Math.min(width, height) * 0.30F;
	}

	private float rIn() {
		return rOut() * 0.41F;
	}

	/** Segment under the cursor (0 = top, clockwise), or -1. */
	private int hovered(double mx, double my) {
		double dx = mx - cx(), dy = my - cy();
		double d = Math.sqrt(dx * dx + dy * dy);
		if (d < rIn() || d > rOut()) {
			return -1;
		}
		return segmentAt(dx, dy);
	}

	private static int segmentAt(double dx, double dy) {
		double a = Math.atan2(dx, -dy);
		if (a < 0) {
			a += Math.PI * 2;
		}
		return ((int) Math.floor((a + Math.PI / 8) / (Math.PI / 4))) % SLOTS;
	}

	// ----------------------------------------------------------------- drawing
	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
		super.extractRenderState(c, mouseX, mouseY, delta); // vanilla menu background (blur + darkening)

		float cx = cx(), cy = cy(), ro = rOut(), ri = rIn();
		int accent = Theme.accent();
		int hov = hovered(mouseX, mouseY);

		// ring: pre-baked segment textures (a handful of quads instead of thousands of 1px fills)
		int base = 0xD00F1014;
		int hoverCol = Gfx.blend(0xD01A1C24, accent, 0.22F);
		float s = Gfx.scale();
		int half = Math.round(ro * s * TEX_HALF / TEX_R);
		int ox = Math.round(cx * s) - half, oy = Math.round(cy * s) - half, sz = half * 2;
		c.pose().pushMatrix();
		c.pose().scale(1F / s, 1F / s);
		for (int i = 0; i < SLOTS; i++) {
			c.blit(RenderPipelines.GUI_TEXTURED, SEG[i], ox, oy, 0F, 0F, sz, sz, TEX, TEX, TEX, TEX, i == hov ? hoverCol : base);
		}
		// (no blue line on the hovered segment any more: the segment itself is lit)
		c.pose().popMatrix();

		// slot numbers (on the clockwise-start edge of each slot) and slot markers
		float rm = (ri + ro) / 2F;
		for (int i = 0; i < SLOTS; i++) {
			double nb = Math.toRadians(i * 45.0 - 22.5 + 7.0);
			float nr = ro - ro * 0.11F;
			Gfx.textC(c, String.valueOf(i + 1), Math.round(cx + nr * (float) Math.sin(nb)), Math.round(cy - nr * (float) Math.cos(nb)) - 4, 0x66FFFFFF);
			double sa = Math.toRadians(i * 45.0);
			float sx = cx + rm * (float) Math.sin(sa), sy = cy - rm * (float) Math.cos(sa);
			EmoteLibrary.Info info = slotInfo(i);
			if (info == null) {
				if (i == hov) {
					Gfx.textC(c, "+", Math.round(sx), Math.round(sy) - 4, 0xAAFFFFFF);
				} else {
					Gfx.circle(c, sx, sy, Math.max(2F, ro * 0.014F), 0x22FFFFFF);
				}
			} else {
				// the emote in 3D (the same mannequin as the Locker), playing while the mouse is over it; its name under it
				int isz = Math.round(Math.min(ro - ri, rm * 0.78F) * (i == hov ? 0.80F : 0.70F));
				int fy = Math.round(sy - isz / 2F - ro * 0.04F);
				boolean drawn = dev.sleepclient.cosmetic.SkinPreview.drawEmoteCard(c, info, i == hov, Math.round(sx - isz / 2F), fy, isz, isz);
				if (!drawn && info.icon()) {
					Gfx.tex(c, info.iconId(), Math.round(sx - isz / 2F), fy, isz, isz, 256, 256, 0xFFFFFFFF);
					drawn = true;
				}
				String n = info.name();
				n = n.length() > 11 ? n.substring(0, 10) + "." : n;
				if (drawn) {
					Gfx.textC(c, n, Math.round(sx), fy + isz - 2, i == hov ? 0xFFFFFFFF : 0xCCFFFFFF);
				} else {
					Gfx.textC(c, n, Math.round(sx), Math.round(sy) - 4, 0xFFFFFFFF);
				}
			}
			// (no dot under the emote that is playing)
		}

		// logo in the middle
		int logo = Math.round(ri * 1.15F);
		Gfx.tex(c, Gfx.id("icon.png"), Math.round(cx - logo / 2F), Math.round(cy - logo / 2F), logo, logo, 256, 256, 0xFFFFFFFF);

		// name of the hovered emote
		if (hov >= 0) {
			EmoteLibrary.Info hi = slotInfo(hov);
			Gfx.textC(c, hi != null ? hi.name() : "Empty slot - click to equip an emote", Math.round(cx), Math.round(cy + ro + ro * 0.18F),
					hi != null ? 0xFFFFFFFF : Theme.DIM);
		}
	}

	// ------------------------------------------------------------------- input
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click.button() == 0) {
			double dx = click.x() - cx(), dy = click.y() - cy();
			double d = Math.sqrt(dx * dx + dy * dy);
			if (d < rIn()) {
				EmoteManager.stop();
				closeWheel();
				return true;
			}
			int h = hovered(click.x(), click.y());
			if (h >= 0) {
				play(h);
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (k >= 49 && k <= 56) {
			play(k - 49);
			return true;
		}
		if (input.key() == SleepClient.settings.emoteKey.code()) {
			closeWheel();
			return true;
		}
		return super.keyPressed(input); // Esc closes
	}
}
