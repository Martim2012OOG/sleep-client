package dev.solclient.mods;

import dev.solclient.CpsMonitor;
import dev.solclient.Gfx;
import dev.solclient.option.Option;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Keystrokes, OneConfig-style: every size, colour and part is editable (box size, gap, corner radius, border
 * thickness, inner border, fade delay, fonts, arrows, CPS on the mouse keys, space bar, mouse movement).
 */
public class KeystrokesMod extends HudMod {

	// ---- what to show
	private final Option.Bool movement;
	private final Option.Bool clicks;
	private final Option.Bool lmbCps;
	private final Option.Bool rmbCps;
	private final Option.Bool spaceBar;
	private final Option.Bool arrows;
	private final Option.Bool mouseMovement;
	// ---- layout
	private final Option.Num boxSize;
	private final Option.Num gap;
	private final Option.Num radius;
	// ---- style
	private final Option.Choice font;
	private final Option.Bool shadow;
	private final Option.Bool background;
	private final Option.Bool border;
	private final Option.Num borderThickness;
	private final Option.Bool innerBorder;
	private final Option.Num fadeDelay;
	// ---- colours
	private final Option.Col backgroundColour;
	private final Option.Col backgroundPressed;
	private final Option.Col borderColour;
	private final Option.Col borderPressed;
	private final Option.Col textColour;
	private final Option.Col textPressed;

	private Key w, a, s, d, lmb, rmb, space;
	private float mouseX, mouseY, lastYaw, lastPitch;
	private boolean hasRot;
	private long lastMouseUpdate;

	public KeystrokesMod() {
		super("Keystrokes", "keystrokes", "Shows when your movement keys, mouse buttons or space bar are pressed.", 0.01F, 0.75F);
		header("Keys");
		movement = bool("Show Movement Keys", true);
		clicks = bool("Show Clicks", true);
		lmbCps = bool("Show LMB CPS", false);
		rmbCps = bool("Show RMB CPS", false);
		spaceBar = bool("Show Space Bar", false);
		arrows = bool("Replace Names With Arrows", false);
		mouseMovement = bool("Show Mouse Movement", false);
		header("Layout");
		boxSize = num("Box Size", 18, 6, 64, 0.5F, "");
		gap = num("Gap", 1, 0, 6, 0.5F, "");
		radius = num("Corner Radius", 2, 0, 10, 0.5F, "");
		header("Style");
		font = choice("Font", 1, "Minecraft", "Sleep");
		shadow = bool("Text Shadow", false);
		background = bool("Background", true);
		border = bool("Border", false);
		borderThickness = num("Border Thickness", 1, 0.5F, 3, 0.25F, "");
		innerBorder = bool("Inner Border", false);
		fadeDelay = num("Key Fade Delay", 75, 0, 400, 5, "ms");
		header("Colours");
		backgroundColour = colour("Background Colour", 0x6F000000);
		backgroundPressed = colour("Background Colour (Pressed)", 0x6FFFFFFF);
		borderColour = colour("Border Colour", 0xFFFFFFFF);
		borderPressed = colour("Border Colour (Pressed)", 0xFFFFFFFF);
		textColour = colour("Text Colour", 0xFFFFFFFF);
		textPressed = colour("Text Colour (Pressed)", 0xFF000000);
	}

	private void ensureKeys() {
		if (w != null) {
			return;
		}
		w = new Key(() -> mc.options.keyUp.isDown(), "W", 0);
		a = new Key(() -> mc.options.keyLeft.isDown(), "A", 1);
		s = new Key(() -> mc.options.keyDown.isDown(), "S", 2);
		d = new Key(() -> mc.options.keyRight.isDown(), "D", 3);
		lmb = new Key(() -> mc.options.keyAttack.isDown(), "LMB", -1);
		rmb = new Key(() -> mc.options.keyUse.isDown(), "RMB", -1);
		space = new Key(() -> mc.options.keyJump.isDown(), "Space", -1);
	}

	private float bs() {
		return boxSize.f();
	}

	private float g() {
		return gap.f();
	}

	private float fullW() {
		return bs() * 3 + g() * 2;
	}

	private float mouseH() {
		return lmbCps.on() || rmbCps.on() ? bs() * 1.2F : bs();
	}

	@Override
	public int width() {
		return Math.max(1, Math.round(fullW()));
	}

	@Override
	public int height() {
		float h = 0;
		if (movement.on()) h += bs() * 2 + g() * 2;
		if (mouseMovement.on()) h += bs() * 2 + g();
		if (clicks.on()) h += mouseH() + g();
		if (spaceBar.on()) h += bs() * 0.55F + g();
		return Math.max(1, Math.round(h - g()));
	}

	@Override
	public void frame() {
		if (mc.player == null) {
			return;
		}
		float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
		if (hasRot) {
			float lim = fullW() / 2 - 4;
			mouseX = Mth.clamp(mouseX + Mth.wrapDegrees(yaw - lastYaw) / 6F, -lim, lim);
			mouseY = Mth.clamp(mouseY + (pitch - lastPitch) / 6F, -bs() + 4, bs() - 4);
		}
		lastYaw = yaw;
		lastPitch = pitch;
		hasRot = true;
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		ensureKeys();
		float b = bs(), gp = g(), fw = fullW();
		float y = 0;
		if (movement.on()) {
			w.render(c, b + gp, y, b, b);
			y += b + gp;
			a.render(c, 0, y, b, b);
			s.render(c, b + gp, y, b, b);
			d.render(c, (b + gp) * 2, y, b, b);
			y += b + gp;
		}
		if (mouseMovement.on()) {
			float h = b * 2;
			box(c, 0, y, fw, h, 0F);
			int tc = textColour.c();
			float cx = fw / 2F, cy = y + h / 2F;
			long now = System.nanoTime();
			mouseX = decay(mouseX, now - lastMouseUpdate);
			mouseY = decay(mouseY, now - lastMouseUpdate);
			lastMouseUpdate = now;
			Gfx.rrectF(c, cx - 0.5F, cy - 1, 1, 2, 0, tc);
			Gfx.circle(c, cx + mouseX, cy + mouseY, 3.5F, tc);
			y += h + gp;
		}
		if (clicks.on()) {
			float hw = (fw - gp) / 2F, h = mouseH();
			lmb.render(c, 0, y, hw, h);
			rmb.render(c, hw + gp, y, hw, h);
			y += h + gp;
		}
		if (spaceBar.on()) {
			space.render(c, 0, y, fw, b * 0.55F);
		}
	}

