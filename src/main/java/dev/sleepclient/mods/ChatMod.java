package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Chat: size and background, timestamps, stacked spam, mention sound and name highlight, a swear-word filter (own word list
 * for pt / en / es) and a key that hides the chat.
 */
public class ChatMod extends Mod {

	private final Option.Header hSize = header("Size and look");
	private final Option.Num width = num("Width", 320, 40, 320, 1, "");
	private final Option.Num closedHeight = num("Height (Closed)", 90, 20, 180, 1, "");
	private final Option.Num openHeight = num("Height (Open)", 180, 20, 180, 1, "");
	private final Option.Bool background = bool("Background", true);
	private final Option.Num backgroundOpacity = num("Background Opacity", 50, 0, 100, 1, "%");
	private final Option.Header hGeneral = header("General");
	private final Option.Bool timestamps = bool("Timestamps", false);
	private final Option.Choice timestampFormat = choice("Timestamp Format", 0, "14:05", "2:05 PM", "14:05:09");
	private final Option.Bool stackSpam = bool("Stack Spam Messages", false).tip("Identical messages one after the other become one line with (x2), (x3)...");
	private final Option.Key toggleKey = key("Toggle Chat Visibility", -1);
	private final Option.Header hMention = header("Mentions");
	private final Option.Bool mentionSound = bool("Mention Sound", false).tip("Plays a sound when somebody writes your name.");
	private final Option.Choice mentionTone = choice("Mention Tone", 0, "Experience Orb", "Pling", "Bell", "Level Up", "Anvil");
	private final Option.Bool exactMatch = bool("Ping Exact Match", false).tip("Only the whole name with the same capital letters counts.");
	private final Option.Header hName = header("Your name in chat");
	private final Option.Choice nameColor = choice("Highlighted Name Color", 0, "Off", "Gold", "Yellow", "Green", "Aqua", "Red", "Pink", "White");
	private final Option.Bool nameBold = bool("Highlight Name Bold", false);
	private final Option.Bool nameItalic = bool("Highlight Name Italic", false);
	private final Option.Bool nameUnderline = bool("Highlight Name Underline", false);
	private final Option.Bool nameStrike = bool("Highlight Name Strikethrough", false);
	private final Option.Header hFilter = header("Filter");
	private final Option.Choice filterLevel = choice("Profanity Filter Level", 0, "Off", "Normal", "High", "Custom Only")
			.tip("Normal: slurs and the worst words. High: every swear word (pt / en / es). Custom Only: just your own list.");
	private final Option.Text customWords = text("Custom Filter Words", "", 200, "word1, word2, word3");
	private final Option.Bool hideFiltered = bool("Hide Filtered Messages", false).tip("Messages with a filtered word are not shown at all (instead of being starred out).");

	public static ChatMod instance;

