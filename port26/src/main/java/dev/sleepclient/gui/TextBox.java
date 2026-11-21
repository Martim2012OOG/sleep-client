package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import dev.sleepclient.Keys;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Editable text like Minecraft's own fields: caret, mouse click / drag selection, double click selects a word,
 * Ctrl+A / C / V / X, Ctrl+Backspace / Delete, arrows (+Shift to select, +Ctrl by word), Home / End.
 * (Cmd works instead of Ctrl on macOS.)
 */
public final class TextBox {

	private String text = "";
	private int cursor, anchor;
	public final int max;
	private final IntPredicate allowed;
	// last drawn position (for mouse hit tests)
	private int fx, fy, fw, fh, textX, scroll;
	private long lastClick;
	private boolean dragging;

	public TextBox(int max) {
		this(max, cp -> cp >= 32 && cp != 127);
	}

	public TextBox(int max, IntPredicate allowed) {
		this.max = max;
		this.allowed = allowed;
	}

	public String text() {
		return text;
	}

	public boolean isEmpty() {
		return text.isEmpty();
	}

	public void set(String s) {
		text = s == null ? "" : s.length() > max ? s.substring(0, max) : s;
		cursor = anchor = text.length();
		scroll = 0;
	}

	public void clear() {
		set("");
	}

	private int selStart() {
		return Math.min(cursor, anchor);
	}

	private int selEnd() {
		return Math.max(cursor, anchor);
	}

	public boolean hasSelection() {
		return cursor != anchor;
	}

	public String selected() {
		return text.substring(selStart(), selEnd());
	}

	/** Inserts (replacing the selection), dropping characters that are not allowed and anything past the limit. */
	public void insert(String s) {
		StringBuilder clean = new StringBuilder();
		s.codePoints().forEach(cp -> {
			if (allowed.test(cp)) {
				clean.appendCodePoint(cp);
			}
		});
		String before = text.substring(0, selStart()), after = text.substring(selEnd());
		int room = max - before.length() - after.length();
		String add = clean.length() > room ? clean.substring(0, Math.max(0, room)) : clean.toString();
		text = before + add + after;
		cursor = anchor = before.length() + add.length();
	}

	private void deleteSelection() {
		insert("");
	}

	private int wordLeft(int i) {
		while (i > 0 && text.charAt(i - 1) == ' ') {
			i--;
		}
		while (i > 0 && text.charAt(i - 1) != ' ') {
			i--;
		}
		return i;
	}

	private int wordRight(int i) {
		while (i < text.length() && text.charAt(i) == ' ') {
			i++;
		}
		while (i < text.length() && text.charAt(i) != ' ') {
			i++;
		}
		return i;
	}

	private static boolean ctrl() {
		return Keys.down(341) || Keys.down(345) || Keys.down(343) || Keys.down(347);
	}

	private static boolean shift() {
		return Keys.down(340) || Keys.down(344);
	}

