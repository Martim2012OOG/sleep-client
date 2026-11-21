package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Every Sleep notification (v99), drawn exactly like the user's notificacao.html: bottom-left, one card at a time
 * (a new one replaces the old one), sliding in from the left. Navy card with a dark edge and a dark line under it,
 * a blue square icon, a white title, a grey line and a picture on the right: a player's face (square) or a little
 * pixel landscape / the clip (16:9).
 */
public final class Notify {

	public enum Kind {
		MSG("msg"), SENT("sent"), PLAY("play"), ONLINE("online"), REC("rec"), CLIP("clip"), ERROR("error"), INFO("info"), VOICE("voice"),
		FRIEND("friend"), DOWNLOAD("download"), DONE("check"), KUBO("kubo");

		final String icon;

		Kind(String icon) {
			this.icon = icon;
		}
	}

	private record Card(Kind kind, String title, String sub, boolean rawSub, String face, String scene, Identifier tex, int texW, int texH, long at,
			Runnable action) {
		Card(Kind kind, String title, String sub, boolean rawSub, String face, String scene, Identifier tex, int texW, int texH, long at) {
			this(kind, title, sub, rawSub, face, scene, tex, texW, texH, at, null);
		}
	}

	/** Cards that arrived while another one was showing: they wait their turn instead of replacing it (so a message or an invite is never lost). */
	private static final java.util.ArrayDeque<Card> QUEUE = new java.util.ArrayDeque<>();

	private static final long DURATION = 4500L, IN_MS = 320L, OUT_MS = 250L;
	private static volatile Card card;
	private static volatile long closingAt;
	private static final Map<String, Identifier> SCENES = new HashMap<>();

	private Notify() {
	}

	// ------------------------------------------------------------------ API (any thread)
	public static void show(Kind kind, String title, String sub) {
		put(new Card(kind, title, sub, false, null, null, null, 0, 0, 0));
	}

	/** With a player's face on the right (messages, friends, voice). The second line is shown as typed. */
	public static void show(Kind kind, String title, String sub, String faceName) {
		put(new Card(kind, title, sub, kind == Kind.MSG || kind == Kind.SENT, faceName, null, null, 0, 0, 0));
	}

	/** A card you can click: it shows the person's head, and clicking it runs 'onClick' (join the server of an invite...). */
	public static void clickable(Kind kind, String title, String sub, String faceName, Runnable onClick) {
		put(new Card(kind, title, sub, true, faceName, null, null, 0, 0, 0, onClick));
	}

	/** "X is playing": with the little pixel landscape (16:9), different for every name. */
	public static void playing(String title, String sub, String name) {
		put(new Card(Kind.PLAY, title, sub, true, null, name == null ? "?" : name, null, 0, 0, 0));
	}

	private static final String ICON_SCENE = "\u0000icon";

	/** A toast whose icon is the real picture of an item (bought / equipped), instead of the blue Sleep square. */
	public static void item(Kind kind, String title, String sub, Identifier tex, int w, int h) {
		put(new Card(kind, title, sub, false, null, ICON_SCENE, tex, w, h, 0));
	}

	/** With a picture of the clip / recording (16:9). */
	public static void picture(Kind kind, String title, String sub, Identifier tex, int w, int h) {
		put(new Card(kind, title, sub, false, null, null, tex, w, h, 0));
	}

	private static void put(Card c) {
		String sub = c.sub();
		if (dev.sleepclient.ext.KuboApi.toastsHidden() && (c.kind() == Kind.MSG || c.kind() == Kind.SENT)) {
			sub = dev.sleepclient.Lang.tr("New message", "Nova mensagem"); // Streamer Mode: no message text on screen
		}
		Card n = new Card(c.kind(), c.title(), sub, c.rawSub(), c.face(), c.scene(), c.tex(), c.texW(), c.texH(), System.currentTimeMillis(), c.action());
		synchronized (QUEUE) {
			Card cur = card;
			boolean busy = cur != null && System.currentTimeMillis() < (closingAt > 0 ? closingAt : cur.at() + DURATION) + OUT_MS;
			if (busy) {
				// the same card again (a burst) is not queued twice
				for (Card q : QUEUE) {
					if (q.title().equals(n.title()) && java.util.Objects.equals(q.sub(), n.sub())) {
						return;
					}
				}
				QUEUE.addLast(n);
				while (QUEUE.size() > 6) {
					QUEUE.pollFirst();
				}
				// somebody is waiting: do not keep the one on screen for the whole time
				if (closingAt == 0 && System.currentTimeMillis() - cur.at() > 1800L) {
					closingAt = System.currentTimeMillis();
				}
				return;
			}
			card = n;
			closingAt = 0;
		}
		ping(c.kind());
	}

