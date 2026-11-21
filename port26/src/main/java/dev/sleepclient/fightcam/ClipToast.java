package dev.sleepclient.fightcam;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sleepclient.Gfx;
import dev.sleepclient.gui.Theme;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * The "Clip saved - Clipped the last 15 seconds" card (with a little picture of the clip) that slides in at the top.
 * It is drawn after the picture for the video was taken, so it never ends up in your clips.
 */
public final class ClipToast {

	private static final int TW = 96, TH = 54;
	private static final long SHOW_MS = 4200;

	private record Toast(String title, String line, int[] thumb, boolean error) {
	}

	private static final ArrayDeque<Toast> QUEUE = new ArrayDeque<>();
	private static Toast current;
	private static long shownAt;
	private static DynamicTexture tex;
	private static Identifier texId;

	private ClipToast() {
	}

	/** Any thread. thumb = TW x TH ARGB pixels or null. */
	public static void show(String title, String line, int[] thumb, boolean error) {
		// v99: shown as the bottom-left card of notificacao.html (gui.Notify), with the clip's picture
		Minecraft.getInstance().execute(() -> {
			String low = title == null ? "" : title.toLowerCase(java.util.Locale.ROOT);
			dev.sleepclient.gui.Notify.Kind k = error ? dev.sleepclient.gui.Notify.Kind.ERROR
					: low.startsWith("recording") && !low.contains("saved") || low.contains("camera point") ? dev.sleepclient.gui.Notify.Kind.REC
					: low.contains("ready") ? dev.sleepclient.gui.Notify.Kind.DONE : dev.sleepclient.gui.Notify.Kind.CLIP;
			if (thumb != null && thumb.length == TW * TH) {
				try {
					upload(thumb);
					dev.sleepclient.gui.Notify.picture(k, title, line, texId, TW, TH);
					return;
				} catch (RuntimeException ignored) {
				}
			}
			dev.sleepclient.gui.Notify.show(k, title, line);
		});
	}

	public static int thumbW() {
		return TW;
	}

	public static int thumbH() {
		return TH;
	}

	private static void upload(int[] px) {
		Minecraft mc = Minecraft.getInstance();
		if (tex == null) {
			tex = new DynamicTexture("kubo_clip_thumb", TW, TH, false);
			texId = Gfx.id("clip_thumb");
			mc.getTextureManager().register(texId, tex);
		}
		NativeImage img = tex.getPixels();
		if (img == null) {
			return;
		}
		for (int y = 0; y < TH; y++) {
			for (int x = 0; x < TW; x++) {
				img.setPixel(x, y, px[y * TW + x]);
			}
		}
		tex.upload();
	}

	private static float ease(float k) {
		k = Math.max(0, Math.min(1, k));
		return 1 - (1 - k) * (1 - k) * (1 - k);
	}

	/** Height taken in the notifications corner right now (other pop-ups stack under it). */
	public static int occupied() {
		if (true) {
			return 0; // one card at a time now (gui.Notify)
		}
		if (current == null) {
			return 0;
		}
		long age = System.currentTimeMillis() - shownAt;
		return age > SHOW_MS ? 0 : 54;
	}

	public static void draw(GuiGraphicsExtractor c) {
		if (true) {
			return; // drawn by gui.Notify now
		}
		long now = System.currentTimeMillis();
		if (current == null || now - shownAt > SHOW_MS) {
			current = QUEUE.poll();
			if (current == null) {
				return;
			}
			shownAt = now;
			if (current.thumb() != null && current.thumb().length == TW * TH) {
				try {
					upload(current.thumb());
				} catch (RuntimeException e) {
					current = new Toast(current.title(), current.line(), null, current.error());
				}
			}
		}
		Toast t = current;
		long age = now - shownAt;
		float in = ease(age / 300F), out = ease((SHOW_MS - age) / 300F);
		float k = Math.min(in, out);
		Minecraft mc = Minecraft.getInstance();
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		boolean thumb = t.thumb() != null && texId != null;
		String title = dev.sleepclient.Lang.t(t.title()), line = dev.sleepclient.Lang.t(t.line());
		int w = Math.min(sw - 16, Math.max(190, Math.max(Gfx.tw(title), Gfx.tw(line)) + 52 + (thumb ? 72 : 10)));
		int h = 48;
		// the same corner as every other notification (Settings > Notifications Position), sliding in from the side
		int pos = dev.sleepclient.social.SocialToast.corner();
		boolean left = pos == 1 || pos == 3, bottom = pos >= 2;
		int slide = Math.round((1 - k) * (w + 12));
		int x = left ? 6 - slide : sw - w - 6 + slide;
		int y = bottom ? sh - h - 30 : 6;
		int a = Math.round(255 * Math.min(1, k * 1.4F));
		if (a < 8) {
			return;
		}
		Gfx.rrectFree(c, x - 1, y - 1, w + 2, h + 2, 9, Gfx.withAlpha(0x05060A, Math.round(a * 0.9F)));
		Gfx.rrectFree(c, x, y, w, h, 8, Gfx.withAlpha(0x1B1E27, Math.round(a * 0.97F)));
		// icon: the Sleep logo on the accent colour (red ! when something failed)
		int ac = t.error() ? 0xFFE5484D : Theme.accent();
		Gfx.rrectFree(c, x + 9, y + 10, 28, 28, 6, Gfx.withAlpha(ac, a));
		Gfx.rrectFree(c, x + 9, y + 10, 28, 3, 1.5F, Gfx.withAlpha(0xFFFFFF, a / 5));
		if (t.error()) {
			Gfx.textC(c, "!", x + 23, y + 20, Gfx.withAlpha(0xFFFFFF, a));
		} else {
			Gfx.tex(c, Gfx.id("icon.png"), x + 11, y + 12, 24, 24, 256, 256, Gfx.withAlpha(0xFFFFFF, a));
		}
		if (a > 10) {
			c.text(mc.font, Gfx.ui(t.title()), x + 45, y + 12, Gfx.withAlpha(0xFFFFFF, a), false);
			c.text(mc.font, Gfx.ui(t.line()), x + 45, y + 27, Gfx.withAlpha(0xA9AFBF, a), false);
		}
		if (thumb) {
			int tw = 64, th = 36, tx = x + w - tw - 7, ty = y + (h - th) / 2;
			Gfx.rrectFree(c, tx - 2, ty - 2, tw + 4, th + 4, 4, Gfx.withAlpha(0x3A3F4E, a));
			Gfx.tex(c, texId, tx, ty, tw, th, TW, TH, Gfx.withAlpha(0xFFFFFF, a));
		}
		// how long it stays
		int pw = Math.round((w - 18) * Math.max(0, 1F - age / (float) SHOW_MS));
		c.fill(x + 9, y + h - 3, x + 9 + pw, y + h - 2, Gfx.withAlpha(ac, a / 2));
	}
}
