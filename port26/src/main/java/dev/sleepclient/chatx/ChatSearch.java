package dev.sleepclient.chatx;

import dev.sleepclient.Gfx;
import dev.sleepclient.mixin.overlay.ChatHudAccessor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Chat search (Chat Search mod). While searching, what you type in the chat box is the search: the chat shows only
 * the lines with it, the word highlighted (the selected match in orange). Enter / arrows = next / previous,
 * Esc or X = stop searching (the chat comes back as it was, with any new messages).
 */
public final class ChatSearch {

	public static boolean active;
	private static List<GuiMessage> snapshot = List.of();
	private static final Set<GuiMessage> ours = Collections.newSetFromMap(new IdentityHashMap<>());
	private static String lastQuery = null;
	private static int lastIndex = -1;
	public static int index, count;
	private static String savedDraft = "";
	private static RecordComponent contentComp, sigComp;

	private ChatSearch() {
	}

	private static ChatComponent hud() {
		return dev.sleepclient.compat.Mc.chat(Minecraft.getInstance());
	}

	@SuppressWarnings("unchecked")
	private static List<GuiMessage> messages() {
		return ((ChatHudAccessor) hud()).plegumo$messages();
	}

	private static Object comp(GuiMessage l, boolean content) {
		try {
			if (contentComp == null) {
				for (RecordComponent rc : GuiMessage.class.getRecordComponents()) {
					if (Component.class.isAssignableFrom(rc.getType())) {
						contentComp = rc;
					} else if (rc.getType().getSimpleName().contains("Signature") || rc.getType().getName().contains("class_7469")) {
						sigComp = rc;
					}
				}
			}
			RecordComponent rc = content ? contentComp : sigComp;
			return rc == null ? null : rc.getAccessor().invoke(l);
		} catch (Exception e) {
			return null;
		}
	}

	public static void start(String currentText) {
		if (active) {
			return;
		}
		active = true;
		savedDraft = currentText == null ? "" : currentText;
		snapshot = new ArrayList<>(messages());
		ours.clear();
		lastQuery = null;
		index = 0;
		count = 0;
	}

	/** Stops searching; returns what was typed in the chat box before. */
	public static String stop() {
		if (!active) {
			return "";
		}
		active = false;
		List<GuiMessage> now = messages();
		List<GuiMessage> fresh = new ArrayList<>();
		for (GuiMessage l : now) {
			if (!ours.contains(l)) {
				fresh.add(l); // arrived while searching
			}
		}
		now.clear();
		now.addAll(fresh);
		now.addAll(snapshot);
		ours.clear();
		snapshot = List.of();
		((ChatHudAccessor) hud()).plegumo$refresh();
		hud().resetChatScroll();
		return savedDraft;
	}

	public static void next(int dir) {
		if (count == 0) {
			return;
		}
		index = Math.floorMod(index + dir, count);
	}

	/** Called every frame with the text of the chat box. */
	public static void update(String query) {
		if (!active) {
			return;
		}
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (q.equals(lastQuery) && index == lastIndex) {
			return;
		}
		if (!q.equals(lastQuery)) {
			index = 0;
		}
		lastQuery = q;
		lastIndex = index;
		List<GuiMessage> now = messages();
		// keep new messages that came in while searching
		for (GuiMessage l : now) {
			if (!ours.contains(l) && !snapshot.contains(l)) {
				snapshot.add(0, l);
			}
		}
		now.clear();
		ours.clear();
		if (q.isEmpty()) {
			now.addAll(snapshot);
			ours.addAll(snapshot);
			count = 0;
		} else {
			int n = 0;
			for (GuiMessage l : snapshot) {
				Object c = comp(l, true);
				if (c instanceof Component t && t.getString().toLowerCase(Locale.ROOT).contains(q)) {
					GuiMessage h = new GuiMessage(l.addedTime(), highlight(t, q, n == index), (net.minecraft.network.chat.MessageSignature) comp(l, false),
							l.source(), l.tag());
					now.add(h);
					ours.add(h);
					n++;
				}
			}
			count = n;
			if (index >= count) {
				index = Math.max(0, count - 1);
			}
		}
		((ChatHudAccessor) hud()).plegumo$refresh();
		hud().resetChatScroll();
		if (index > 0) {
			hud().scrollChat(index); // messages are newest first: older matches are further up
		}
	}

