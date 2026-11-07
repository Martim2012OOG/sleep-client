package dev.solclient.gui;

import dev.solclient.Gfx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Small immediate-mode base for the Plegumo title / pause screens: clickable regions rebuilt every frame,
 * hover animations and the shared button / icon-button look (same colours as the Right Shift menu).
 * Everything is in normal GUI units.
 */
public abstract class UiScreen extends Screen {

	// opaque tiles: rounded borders on opaque fills take the fast drawing path (no lag on big / Retina screens)
	protected static int TILE = 0xFF14161B, TILE_HOVER = 0xFF1E2128;
	protected static final int BORDER = 0x40FFFFFF;
	/** GUI units -> screen: everything in these screens is laid out for a fixed height, so it fits any window. */
	protected float ui = 1F;
	private static final float DESIGN_H = 400F;

	private record Region(int x, int y, int w, int h, IntConsumer click) {
	}

	private final List<Region> regions = new ArrayList<>();
	private record DragRegion(int x, int y, int w, int h, java.util.function.DoubleConsumer onX) {
	}
	private final List<DragRegion> drags = new ArrayList<>();
	private DragRegion activeDrag;
	private final Map<String, Float> hover = new HashMap<>();
	protected final MinecraftClient mc = MinecraftClient.getInstance();
	protected final long openedAt = System.currentTimeMillis();
	protected int mouseX, mouseY;
	/** Tooltip for this frame (drawn last, next to the mouse). */
	protected String tooltip;

	protected UiScreen(String title) {
		super(Text.literal(title));
	}

	// ---------------------------------------------------------------- frame
	@Override
	public void render(DrawContext c, int mx, int my, float delta) {
		super.render(c, mx, my, delta); // panorama / blurred game behind
		long nowNs = System.nanoTime();
		frameDt = lastNs == 0 ? 1F / 60F : Math.max(0.001F, Math.min(0.1F, (nowNs - lastNs) / 1e9F));
		lastNs = nowNs;
		int realW = width, realH = height;
		float pct = dev.solclient.SolClient.settings == null ? 100F : dev.solclient.SolClient.settings.screenSize.f();
		ui = Math.max(0.3F, Math.min(3F, realH / DESIGN_H * pct / 100F));
		regions.clear();
		drags.clear();
		mouseX = (int) (mx / ui);
		mouseY = (int) (my / ui);
		tooltip = null;
		float prevScale = Gfx.scaleOverride;
		width = Math.round(realW / ui);
		height = Math.round(realH / ui);
		Gfx.scaleOverride = (float) (mc.getWindow().getScaleFactor() * ui);
		c.getMatrices().pushMatrix();
		c.getMatrices().scale(ui, ui);
		try {
			drawGuarded(c, delta);
			if (tooltip != null) {
				int w = Gfx.tw(tooltip) + 12;
				int x = Math.min(width - w - 2, mouseX + 10), y = Math.max(2, mouseY - 18);
				Gfx.rrectBorder(c, x, y, w, 16, 4, 0xFF101114, BORDER);
				Gfx.text(c, tooltip, x + 6, y + 4, Theme.TEXT);
			}
		} finally {
			c.getMatrices().popMatrix();
			width = realW;
			height = realH;
			Gfx.scaleOverride = prevScale;
		}
	}

	/** Mouse position (screen) -> layout units. */
	protected double sx(double v) {
		return v / ui;
	}

	private static boolean animations() {
		return dev.solclient.SolClient.settings != null && dev.solclient.SolClient.settings.buttonAnimations.on();
	}