	private static float decay(float v, long sinceNanos) {
		float step = (float) (sinceNanos / 25_000_000D);
		return v > 0 ? Math.max(0, v - step) : v < 0 ? Math.min(0, v + step) : 0;
	}

	/** Background + border + inner border of one key; p = 0 released .. 1 pressed. */
	private void box(GuiGraphicsExtractor c, float x, float y, float w, float h, float p) {
		float r = Math.min(radius.f(), Math.min(w, h) / 2F);
		if (border.on()) {
			float t = borderThickness.f();
			Gfx.rrectF(c, x, y, w, h, r, Gfx.blend(borderColour.c(), borderPressed.c(), p));
			if (background.on()) {
				Gfx.rrectF(c, x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0, r - t), Gfx.blend(backgroundColour.c(), backgroundPressed.c(), p));
			}
		} else if (background.on()) {
			Gfx.rrectF(c, x, y, w, h, r, Gfx.blend(backgroundColour.c(), backgroundPressed.c(), p));
		}
		if (innerBorder.on()) {
			float t = border.on() ? borderThickness.f() : 0;
			int col = Gfx.withAlpha(Gfx.blend(textColour.c(), textPressed.c(), p), 0x40);
			float ix = x + t + 1, iy = y + t + 1, iw = w - 2 * t - 2, ih = h - 2 * t - 2;
			Gfx.rrectF(c, ix, iy, iw, 0.5F, 0, col);
			Gfx.rrectF(c, ix, iy + ih - 0.5F, iw, 0.5F, 0, col);
			Gfx.rrectF(c, ix, iy, 0.5F, ih, 0, col);
			Gfx.rrectF(c, ix + iw - 0.5F, iy, 0.5F, ih, 0, col);
		}
	}

	private int textW(String t) {
		return font.idx() == 1 ? Gfx.tw(t) : mc.font.width(t);
	}

	private void text(GuiGraphicsExtractor c, String t, float cx, float cy, float scale, int col) {
		c.pose().pushMatrix();
		c.pose().translate(cx, cy);
		c.pose().scale(scale, scale);
		int x = -textW(t) / 2, y = -4;
		Component tx = font.idx() == 1 ? Gfx.ui(t) : Component.literal(t);
		c.text(mc.font, tx, x, y, col, shadow.on());
		c.pose().popMatrix();
	}

	/** Arrow for W A S D (dir 0 up, 1 left, 2 down, 3 right). */
	private void arrow(GuiGraphicsExtractor c, float cx, float cy, float size, int dir, int col) {
		int n = Math.max(3, Math.round(size));
		for (int i = 0; i < n; i++) {
			float half = (i + 1) * 0.5F;
			float o = i - n / 2F;
			switch (dir) {
				case 0 -> Gfx.rrectF(c, cx - half, cy + o, half * 2, 1, 0, col);
				case 2 -> Gfx.rrectF(c, cx - half, cy - o - 1, half * 2, 1, 0, col);
				case 1 -> Gfx.rrectF(c, cx + o, cy - half, 1, half * 2, 0, col);
				default -> Gfx.rrectF(c, cx - o - 1, cy - half, 1, half * 2, 0, col);
			}
		}
	}

	private class Key {
		private final BooleanSupplier down;
		private final String name;
		private final int dir;
		private boolean was;
		private long changed;

		Key(BooleanSupplier down, String name, int dir) {
			this.down = down;
			this.name = name;
			this.dir = dir;
		}

		void render(GuiGraphicsExtractor c, float x, float y, float w, float h) {
			boolean on = down.getAsBoolean();
			long now = System.currentTimeMillis();
			if (on != was) {
				changed = now;
				was = on;
			}
			float fade = fadeDelay.f();
			float t = fade <= 0 ? 1F : Mth.clamp((now - changed) / fade, 0F, 1F);
			float p = on ? t : 1F - t;
			box(c, x, y, w, h, p);
			int col = Gfx.blend(textColour.c(), textPressed.c(), p);
			float scale = Math.max(0.5F, bs() / 18F);
			float cx = x + w / 2F, cy = y + h / 2F;

			if (name.equals("Space")) {
				float lw = w * 0.5F;
				Gfx.rrectF(c, cx - lw / 2, cy - 0.5F, lw, 1.2F, 0.5F, col);
				return;
			}
			CpsMonitor m = name.equals("LMB") && lmbCps.on() ? CpsMonitor.LMB : name.equals("RMB") && rmbCps.on() ? CpsMonitor.RMB : null;
			if (dir >= 0 && arrows.on()) {
				arrow(c, cx, cy, 5 * scale, dir, col);
			} else if (m != null) {
				text(c, name, cx, cy - h * 0.14F, scale, col);
				text(c, m.getCps() + " CPS", cx, cy + h * 0.24F, scale * 0.55F, col);
			} else {
				text(c, name, cx, cy + 0.5F, scale, col);
			}
		}
	}
}