	private static Component highlight(Component t, String q, boolean current) {
		String plain = t.getString();
		String low = plain.toLowerCase(Locale.ROOT);
		boolean[] hit = new boolean[plain.length()];
		for (int i = low.indexOf(q); i >= 0; i = low.indexOf(q, i + 1)) {
			for (int k = i; k < i + q.length() && k < hit.length; k++) {
				hit[k] = true;
			}
		}
		MutableComponent out = Component.empty();
		int[] pos = {0};
		Style mark = Style.EMPTY.withColor(TextColor.fromRgb(current ? 0xFF9F1A : 0xFFD54F)).withBold(true).withUnderlined(true);
		t.visit((style, s) -> {
			int start = pos[0];
			StringBuilder seg = new StringBuilder();
			boolean segHit = false;
			for (int k = 0; k < s.length(); k++) {
				boolean h = start + k < hit.length && hit[start + k];
				if (seg.length() > 0 && h != segHit) {
					out.append(Component.literal(seg.toString()).setStyle(segHit ? mark.applyTo(style) : style));
					seg.setLength(0);
				}
				segHit = h;
				seg.append(s.charAt(k));
			}
			if (seg.length() > 0) {
				out.append(Component.literal(seg.toString()).setStyle(segHit ? mark.applyTo(style) : style));
			}
			pos[0] += s.length();
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	// ------------------------------------------------------------------ drawing (ChatScreen)
	public static int bx, by, bw = 16, bh = 14;
	public static int[] upBtn, downBtn, closeBtn;

	/** Search button (and the search bar while searching) above the chat. */
	public static void render(GuiGraphicsExtractor c, int screenH, int mouseX, int mouseY) {
		var o = Minecraft.getInstance().options;
		double scale = o.chatScale().get();
		int chatH = (int) Math.round(ChatComponent.getHeight(o.chatHeightFocused().get()) * scale);
		int w = Math.max(160, (int) Math.round(ChatComponent.getWidth(o.chatWidth().get()) * scale) + 8);
		int top = screenH - 40 - chatH - 18;
		bx = 2;
		by = Math.max(2, top);
		if (!active) {
			boolean h = in(mouseX, mouseY, bx, by, bw, bh);
			Gfx.button(c, bx, by, bw, bh, h, false);
			Gfx.tex(c, dev.sleepclient.gui.Theme.iconPublic("chat_search"), bx + 3, by + 2, 10, 10, 64, 64, 0xFFE6E8EE);
			return;
		}
		Gfx.rrect(c, bx, by, w, 16, 4, 0xE0101218);
		Gfx.tex(c, dev.sleepclient.gui.Theme.iconPublic("chat_search"), bx + 4, by + 3, 10, 10, 64, 64, 0xFFB4C6FF);
		String q = lastQuery == null || lastQuery.isEmpty() ? "Type in the chat box to search..." : "Searching: " + lastQuery;
		Gfx.text(c, q, bx + 18, by + 4, lastQuery == null || lastQuery.isEmpty() ? 0xFF7D8190 : 0xFFE6E8EE);
		String cnt = count == 0 ? "0/0" : (index + 1) + "/" + count;
		int rx = bx + w - 4;
		closeBtn = new int[]{rx - 12, by + 2, 12, 12};
		Gfx.tex(c, dev.sleepclient.gui.Theme.iconPublic("close"), rx - 11, by + 3, 10, 10, 64, 64, in(mouseX, mouseY, closeBtn) ? 0xFFFF6B6B : 0xFFB0B4C0);
		rx -= 16;
		downBtn = new int[]{rx - 12, by + 2, 12, 12};
		Gfx.textC(c, "v", rx - 6, by + 4, in(mouseX, mouseY, downBtn) ? 0xFFFFFFFF : 0xFFB0B4C0);
		rx -= 14;
		upBtn = new int[]{rx - 12, by + 2, 12, 12};
		Gfx.textC(c, "^", rx - 6, by + 5, in(mouseX, mouseY, upBtn) ? 0xFFFFFFFF : 0xFFB0B4C0);
		rx -= 16;
		Gfx.text(c, cnt, rx - Gfx.tw(cnt), by + 4, 0xFF9AA0B4);
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static boolean in(int mx, int my, int[] r) {
		return r != null && in(mx, my, r[0], r[1], r[2], r[3]);
	}

	/** Click: 0 nothing, 1 toggle, 2 up, 3 down, 4 close. */
	public static int click(int mx, int my) {
		if (!active) {
			return in(mx, my, bx, by, bw, bh) ? 1 : 0;
		}
		if (in(mx, my, closeBtn)) {
			return 4;
		}
		if (in(mx, my, upBtn)) {
			return 2;
		}
		if (in(mx, my, downBtn)) {
			return 3;
		}
		return 0;
	}
}
