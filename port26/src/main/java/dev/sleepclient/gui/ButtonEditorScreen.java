package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Draw your own buttons: a pixel editor for the button picture (24 x 10). Left mouse paints, right mouse erases, the bucket fills.
 * The three previews on the right show the button at different sizes (hover and press them to see how they react).
 * Save & use switches your design on for every menu; "Back to normal" switches it off.
 */
public final class ButtonEditorScreen extends Screen {

	private static final int[] PALETTE = {0xFF0A0B10, 0xFF262A36, 0xFF323849, 0xFF4A5268, 0xFF7C8294, 0xFFB9BDCB, 0xFFFFFFFF, 0xFFE5453A, 0xFFF2A33A, 0xFFE6C84A,
			0xFF7CFF9B, 0xFF2E8B57, 0xFF3B6FD4, 0xFF7C8DFF, 0xFFB06BFF, 0xFFFF6BB0, 0xFF000000, 0xFF373737, 0xFF6F6F6F, 0xFF8B8B8B, 0xFF5B3A1E, 0xFF9C6B3A,
			0xFF1B5A2A, 0xFF1F3B5E};

	private final Minecraft mc = Minecraft.getInstance();
	private final Screen parent;
	private int colour = 0xFF3A4155, tool; // 0 pencil, 1 bucket
	private int cell = 12, gx, gy;
	private boolean painting, erasing, saved;
	private int sliderDrag = -1;
	private boolean leftHeld;
	private int slX, slW, palY;

	private record Btn(int x, int y, int w, int h, Runnable run) {
	}

	private final List<Btn> buttons = new ArrayList<>();

	public ButtonEditorScreen(Screen parent) {
		super(Component.literal("Draw your buttons"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		CustomButton.reload();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		if (!saved) {
			CustomButton.reload(); // unsaved drawing is dropped
		}
	}

	private void leave() {
		dev.sleepclient.compat.Mc.setScreen(mc, parent);
	}

	private void btn(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h, String label, boolean on, Runnable run) {
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		Gfx.rrect(c, x, y, w, h, 4, on ? 0xFF3B6FD4 : hov ? 0xFF2F3447 : 0xFF222634);
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 8) / 2, 0xFFFFFFFF);
		buttons.add(new Btn(x, y, w, h, run));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
		buttons.clear();
		c.fill(0, 0, width, height, 0xE00A0C12);
		int pw = Math.min(width - 16, 560), ph = Math.min(height - 16, 300);
		int px = (width - pw) / 2, py = (height - ph) / 2;
		Gfx.rrect(c, px, py, pw, ph, 8, 0xF0141824);
		Gfx.text(c, "Draw your own buttons", px + 12, py + 10, 0xFFFFFFFF);
		Gfx.text(c, "left = paint   right = erase", px + pw - 12 - Gfx.tw("left = paint   right = erase"), py + 10, 0x80FFFFFF);

		// canvas
		cell = Math.max(5, Math.min(14, (pw - 200) / CustomButton.W));
		gx = px + 12;
		gy = py + 30;
		int cw = CustomButton.W * cell, chh = CustomButton.H * cell;
		Gfx.rrect(c, gx - 3, gy - 3, cw + 6, chh + 6, 4, 0xFF0C0E16);
		for (int y = 0; y < CustomButton.H; y++) {
			for (int x = 0; x < CustomButton.W; x++) {
				int checker = ((x + y) & 1) == 0 ? 0xFF1A1E2B : 0xFF222737;
				c.fill(gx + x * cell, gy + y * cell, gx + (x + 1) * cell, gy + (y + 1) * cell, checker);
				int v = CustomButton.get(x, y);
				if ((v >>> 24) != 0) {
					c.fill(gx + x * cell, gy + y * cell, gx + (x + 1) * cell, gy + (y + 1) * cell, v);
				}
			}
		}
		// the 9-slice borders (these corners stay as drawn when a button is stretched)
		int b1 = gx + CustomButton.BL * cell, b2 = gx + (CustomButton.W - CustomButton.BL) * cell;
		int b3 = gy + CustomButton.BT * cell, b4 = gy + (CustomButton.H - CustomButton.BT) * cell;
		c.fill(b1, gy, b1 + 1, gy + chh, 0x40FFFFFF);
		c.fill(b2, gy, b2 + 1, gy + chh, 0x40FFFFFF);
		c.fill(gx, b3, gx + cw, b3 + 1, 0x40FFFFFF);
		c.fill(gx, b4, gx + cw, b4 + 1, 0x40FFFFFF);
		Gfx.text(c, "the middle stretches, the corners stay", gx, gy + chh + 8, 0x70FFFFFF);

		// previews
		int vx = gx + cw + 22, vy = gy;
		Gfx.text(c, "Preview", vx, vy - 12, 0xB0FFFFFF);
		int[][] sizes = {{80, 20}, {140, 20}, {110, 30}};
		int yy = vy;
		for (int[] s : sizes) {
			int bw = Math.min(s[0], px + pw - 12 - vx), bh = s[1];
			boolean hov = mx >= vx && mx < vx + bw && my >= yy && my < yy + bh;
			boolean down = hov && leftHeld;
			CustomButton.draw(c, vx, yy, bw, bh, hov, down);
			Gfx.textC(c, "Button", vx + bw / 2, yy + (bh - 8) / 2, 0xFFFFFFFF);
			yy += bh + 8;
		}

		// palette
		palY = gy + chh + 26;
		int sw = 14, gap = 3, perRow = 12;
		for (int i = 0; i < PALETTE.length; i++) {
			int x = gx + (i % perRow) * (sw + gap), y = palY + (i / perRow) * (sw + gap);
			c.fill(x, y, x + sw, y + sw, PALETTE[i]);
			if (PALETTE[i] == colour) {
				Gfx.rrectBorder(c, x - 2, y - 2, sw + 4, sw + 4, 3, 0x00000000, 0xFFFFFFFF);
			}
			final int col = PALETTE[i];
			buttons.add(new Btn(x, y, sw, sw, () -> colour = col));
		}
		// RGB sliders for any colour
		slX = gx + perRow * (sw + gap) + 14;
		slW = Math.max(60, px + pw - 12 - slX - 26);
		String[] names = {"R", "G", "B"};
		for (int k = 0; k < 3; k++) {
			int y = palY + k * 15;
			int shift = 16 - 8 * k;
			int v = (colour >> shift) & 255;
			Gfx.text(c, names[k], slX - 10, y + 1, 0xB0FFFFFF);
			c.fill(slX, y + 4, slX + slW, y + 7, 0xFF2A2E3A);
			int kx = slX + Math.round(slW * v / 255F);
			c.fill(kx - 2, y, kx + 2, y + 11, 0xFFFFFFFF);
			if (sliderDrag == k) {
				int nv = Math.max(0, Math.min(255, Math.round((mx - slX) * 255F / slW)));
				colour = (colour & ~(255 << shift)) | (nv << shift) | 0xFF000000;
			}
		}
		c.fill(slX + slW + 6, palY, slX + slW + 22, palY + 14, colour);

		// tools
		int ty = palY + 2 * (sw + gap) + 8, bx = gx, bh = 18;
		btn(c, mx, my, bx, ty, 60, bh, "Pencil", tool == 0, () -> tool = 0);
		bx += 64;
		btn(c, mx, my, bx, ty, 60, bh, "Bucket", tool == 1, () -> tool = 1);
		bx += 64;
		btn(c, mx, my, bx, ty, 52, bh, "Clear", false, CustomButton::clear);
		bx += 62;
		String[] pre = {"Flat", "Round", "Outline", "Gradient", "Pixel"};
		int[] order = {0, 1, 2, 3, 4};
		Gfx.text(c, "Start from:", bx, ty + 5, 0x90FFFFFF);
		bx += Gfx.tw("Start from:") + 6;
		for (int i = 0; i < pre.length; i++) {
			final int n = order[i];
			int w = Gfx.tw(pre[i]) + 14;
			if (bx + w < px + pw - 8) {
				btn(c, mx, my, bx, ty, w, bh, pre[i], false, () -> CustomButton.preset(n));
			}
			bx += w + 4;
		}
		// save / reset / close
		int sy = py + ph - 28;
		btn(c, mx, my, px + 12, sy, 110, 20, "Save & use", false, () -> {
			CustomButton.save();
			saved = true;
			var s = dev.sleepclient.SleepClient.settings;
			if (s != null) {
				s.customButtons.set(true);
			}
			leave();
		});
		btn(c, mx, my, px + 128, sy, 110, 20, "Back to normal", false, () -> {
			var s = dev.sleepclient.SleepClient.settings;
			if (s != null) {
				s.customButtons.set(false);
			}
			saved = true;
			leave();
		});
		btn(c, mx, my, px + pw - 12 - 70, sy, 70, 20, "Cancel", false, this::leave);
	}

