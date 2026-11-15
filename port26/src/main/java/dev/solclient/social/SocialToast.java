package dev.solclient.social;

import dev.solclient.Gfx;
import dev.solclient.SolClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pop-up in the notifications corner (top right by default, Settings > Notifications Position) for new
 * notifications and friend messages. Messages show the friend's head, name and the message, like Discord.
 * When a "Clip saved" card is showing in the same corner, this one sits right under it.
 */
public final class SocialToast {

	private static final long SHOW_MS = 5_000L;
	private static volatile String title, line;
	private static volatile String nick; // set for friend messages: draws their head
	private static volatile long shownAt;

	private SocialToast() {
	}

	public static void show(String t, String l) {
		showAs(t, l, null);
	}

	/** A message from a friend: head + name + text. */
	public static void message(String friendNick, String text) {
		showAs(friendNick, text, friendNick == null ? "" : friendNick);
	}

	private static void showAs(String t, String l, String who) {
		// v99: every notification is the bottom-left card of notificacao.html (gui.Notify)
		if (who != null) {
			dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.MSG, dev.solclient.Lang.tr("Message from ", "Mensagem de ") + t, l, who.isEmpty() ? t : who);
			dev.solclient.social.KuboLive.stoppedTyping(who.isEmpty() ? t : who);
		} else {
			dev.solclient.gui.Notify.Kind k = kindFor(t, l);
			dev.solclient.gui.Notify.show(k, t, l);
		}
		// (the sound is the notification "pling" now: gui.Notify)
	}

	/** Picks the icon of a card from its text (title first, then the line), so each card gets a fitting glyph. */
	private static dev.solclient.gui.Notify.Kind kindFor(String t, String l) {
		String low = ((t == null ? "" : t) + "\n" + (l == null ? "" : l)).toLowerCase(java.util.Locale.ROOT);
		String title = t == null ? "" : t.toLowerCase(java.util.Locale.ROOT);
		if (has(low, "could not", "failed", "error", "erro", "falh", "não foi possível", "nao foi possivel")) {
			return dev.solclient.gui.Notify.Kind.ERROR;
		}
		if (has(title, "friend", "amig")) {
			return dev.solclient.gui.Notify.Kind.FRIEND;
		}
		if (has(title, "invite", "convite", "convid", "party", "playing", "a jogar")) {
			return dev.solclient.gui.Notify.Kind.PLAY;
		}
		if (has(title, "online")) {
			return dev.solclient.gui.Notify.Kind.ONLINE;
		}
		if (has(title, "voice", "voz", "call", "chamada")) {
			return dev.solclient.gui.Notify.Kind.VOICE;
		}
		if (has(title, "message", "mensage", "chat")) {
			return dev.solclient.gui.Notify.Kind.MSG;
		}
		if (has(title, "fightcam", "clip", "replay", "record", "grava")) {
			return dev.solclient.gui.Notify.Kind.CLIP;
		}
		if (has(low, "download", "update", "atualiz", "transfer")) {
			return dev.solclient.gui.Notify.Kind.DOWNLOAD;
		}
		if (has(low, "saved", "guardad", "done", "conclu", "added", "adicionad", "pronto")) {
			return dev.solclient.gui.Notify.Kind.DONE;
		}
		return dev.solclient.gui.Notify.Kind.KUBO;
	}

	private static boolean has(String s, String... words) {
		for (String w : words) {
			if (s.contains(w)) {
				return true;
			}
		}
		return false;
	}

	private static void showAsOld(String t, String l, String who) {
		if (dev.solclient.ext.KuboApi.toastsHidden()) {
			l = dev.solclient.Lang.tr("New message", "Nova mensagem"); // Streamer Mode: no message text on screen
		}
		title = t;
		line = l;
		nick = who;
		shownAt = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		if (who != null && mc.player != null) {
			mc.execute(() -> {
				if (mc.player != null) {
					mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 0.5F, 1.8F);
				}
			});
		}
	}

	/** Where toasts go: 0 top right, 1 top left, 2 bottom right, 3 bottom left. */
	public static int corner() {
		return SolClient.settings == null ? 0 : SolClient.settings.toastPosition.idx();
	}

	public static void draw(GuiGraphicsExtractor c) {
		if (true) {
			return; // drawn by gui.Notify now
		}
		String t = title;
		if (t == null) {
			return;
		}
		long age = System.currentTimeMillis() - shownAt;
		if (age > SHOW_MS) {
			title = null;
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float in = Math.min(1F, age / 220F);
		float out = Math.min(1F, (SHOW_MS - age) / 300F);
		float a = Math.min(in, out);
		in = 1 - (1 - in) * (1 - in) * (1 - in);
		String l = line;
		String who = nick;
		boolean msg = who != null;
		int textX = msg ? 34 : 22;
		int w = Math.max(Gfx.twRaw(t), l == null ? 0 : Gfx.twRaw(l)) + textX + 12;
		w = Math.max(msg ? 150 : 100, Math.min(w, 230));
		int h = msg ? 38 : l == null || l.isEmpty() ? 24 : 34;
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		int pos = corner();
		boolean left = pos == 1 || pos == 3, bottom = pos >= 2;
		int slide = Math.round((1F - in) * 40F);
		int x = left ? 6 - slide : sw - w - 6 + slide;
		int stack = dev.solclient.fightcam.ClipToast.occupied(); // under the clip card, if it is showing
		int y = bottom ? sh - h - 30 - stack : 6 + stack;
		int alpha = Math.round(a * 240);
		if (alpha < 8) {
			return;
		}
		Gfx.rrectBorder(c, x, y, w, h, 8, Gfx.withAlpha(0x15171D, alpha), Gfx.withAlpha(0x3A3D49, alpha));
		int accent = SolClient.settings.uiColour.c() | 0xFF000000;
		if (msg) {
			drawHead(c, who, x + 8, y + 9, 20, alpha);
			Gfx.circle(c, x + 27, y + 28, 4, Gfx.withAlpha(0x15171D, alpha));
			Gfx.circle(c, x + 27, y + 28, 2.6F, Gfx.withAlpha(0x4ADE80, alpha));
			Gfx.textRaw(c, clip(t, w - textX - 10), x + textX, y + 8, Gfx.withAlpha(0xFFFFFF, Math.max(alpha, 30)));
			if (l != null) {
				dev.solclient.chat.Emoji.draw(c, clip(l, w - textX - 10), x + textX, y + 21, Gfx.withAlpha(0xC4C8D4, Math.max(alpha, 30)));
			}
			// little progress line: how long it stays
			int pw = Math.round((w - 16) * (1F - age / (float) SHOW_MS));
			c.fill(x + 8, y + h - 3, x + 8 + pw, y + h - 2, Gfx.withAlpha(accent, alpha / 2));
			return;
		}
		Gfx.circle(c, x + 12, y + h / 2F, 4, Gfx.withAlpha(accent, alpha));
		Gfx.text(c, clip(t, w - 30), x + 22, y + 6, Gfx.withAlpha(0xF1F3F8, Math.max(alpha, 30)));
		if (l != null && !l.isEmpty()) {
			Gfx.text(c, clip(l, w - 30), x + 22, y + 18, Gfx.withAlpha(0x8C90A0, Math.max(alpha, 30)));
		}
	}

	private static void drawHead(GuiGraphicsExtractor c, String who, int x, int y, int s, int alpha) {
		Minecraft mc = Minecraft.getInstance();
		Gfx.rrect(c, x - 1, y - 1, s + 2, s + 2, 4, Gfx.withAlpha(0x0A0B0E, alpha));
		try {
			var nh = mc.getConnection();
			var e = nh == null || who.isEmpty() ? null : nh.getPlayerInfo(who);
			if (e != null) {
				net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(c, e.getSkin(), x, y, s);
				return;
			}
		} catch (Throwable ignored) {
		}
		RemoteTextures.Tex t = FaceSkins.of(who);
		if (t != null) {
			dev.solclient.gui.AccountsScreen.face(c, t, x, y, s);
		} else {
			Gfx.rrect(c, x, y, s, s, 4, Gfx.withAlpha(0x2C2F38, alpha));
			Gfx.textRaw(c, who.isEmpty() ? "?" : who.substring(0, 1).toUpperCase(), x + s / 2 - 3, y + s / 2 - 4, 0xFFFFFFFF);
		}
	}

	private static String clip(String s, int maxW) {
		if (s == null) {
			return "";
		}
		if (Gfx.twRaw(s) <= maxW) {
			return s;
		}
		while (s.length() > 1 && Gfx.twRaw(s + "..") > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}
}