	/** "[14:05] " in grey, or null when off. */
	public static String timestamp() {
		if (instance == null || !instance.enabled || !instance.timestamps.on()) {
			return null;
		}
		String p = switch (instance.timestampFormat.idx()) {
			case 1 -> "h:mm a";
			case 2 -> "HH:mm:ss";
			default -> "HH:mm";
		};
		return "[" + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern(p)) + "] ";
	}

	// ---------------------------------------------------------------- incoming messages
	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_@$!+]+");
	private static String lastPlain = "";
	private static int lastCount;
	private static long lastAt;

	private static boolean active() {
		return instance != null && instance.enabled;
	}

	private List<String> custom() {
		List<String> l = new ArrayList<>();
		for (String w : customWords.get().split("[,;\\n]")) {
			String t = w.strip().toLowerCase(Locale.ROOT);
			if (t.length() >= 2) {
				l.add(t);
			}
		}
		return l;
	}

	private boolean flagged(String word) {
		int lvl = filterLevel.idx();
		if (lvl == 0) {
			return false;
		}
		String low = word.toLowerCase(Locale.ROOT);
		for (String c : custom()) {
			if (low.contains(c)) {
				return true;
			}
		}
		if (lvl == 3) {
			return false;
		}
		return dev.sleepclient.social.NickFilter.isBadWord(word, lvl == 1);
	}

	/** True when this message has a filtered word and "Hide Filtered Messages" is on (the chat then never shows it). */
	public static boolean hide(Text t) {
		if (!active() || instance.filterLevel.idx() == 0 || !instance.hideFiltered.on() || t == null) {
			return false;
		}
		Matcher m = WORD.matcher(t.getString());
		while (m.find()) {
			if (instance.flagged(m.group())) {
				return true;
			}
		}
		return false;
	}

	/** Filter, name highlight, mention sound and stacked spam, in that order. {@code hud} is where the previous line is removed from. */
	public static Text process(Text in, ChatHud hud) {
		if (!active() || in == null) {
			return in;
		}
		try {
			ChatMod m = instance;
			Text t = in;
			String me = m.mc.getSession().getUsername();
			String plain = t.getString();
			if (me != null && !me.isEmpty() && m.mentionSound.on() && mentions(plain, me, m.exactMatch.on())) {
				m.ping();
			}
			boolean needFilter = m.filterLevel.idx() != 0;
			boolean needHighlight = me != null && !me.isEmpty() && (m.nameColor.idx() != 0 || m.nameBold.on() || m.nameItalic.on() || m.nameUnderline.on() || m.nameStrike.on());
			if (needFilter || needHighlight) {
				t = m.rewrite(t, needFilter, needHighlight ? me : null);
			}
			if (m.stackSpam.on() && hud != null) {
				String key = t.getString();
				long now = System.currentTimeMillis();
				if (!key.isBlank() && key.equals(lastPlain) && now - lastAt < 90_000L) {
					lastCount++;
					var acc = (dev.sleepclient.mixin.overlay.ChatHudAccessor) (Object) hud;
					var msgs = acc.plegumo$messages();
					if (!msgs.isEmpty()) {
						msgs.remove(0);
						acc.plegumo$refresh();
					}
					t = Text.empty().append(t).append(Text.literal(" (x" + lastCount + ")").formatted(Formatting.GRAY));
				} else {
					lastPlain = key;
					lastCount = 1;
				}
				lastAt = now;
			}
			return t;
		} catch (RuntimeException e) {
			return in;
		}
	}

	private static boolean mentions(String plain, String me, boolean exact) {
		// not your own message ("Name: hi", "<Name> hi", "[RANK] Name: hi")
		String own = plain.toLowerCase(Locale.ROOT);
		String mine = me.toLowerCase(Locale.ROOT);
		if (own.startsWith("<" + mine + ">") || own.contains(mine + ":") || own.contains(mine + " »") || own.contains(mine + " >")) {
			return false;
		}
		Matcher m = WORD.matcher(plain);
		while (m.find()) {
			String w = m.group();
			if (exact ? w.equals(me) : w.equalsIgnoreCase(me)) {
				return true;
			}
		}
		return false;
	}

	private void ping() {
		var tone = switch (mentionTone.idx()) {
			case 1 -> PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.2F);
			case 2 -> PositionedSoundInstance.ui(SoundEvents.BLOCK_BELL_USE, 1.0F, 0.8F);
			case 3 -> PositionedSoundInstance.ui(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 0.7F);
			case 4 -> PositionedSoundInstance.ui(SoundEvents.BLOCK_ANVIL_LAND, 1.0F, 0.4F);
			default -> PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 0.9F);
		};
		mc.getSoundManager().play(tone);
	}

	private Formatting colour() {
		return switch (nameColor.idx()) {
			case 1 -> Formatting.GOLD;
			case 2 -> Formatting.YELLOW;
			case 3 -> Formatting.GREEN;
			case 4 -> Formatting.AQUA;
			case 5 -> Formatting.RED;
			case 6 -> Formatting.LIGHT_PURPLE;
			case 7 -> Formatting.WHITE;
			default -> null;
		};
	}

	/** Rebuilds the text piece by piece (keeping every colour): swear words become stars, your name gets the highlight style. */
	private Text rewrite(Text t, boolean filter, String me) {
		MutableText out = Text.empty();
		Formatting col = colour();
		t.visit((style, str) -> {
			String s = str;
			if (filter) {
				Matcher m = WORD.matcher(s);
				StringBuilder b = new StringBuilder();
				int last = 0;
				while (m.find()) {
					b.append(s, last, m.start());
					String w = m.group();
					b.append(flagged(w) ? "*".repeat(w.length()) : w);
					last = m.end();
				}
				b.append(s.substring(last));
				s = b.toString();
			}
			if (me != null && s.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT))) {
				Style hi = style;
				if (col != null) {
					hi = hi.withFormatting(col);
				}
				hi = hi.withBold(nameBold.on() ? Boolean.TRUE : style.isBold()).withItalic(nameItalic.on() ? Boolean.TRUE : style.isItalic())
						.withUnderline(nameUnderline.on() ? Boolean.TRUE : style.isUnderlined())
						.withStrikethrough(nameStrike.on() ? Boolean.TRUE : style.isStrikethrough());
				String low = s.toLowerCase(Locale.ROOT), nm = me.toLowerCase(Locale.ROOT);
				int from = 0, at;
				while ((at = low.indexOf(nm, from)) >= 0) {
					if (at > from) {
						out.append(Text.literal(s.substring(from, at)).setStyle(style));
					}
					out.append(Text.literal(s.substring(at, at + nm.length())).setStyle(hi));
					from = at + nm.length();
				}
				if (from < s.length()) {
					out.append(Text.literal(s.substring(from)).setStyle(style));
				}
			} else {
				out.append(Text.literal(s).setStyle(style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	// ---------------------------------------------------------------- size and background
	private boolean applied;
	private double pw, pc, po, pb;
	private boolean keyWas;

	public ChatMod() {
		super("Chat", "chat", "Size, background, timestamps, stacked spam, mention sound, name highlight and a swear-word filter.", Category.HUD);
		instance = this;
		customWords.showIf(() -> filterLevel.idx() != 0);
		hideFiltered.showIf(() -> filterLevel.idx() != 0);
		mentionTone.showIf(mentionSound::on);
		exactMatch.showIf(mentionSound::on);
		timestampFormat.showIf(timestamps::on);
		backgroundOpacity.showIf(background::on);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	protected void onDisable() {
		if (applied) {
			mc.options.getChatWidth().setValue(pw);
			mc.options.getChatHeightUnfocused().setValue(pc);
			mc.options.getChatHeightFocused().setValue(po);
			mc.options.getTextBackgroundOpacity().setValue(pb);
			applied = false;
		}
	}

	@Override
	public void tick() {
		if (!applied) {
			pw = mc.options.getChatWidth().getValue();
			pc = mc.options.getChatHeightUnfocused().getValue();
			po = mc.options.getChatHeightFocused().getValue();
			pb = mc.options.getTextBackgroundOpacity().getValue();
			applied = true;
		}
		mc.options.getChatWidth().setValue((width.f() - 40.0) / 280.0);
		mc.options.getChatHeightUnfocused().setValue((closedHeight.f() - 20.0) / 160.0);
		mc.options.getChatHeightFocused().setValue((openHeight.f() - 20.0) / 160.0);
		mc.options.getTextBackgroundOpacity().setValue(background.on() ? backgroundOpacity.f() / 100.0 : 0.0);
		// the "hide chat" key (only in the game, not while typing)
		boolean down = toggleKey.code() >= 0 && mc.currentScreen == null && mc.player != null && dev.sleepclient.Keys.down(toggleKey.code());
		if (down && !keyWas) {
			var vis = mc.options.getChatVisibility();
			vis.setValue(vis.getValue() == ChatVisibility.HIDDEN ? ChatVisibility.FULL : ChatVisibility.HIDDEN);
		}
		keyWas = down;
	}
}