	private void paint(double mx, double my, boolean erase) {
		int x = (int) Math.floor((mx - gx) / cell), y = (int) Math.floor((my - gy) / cell);
		if (x < 0 || y < 0 || x >= CustomButton.W || y >= CustomButton.H) {
			return;
		}
		if (tool == 1 && !erase) {
			fill(x, y, CustomButton.get(x, y), colour);
		} else {
			CustomButton.set(x, y, erase ? 0 : colour);
		}
	}

	private void fill(int x, int y, int from, int to) {
		if (from == to) {
			return;
		}
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		q.add(new int[] {x, y});
		while (!q.isEmpty()) {
			int[] p = q.poll();
			if (p[0] < 0 || p[1] < 0 || p[0] >= CustomButton.W || p[1] >= CustomButton.H || CustomButton.get(p[0], p[1]) != from) {
				continue;
			}
			CustomButton.set(p[0], p[1], to);
			q.add(new int[] {p[0] + 1, p[1]});
			q.add(new int[] {p[0] - 1, p[1]});
			q.add(new int[] {p[0], p[1] + 1});
			q.add(new int[] {p[0], p[1] - 1});
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x(), my = click.y();
		if (click.button() == 0) {
			for (int i = buttons.size() - 1; i >= 0; i--) {
				Btn b = buttons.get(i);
				if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
					b.run().run();
					return true;
				}
			}
			// a slider?
			for (int k = 0; k < 3; k++) {
				int y = palY + k * 15;
				if (mx >= slX - 4 && my >= y - 2 && my < y + 13 && mx < slX + slW + 4) {
					sliderDrag = k;
					return true;
				}
			}
		}
		leftHeld = click.button() == 0;
		painting = click.button() == 0;
		erasing = click.button() == 1;
		paint(mx, my, erasing);
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (sliderDrag < 0 && (painting || erasing) && tool == 0) {
			paint(click.x(), click.y(), erasing);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		painting = false;
		erasing = false;
		leftHeld = false;
		sliderDrag = -1;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (input.key() == 256) {
			leave();
			return true;
		}
		return super.keyPressed(input);
	}
}