	/** The card on screen is over: the next waiting one comes in. */
	private static void next() {
		Card n;
		synchronized (QUEUE) {
			n = QUEUE.pollFirst();
			card = n == null ? null : new Card(n.kind(), n.title(), n.sub(), n.rawSub(), n.face(), n.scene(), n.tex(), n.texW(), n.texH(),
					System.currentTimeMillis(), n.action());
			closingAt = 0;
		}
		if (n != null) {
			ping(n.kind());
		}
	}

	private static long lastPing;

	/** The sound of a card: the Sleep "pling" or the one picked for its kind (Settings > HUD Options > Notifications). */
	private static void ping(Kind kind) {
		var s = dev.sleepclient.SleepClient.settings;
		long now = System.currentTimeMillis();
		if (s == null || !s.notifySound.on() || s.notifyVolume.f() <= 0 || now - lastPing < 400L) {
			return; // a burst of cards = one pling
		}
		lastPing = now;
		float vol = s.notifyVolume.f() / 100F;
		String picked = s.notifySoundFor(kind);
		if (dev.sleepclient.SoundLib.SILENT.equals(picked)) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> {
			try {
				if (!picked.isEmpty()) {
					dev.sleepclient.SoundLib.play(picked, vol, 1F);
					return;
				}
				mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(
						net.minecraft.sound.SoundEvent.of(Gfx.id("notify")), 1F, vol));
			} catch (Throwable t) {
				System.err.println("[Sleep] notification sound: " + t);
			}
		});
	}

	public static boolean showing() {
		Card c = card;
		return c != null && System.currentTimeMillis() - c.at() < DURATION + OUT_MS;
	}

	/** Click on the card (with a screen open): it goes away, like in the html. */
	public static boolean click(double mx, double my) {
		int[] r = lastRect;
		Card k = card;
		if (k != null && r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
			closingAt = System.currentTimeMillis();
			if (k.action() != null) {
				MinecraftClient.getInstance().execute(k.action()); // e.g. join the server of the invite
			}
			return true;
		}
		return false;
	}

	private static int[] lastRect;

	// ------------------------------------------------------------------ drawing
	/** cubic-bezier(.2,.9,.3,1.1): fast in, a tiny overshoot at the end. */
	private static float easeIn(float t) {
		t = Math.max(0F, Math.min(1F, t));
		float lo = 0F, hi = 1F, u = t;
		for (int i = 0; i < 18; i++) {
			u = (lo + hi) / 2F;
			float x = bez(u, 0.2F, 0.3F);
			if (x < t) {
				lo = u;
			} else {
				hi = u;
			}
		}
		return bez(u, 0.9F, 1.1F);
	}

	private static float bez(float u, float p1, float p2) {
		float v = 1F - u;
		return 3F * v * v * u * p1 + 3F * v * u * u * p2 + u * u * u;
	}

	/** The size setting (Settings > HUD Options > Notification Size): 0.5 .. 1.5. */
	private static float sizeScale() {
		var st = dev.sleepclient.SleepClient.settings;
		return st == null ? 1F : Math.max(0.2F, Math.min(3F, st.notifySize.f() / 100F));
	}

	public static void draw(DrawContext c) {
		float s = sizeScale();
		if (Math.abs(s - 1F) < 0.01F) {
			drawCard(c, 1F);
			return;
		}
		float prev = Gfx.scaleOverride;
		c.getMatrices().pushMatrix();
		c.getMatrices().scale(s, s);
		Gfx.scaleOverride = (float) (MinecraftClient.getInstance().getWindow().getScaleFactor() * s);
		try {
			drawCard(c, s);
		} finally {
			c.getMatrices().popMatrix();
			Gfx.scaleOverride = prev;
		}
	}

	/** Draws the card in its own units; everything is scaled by {@code size} (the click area is kept in screen units). */
	private static void drawCard(DrawContext c, float size) {
		Card k = card;
		if (k == null) {
			lastRect = null;
			return;
		}
		long now = System.currentTimeMillis();
		long age = now - k.at();
		long closeStart = closingAt > 0 ? closingAt : k.at() + DURATION;
		if (now > closeStart + OUT_MS) {
			lastRect = null;
			next(); // shows the next waiting card (or none)
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		int sh = Math.round(mc.getWindow().getScaledHeight() / size);
		// sizes from the html (card 1065 x 200 in the print), for a 180 wide card: 1 unit = 1.8 px
		float u = 1.8F;
		int w = 180, h = Math.round(18.8F * u);
		float slide, alpha = 1F;
		if (now >= closeStart) {
			float t = (now - closeStart) / (float) OUT_MS;
			t = t * t * (3F - 2F * t); // ease
			slide = -1.15F * t;
			alpha = 1F - t;
		} else {
			slide = -1.15F * (1F - easeIn(age / (float) IN_MS));
		}
		int x = Math.round(5 + slide * (w + 5));
		int bottom = dev.sleepclient.voice.VoiceIcons.visible() ? 26 : 7;
		int y = sh - Math.round(bottom / size) - h - 2;
		int a = Math.round(255 * Math.max(0F, Math.min(1F, alpha)));
		if (a < 6) {
			return;
		}
		lastRect = new int[] {Math.round(x * size), Math.round(y * size), Math.round(w * size), Math.round(h * size)};
		int edge = 0x0D111B, cardCol = 0x1D2435;
		// soft shadow + the dark line under the card + the card with its edge
		Gfx.rrectFree(c, x - 1, y + 2, w + 2, h + 3, 4F, Gfx.withAlpha(0x000000, a * 50 / 255));
		Gfx.rrectFree(c, x - 1, y - 1, w + 2, h + 3, 3.6F, Gfx.withAlpha(edge, a));
		Gfx.rrectFree(c, x, y, w, h, 2.9F, Gfx.withAlpha(cardCol, a));

		// icon: blue square, dark edge, light line at the top, white glyph with a small shadow
		int is = Math.round(9F * u), ix = Math.round(x + 3.6F * u), iy = y + (h - is) / 2;
		boolean red = k.kind() == Kind.ERROR;
		boolean itemIcon = ICON_SCENE.equals(k.scene()) && k.tex() != null;
		Gfx.rrectFree(c, ix - 1, iy - 1, is + 2, is + 2, 2.6F, Gfx.withAlpha(edge, a));
		if (itemIcon) {
			// the real picture of the item (its own colours, kept in proportion)
			Gfx.rrectFree(c, ix, iy, is, is, 2.4F, Gfx.withAlpha(0x10141F, a));
			Gfx.tex(c, k.tex(), ix, iy, is, is, k.texW(), k.texH(), Gfx.withAlpha(0xFFFFFF, a));
		} else {
			gradient(c, ix, iy, is, is, red ? 0xFF6B6B : 0x4C88FF, red ? 0xD93636 : 0x2D62E2, a);
			Gfx.rrectFree(c, ix + 1, iy, is - 2, 0.7F, 0.3F, Gfx.withAlpha(0xFFFFFF, a * 56 / 255));
			int gs = Math.round(6.2F * u), gx = ix + (is - gs) / 2, gy = iy + (is - gs) / 2;
			Identifier glyph = Gfx.id("textures/gui/notify/" + k.kind().icon + ".png");
			Gfx.tex(c, glyph, gx, gy + 1, gs, gs, 64, 64, Gfx.withAlpha(0x000000, a / 5));
			Gfx.tex(c, glyph, gx, gy, gs, gs, 64, 64, Gfx.withAlpha(0xFFFFFF, a));
		}

		// picture on the right
		boolean wide = !itemIcon && (k.scene() != null || k.tex() != null);
		boolean square = !wide && k.face() != null;
		int th = Math.round(12.4F * u), tw = wide ? Math.round(21.2F * u) : square ? th : 0;
		int tx = x + w - Math.round(4.2F * u) - tw, ty = y + (h - th) / 2;
		if (tw > 0) {
			Gfx.rrectFree(c, tx - 1.3F, ty - 1.3F, tw + 2.6F, th + 2.6F, 2.4F, Gfx.withAlpha(0xFFFFFF, a * 20 / 255));
			Gfx.rrectFree(c, tx - 1, ty - 1, tw + 2, th + 2, 2F, Gfx.withAlpha(edge, a));
			if (k.tex() != null) {
				Gfx.tex(c, k.tex(), tx, ty, tw, th, k.texW(), k.texH(), Gfx.withAlpha(0xFFFFFF, a));
			} else if (wide) {
				Gfx.tex(c, scene(k.scene()), tx, ty, tw, th, 32, 18, Gfx.withAlpha(0xFFFFFF, a));
			} else {
				Gfx.rrect(c, tx, ty, tw, th, 1, Gfx.withAlpha(0xC99B74, a));
				Faces.draw(c, k.face(), tx, ty, th, a);
			}
		}

		// title + line (cut with … so they never leave the card)
		int textX = Math.round(ix + is + 3.3F * u), textW = (tw > 0 ? tx - Math.round(2F * u) : x + w - Math.round(4.2F * u)) - textX;
		String title = dev.sleepclient.Lang.t(k.title());
		String sub = k.sub() == null ? "" : k.rawSub() ? k.sub() : dev.sleepclient.Lang.t(k.sub());
		int titleY = y + h / 2 - (sub.isEmpty() ? 4 : 9);
		c.drawText(mc.textRenderer, Gfx.raw(cut(title, textW, 1F)), textX, titleY, Gfx.withAlpha(0xFFFFFF, Math.max(a, 20)), false);
		if (!sub.isEmpty()) {
			float s = 0.86F;
			var m = c.getMatrices();
			m.pushMatrix();
			m.translate(textX, titleY + 11);
			m.scale(s, s);
			Text line = Gfx.raw(cut(sub, Math.round(textW / s), 1F));
			if (k.rawSub()) {
				dev.sleepclient.chat.Emoji.draw(c, cut(sub, Math.round(textW / s), 1F), 0, 0, Gfx.withAlpha(0x8C95A8, Math.max(a, 20)));
			} else {
				c.drawText(mc.textRenderer, line, 0, 0, Gfx.withAlpha(0x8C95A8, Math.max(a, 20)), false);
			}
			m.popMatrix();
		}
	}

	private static String cut(String s, int maxW, float scale) {
		if (Gfx.twRaw(s) * scale <= maxW) {
			return s;
		}
		while (s.length() > 1 && Gfx.twRaw(s + "..") * scale > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s.strip() + "..";
	}

	/** linear-gradient(180deg, top, bottom) inside a rounded square. */
	private static void gradient(DrawContext c, int x, int y, int w, int h, int top, int bottom, int a) {
		Gfx.rrectFree(c, x, y, w, h, 2F, Gfx.withAlpha(bottom, a));
		for (int i = 0; i < h; i++) {
			float t = i / (float) Math.max(1, h - 1);
			int col = Gfx.blend(top | 0xFF000000, bottom | 0xFF000000, t);
			int inset = i == 0 || i == h - 1 ? 1 : 0;
			c.fill(x + inset, y + i, x + w - inset, y + i + 1, Gfx.withAlpha(col, a));
		}
	}

	// ------------------------------------------------------------------ pixel landscape (same as the html, seeded by name)
	private static Identifier scene(String name) {
		Identifier id = SCENES.get(name);
		if (id != null) {
			return id;
		}
		NativeImage img = new NativeImage(32, 18, false);
		int[] h = {0};
		for (char ch : name.toCharArray()) {
			h[0] = h[0] * 31 + ch;
		}
		java.util.function.DoubleSupplier r = () -> {
			h[0] = h[0] * 1103515245 + 12345;
			return (h[0] & 0xFFFFFFFFL) / 4294967296.0;
		};
		for (int y = 0; y < 9; y++) {
			int col = hsl(210, 0.8, (78 - y * 2) / 100.0);
			for (int x = 0; x < 32; x++) {
				img.setColorArgb(x, y, col);
			}
		}
		for (int y = 9; y < 18; y++) {
			for (int x = 0; x < 32; x++) {
				img.setColorArgb(x, y, 0xFF7A4F2F);
			}
		}
		for (int i = 0; i < 32; i++) {
			int top = 9 + (int) Math.floor(r.getAsDouble() * 2);
			int grass = r.getAsDouble() > .5 ? 0xFF6AA84F : 0xFF5D9944;
			img.setColorArgb(i, top, grass);
			img.setColorArgb(i, top + 1, grass);
			for (int y = top + 2; y < 18; y++) {
				img.setColorArgb(i, y, r.getAsDouble() > .5 ? 0xFF8A5A36 : 0xFF7A4F2F);
			}
		}
		for (int y = 3; y < 8; y++) {
			for (int x = 3; x < 8; x++) {
				img.setColorArgb(x, y, 0xFF3F7A2C);
			}
		}
		img.setColorArgb(5, 8, 0xFF6B4A2B);
		img.setColorArgb(5, 9, 0xFF6B4A2B);
		for (int x = 21; x < 27; x++) {
			img.setColorArgb(x, 2, 0xFFFFFFFF);
			img.setColorArgb(x, 3, 0xFFFFFFFF);
		}
		for (int x = 23; x < 26; x++) {
			img.setColorArgb(x, 1, 0xFFFFFFFF);
		}
		String key = Integer.toHexString(name.hashCode());
		NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "kubo_notify_scene_" + key, img);
		tex.upload();
		id = Gfx.id("notify_scene_" + key);
		MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
		if (SCENES.size() > 32) {
			SCENES.clear();
		}
		SCENES.put(name, id);
		return id;
	}

	private static int hsl(double hDeg, double s, double l) {
		double c = (1 - Math.abs(2 * l - 1)) * s, hp = hDeg / 60.0, x = c * (1 - Math.abs(hp % 2 - 1));
		double r = 0, g = 0, b = 0;
		if (hp < 1) { r = c; g = x; } else if (hp < 2) { r = x; g = c; } else if (hp < 3) { g = c; b = x; }
		else if (hp < 4) { g = x; b = c; } else if (hp < 5) { r = x; b = c; } else { r = c; b = x; }
		double m = l - c / 2;
		return 0xFF000000 | (int) Math.round((r + m) * 255) << 16 | (int) Math.round((g + m) * 255) << 8 | (int) Math.round((b + m) * 255);
	}
}
