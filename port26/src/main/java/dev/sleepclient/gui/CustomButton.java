package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Your own button design: a small picture you draw in the button editor (24 x 10 pixels). Every button in the menus is drawn from it,
 * stretched to the right size with the corners kept (a 9-slice), lightened on hover and darkened when pressed.
 * The picture is saved in config/sleep/button.txt.
 */
public final class CustomButton {

	public static final int W = 24, H = 10, BL = 4, BT = 3;
	private static final int[] PX = new int[W * H]; // ARGB, 0 = transparent
	private static boolean loaded, dirty = true;
	private static DynamicTexture tex;
	private static Identifier id;

	private CustomButton() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("sleep").resolve("button.txt");
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		preset(0);
		try {
			Path f = file();
			if (Files.isRegularFile(f)) {
				List<String> lines = Files.readAllLines(f);
				for (int y = 0; y < H && y < lines.size(); y++) {
					String[] parts = lines.get(y).trim().split("\\s+");
					for (int x = 0; x < W && x < parts.length; x++) {
						PX[y * W + x] = (int) Long.parseLong(parts[x], 16);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] button design: " + e);
		}
		dirty = true;
	}

	/** True when the menus should use your design. */
	public static boolean active() {
		var s = dev.sleepclient.SleepClient.settings;
		return s != null && s.customButtons.on();
	}

	public static int get(int x, int y) {
		ensureLoaded();
		return PX[y * W + x];
	}

	public static void set(int x, int y, int argb) {
		ensureLoaded();
		if (x >= 0 && y >= 0 && x < W && y < H && PX[y * W + x] != argb) {
			PX[y * W + x] = argb;
			dirty = true;
		}
	}

	public static void clear() {
		ensureLoaded();
		java.util.Arrays.fill(PX, 0);
		dirty = true;
	}

	/** Throws away unsaved drawing: back to what is in the file. */
	public static void reload() {
		loaded = false;
		ensureLoaded();
	}

	public static void save() {
		ensureLoaded();
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			List<String> lines = new ArrayList<>();
			for (int y = 0; y < H; y++) {
				StringBuilder sb = new StringBuilder();
				for (int x = 0; x < W; x++) {
					sb.append(String.format("%08X", PX[y * W + x])).append(' ');
				}
				lines.add(sb.toString().trim());
			}
			Files.write(f, lines);
		} catch (IOException e) {
			System.err.println("[Sleep] button design not saved: " + e);
		}
	}

	private static int mix(int a, int b, float t) {
		return Gfx.blend(a, b, t);
	}

	/** Starting points to draw over: 0 flat, 1 rounded, 2 outline, 3 gradient, 4 pixel (Minecraft-like). */
	public static void preset(int n) {
		java.util.Arrays.fill(PX, 0);
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				boolean edge = x == 0 || y == 0 || x == W - 1 || y == H - 1;
				boolean corner = (x == 0 || x == W - 1) && (y == 0 || y == H - 1);
				int v;
				switch (n) {
					case 1 -> {
						boolean c2 = (x < 2 || x >= W - 2) && (y < 2 || y >= H - 2);
						v = corner || c2 && edge ? 0 : edge || (x == 1 || x == W - 2) && (y == 1 || y == H - 2) ? 0xFF0A0B10 : 0xFF3A4155;
						if (c2 && (x == 1 || x == W - 2) && (y == 1 || y == H - 2)) {
							v = 0xFF0A0B10;
						}
					}
					case 2 -> v = edge ? 0xFF7C8DFF : 0x00000000;
					case 3 -> v = edge ? 0xFF0A0B10 : mix(0xFF4B5575, 0xFF262A36, y / (float) (H - 1));
					case 4 -> v = edge ? 0xFF000000 : y == 1 || x == 1 ? 0xFF8B8B8B : y == H - 2 || x == W - 2 ? 0xFF373737 : 0xFF6F6F6F;
					default -> v = corner ? 0 : edge ? 0xFF0A0B10 : y < 3 ? 0xFF4A5268 : y > H - 4 ? 0xFF262A36 : 0xFF323849;
				}
				PX[y * W + x] = v;
			}
		}
		dirty = true;
	}

	private static void upload() {
		if (tex == null) {
			tex = new DynamicTexture("sleep_custom_button", W, H, false);
			id = Gfx.id("custom_button");
			Minecraft.getInstance().getTextureManager().register(id, tex);
			dirty = true;
		}
		if (!dirty) {
			return;
		}
		NativeImage img = tex.getPixels();
		if (img == null) {
			return;
		}
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				img.setPixel(x, y, PX[y * W + x]);
			}
		}
		tex.upload();
		dirty = false;
	}

	/** Draws a button from the design. Works even when the design is not switched on (the editor preview). */
	public static void draw(GuiGraphicsExtractor c, int x, int y, int w, int h, boolean hover, boolean pressed) {
		if (w < 2 || h < 2) {
			return;
		}
		ensureLoaded();
		upload();
		int tint = pressed ? 0xFFB0B0B8 : 0xFFFFFFFF;
		int dy = pressed ? 1 : 0;
		if (w < 2 * BL || h < 2 * BT) {
			c.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y + dy, 0f, 0f, w, h, W, H, W, H, tint);
		} else {
			int[] sx = {0, BL, W - BL, W}, sy = {0, BT, H - BT, H};
			int[] dx = {x, x + BL, x + w - BL, x + w}, ddy = {y + dy, y + dy + BT, y + dy + h - BT, y + dy + h};
			for (int j = 0; j < 3; j++) {
				for (int i = 0; i < 3; i++) {
					int sw = sx[i + 1] - sx[i], sh = sy[j + 1] - sy[j], dw = dx[i + 1] - dx[i], dh = ddy[j + 1] - ddy[j];
					if (dw > 0 && dh > 0) {
						c.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, dx[i], ddy[j], (float) sx[i], (float) sy[j], dw, dh, sw, sh, W, H, tint);
					}
				}
			}
		}
		if (hover && !pressed) {
			Gfx.rrectFree(c, x + 1, y + 1, w - 2, h - 2, Math.max(0, Gfx.btnRadius() - 1), 0x24FFFFFF);
		}
	}
}