	/** Editing keys. Returns true if handled (Enter / Esc are left to the screen). */
	public boolean key(int key) {
		boolean c = ctrl(), sh = shift();
		Minecraft mc = Minecraft.getInstance();
		if (c && key == 65) { // A
			anchor = 0;
			cursor = text.length();
			return true;
		}
		if (c && key == 67) { // C
			if (hasSelection()) {
				mc.keyboardHandler.setClipboard(selected());
			}
			return true;
		}
		if (c && key == 88) { // X
			if (hasSelection()) {
				mc.keyboardHandler.setClipboard(selected());
				deleteSelection();
			}
			return true;
		}
		if (c && key == 86) { // V
			String clip = mc.keyboardHandler.getClipboard();
			if (clip != null) {
				insert(clip.replace("\r", "").replace("\n", " "));
			}
			return true;
		}
		switch (key) {
			case 259 -> { // backspace
				if (hasSelection()) {
					deleteSelection();
				} else if (cursor > 0) {
					anchor = c ? wordLeft(cursor) : cursor - 1;
					deleteSelection();
				}
				return true;
			}
			case 261 -> { // delete
				if (hasSelection()) {
					deleteSelection();
				} else if (cursor < text.length()) {
					anchor = c ? wordRight(cursor) : cursor + 1;
					deleteSelection();
				}
				return true;
			}
			case 263 -> { // left
				if (hasSelection() && !sh) {
					cursor = anchor = selStart();
				} else {
					cursor = c ? wordLeft(cursor) : Math.max(0, cursor - 1);
					if (!sh) {
						anchor = cursor;
					}
				}
				return true;
			}
			case 262 -> { // right
				if (hasSelection() && !sh) {
					cursor = anchor = selEnd();
				} else {
					cursor = c ? wordRight(cursor) : Math.min(text.length(), cursor + 1);
					if (!sh) {
						anchor = cursor;
					}
				}
				return true;
			}
			case 268 -> { // home
				cursor = 0;
				if (!sh) {
					anchor = cursor;
				}
				return true;
			}
			case 269 -> { // end
				cursor = text.length();
				if (!sh) {
					anchor = cursor;
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	public void typed(int codepoint) {
		if (ctrl()) {
			return;
		}
		insert(new String(Character.toChars(codepoint)));
	}

	// ------------------------------------------------------------------ mouse
	public boolean inside(double mx, double my) {
		return mx >= fx && mx < fx + fw && my >= fy && my < fy + fh;
	}

	private int indexAt(double mx) {
		double rel = mx - textX + scroll;
		if (rel <= 0) {
			return 0;
		}
		for (int i = 1; i <= text.length(); i++) {
			int w = Gfx.twRaw(text.substring(0, i)), pw = Gfx.twRaw(text.substring(0, i - 1));
			if (rel < (w + pw) / 2.0) {
				return i - 1;
			}
		}
		return text.length();
	}

	/** Mouse down inside the field: place the caret (Shift extends the selection, double click selects a word). */
	public void click(double mx) {
		long now = System.currentTimeMillis();
		int i = indexAt(mx);
		if (now - lastClick < 300) {
			anchor = wordLeft(Math.min(text.length(), i + 1));
			cursor = wordRight(i);
		} else {
			cursor = i;
			if (!shift()) {
				anchor = i;
			}
		}
		lastClick = now;
		dragging = true;
	}

	public void drag(double mx) {
		if (dragging) {
			cursor = indexAt(mx);
		}
	}

	public void release() {
		dragging = false;
	}

	// ------------------------------------------------------------------ drawing
	/** Draws the text with selection and caret inside (x, y, w, h); text area starts at tx and is maxW wide. */
	public void draw(GuiGraphicsExtractor c, int x, int y, int w, int h, int tx, int maxW, boolean focused, int accent, int textCol) {
		fx = x;
		fy = y;
		fw = w;
		fh = h;
		textX = tx;
		int caretX = Gfx.twRaw(text.substring(0, cursor));
		if (caretX - scroll > maxW - 2) {
			scroll = caretX - maxW + 2;
		}
		if (caretX - scroll < 0) {
			scroll = caretX;
		}
		scroll = Math.max(0, Math.min(scroll, Math.max(0, Gfx.twRaw(text) - maxW + 2)));
		c.enableScissor(tx - 1, y + 1, tx + maxW + 1, y + h - 1);
		int ty = y + (h - 9) / 2;
		if (focused && hasSelection()) {
			int sx = tx + Gfx.twRaw(text.substring(0, selStart())) - scroll;
			int ex = tx + Gfx.twRaw(text.substring(0, selEnd())) - scroll;
			c.fill(sx, ty - 1, ex, ty + 10, Gfx.withAlpha(accent, 0x88));
		}
		Gfx.textRaw(c, text, tx - scroll, ty, textCol);
		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = tx + caretX - scroll;
			c.fill(cx, ty - 1, cx + 1, ty + 10, 0xFFFFFFFF);
		}
		c.disableScissor();
	}
}