	/** Button background in the style chosen in the settings (Classic / Rounded / Outline / Minimal). */
	protected void buttonBg(DrawContext c, int x, int y, int w, int h, float a) {
		int style = dev.solclient.SolClient.settings == null ? 0 : dev.solclient.SolClient.settings.buttonStyle.idx() * 0;
		int accent = Theme.accent();
		switch (style) {
			case 1 -> Gfx.rrectBorder(c, x, y, w, h, 8, Gfx.blend(0xFF16181D, 0xFF20232A, a), Gfx.blend(BORDER, accent, a));
			case 2 -> {
				Gfx.rrect(c, x, y, w, h, 4, a > 0.5F ? accent : 0x90FFFFFF);
				Gfx.rrect(c, x + 1, y + 1, w - 2, h - 2, 3, Gfx.blend(0xFF0F1116, 0xFF1A1D25, a));
			}
			case 3 -> {
				if (a > 0.01F) {
					Gfx.rrect(c, x, y, w, h, 4, Gfx.withAlpha(0xFFFFFF, Math.round(0x2E * a)));
				}
			}
			default -> {
				// Classic: Laby-like raised slate button; hover just lightens it
				Gfx.button(c, x, y, w, h, a > 0.5F, false);
			}
		}
	}

	protected abstract void draw(DrawContext c, float delta);

	/**
	 * While Minecraft is still loading (loading screen on top), resizing / maximising the window re-creates this
	 * screen before our textures and fonts exist. A problem then must not crash the game: skip that frame.
	 */
	private void drawGuarded(DrawContext c, float delta) {
		if (mc.getOverlay() == null) {
			draw(c, delta);
			return;
		}
		try {
			draw(c, delta);
		} catch (Throwable t) {
			if (!loadWarned) {
				loadWarned = true;
				System.err.println("[Sleep] menu while loading (skipped a frame): " + t);
			}
		}
	}

	private static boolean loadWarned;

	/** 0 -> 1 ease-out since the screen opened (for the intro slide / fade). */
	protected float intro(long delayMs) {
		float t = Math.max(0F, Math.min(1F, (System.currentTimeMillis() - openedAt - delayMs) / 350F));
		return 1F - (1F - t) * (1F - t) * (1F - t);
	}

	// ---------------------------------------------------------------- input helpers
	protected void region(int x, int y, int w, int h, Runnable click) {
		regions.add(new Region(x, y, w, h, b -> {
			if (b == 0) {
				click.run();
			}
		}));
	}

	/** Area that follows the mouse while dragged (sliders); gets the mouse x. */
	protected void regionDrag(int x, int y, int w, int h, java.util.function.DoubleConsumer onX) {
		drags.add(new DragRegion(x, y, w, h, onX));
	}

	/** True while the mouse is holding the drag area that starts at (x, y) (sliders). */
	protected boolean isDragging(int x, int y) {
		return activeDrag != null && activeDrag.x == x && activeDrag.y == y;
	}

	@Override
	public boolean mouseDragged(Click click, double dx, double dy) {
		if (activeDrag != null) {
			activeDrag.onX.accept(sx(click.x()));
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(Click click) {
		activeDrag = null;
		return false;
	}

	/** Horizontal slider 0..1 (value) with a label above it; calls set while dragging. Smooth (see SmoothSlider). */
	protected void slider(DrawContext c, String id, int x, int y, int w, String label, float value, java.util.function.Consumer<Float> set) {
		int h = 20;
		Gfx.text(c, label, x, y, Theme.TEXT);
		boolean hov = hovered(x - 6, y, w + 12, h);
		SmoothSlider.draw(c, id, x, y + 14, w, value, isDragging(x - 6, y), hov, Math.round(value * 100) + "%");
		regionDrag(x - 6, y, w + 12, h, mx -> {
			float v = (float) Math.max(0, Math.min(1, (mx - x) / w));
			SmoothSlider.dragTo(id, v);
			set.accept(v);
		});
	}

	protected void regionButtons(int x, int y, int w, int h, IntConsumer click) {
		regions.add(new Region(x, y, w, h, click));
	}

	protected boolean hovered(int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private float frameDt = 1F / 60F;
	private long lastNs;

	protected float anim(String key, boolean target) {
		if (!animations()) {
			return target ? 1F : 0F; // no hover animations (setting)
		}
		float v = hover.getOrDefault(key, 0F);
		v += ((target ? 1F : 0F) - v) * (1F - (float) Math.exp(-frameDt * 28F)); // by the clock, not per frame
		if (Math.abs((target ? 1F : 0F) - v) < 0.01F) {
			v = target ? 1F : 0F;
		}
		hover.put(key, v);
		return v;
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (click.button() == 0 && Notify.click(click.x(), click.y())) {
			return true; // click on a notification card: it goes away
		}
		double mx = sx(click.x()), my = sx(click.y());
		for (int i = drags.size() - 1; i >= 0; i--) {
			DragRegion d = drags.get(i);
			if (click.button() == 0 && mx >= d.x && mx < d.x + d.w && my >= d.y && my < d.y + d.h) {
				activeDrag = d;
				d.onX.accept(mx);
				return true;
			}
		}
		for (int i = regions.size() - 1; i >= 0; i--) {
			Region r = regions.get(i);
			if (mx >= r.x && mx < r.x + r.w && my >= r.y && my < r.y + r.h) {
				r.click.accept(click.button());
				return true;
			}
		}
		return clickedNothing(mx, my);
	}

	/** Click outside every region (e.g. to close a dropdown). */
	protected boolean clickedNothing(double mx, double my) {
		return false;
	}

	// ---------------------------------------------------------------- widgets
	/** Wide text button (Singleplayer, Back to Game...). */
	protected void button(DrawContext c, String id, int x, int y, int w, int h, String label, Runnable run) {
		boolean hov = hovered(x, y, w, h);
		float a = anim(id, hov);
		buttonBg(c, x, y, w, h, a);
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 9) / 2 - (h > 14 ? 1 : 0), 0xFFFFFFFF);
		region(x, y, w, h, run);
	}

	/** Square icon button with a tooltip. */
	protected void iconButton(DrawContext c, String id, int x, int y, int s, String icon, String tip, Runnable run) {
		boolean hov = hovered(x, y, s, s);
		float a = anim(id, hov);
		buttonBg(c, x, y, s, s, a);
		int is = Math.round(s * 0.6F);
		Gfx.tex(c, Theme.icon2(icon), x + (s - is) / 2, y + (s - is) / 2, is, is, 64, 64, 0xFFFFFFFF);
		if (hov) {
			tooltip = tip;
		}
		region(x, y, s, s, run);
	}

	/** Translucent panel with a small title. */
	protected void panel(DrawContext c, int x, int y, int w, int h, String title) {
		Gfx.rrectBorder(c, x, y, w, h, 6, Theme.PANEL_UI, BORDER);
		if (title != null) {
			Gfx.text(c, title, x + 10, y + 9, Theme.DIM);
		}
	}

	/** Play triangle (for the server list). */
	protected static void playIcon(DrawContext c, int x, int y, int size, int col) {
		for (int i = 0; i < size; i++) {
			int half = Math.round((size - i) / 2F);
			int cy = y + size / 2;
			c.fill(x + i, cy - half, x + i + 1, cy + half, col);
		}
	}

	protected static void chevron(DrawContext c, int cx, int cy, boolean down, int col) {
		for (int i = 0; i < 4; i++) {
			int yy = down ? cy - 2 + i : cy + 1 - i;
			c.fill(cx - 4 + i, yy, cx - 3 + i, yy + 1, col);
			c.fill(cx + 3 - i, yy, cx + 4 - i, yy + 1, col);
		}
	}

	protected static String fit(String s, int maxW) {
		if (s == null) {
			return "";
		}
		if (Gfx.tw(s) <= maxW) {
			return s;
		}
		while (s.length() > 1 && Gfx.tw(s + "..") > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}

	/** The client logo (static). */
	protected void logo(DrawContext c, int cx, int cy, int size) {
		Gfx.tex(c, Gfx.id("icon.png"), cx - size / 2, cy - size / 2, size, size, 256, 256, 0xFFFFFFFF);
	}

	/** Logo + "SLEEP" (bottom-right corner in the pause menu, top in the title). */
	protected void brand(DrawContext c, int x, int y) {
		Gfx.tex(c, Theme.icon2("sol_client"), x, y, 16, 16, 64, 64, 0xFFFFFFFF);
		Gfx.text(c, "SLEEP", x + 21, y + 4, Theme.TEXT);
	}
}
