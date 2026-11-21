package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * Nametag Health: the player's life under their name, like "13 ❤" (the way servers like MCTiers show it), and the
 * same above mobs near you (replaces the old Health Bars mod). It uses the health the server sends; some servers hide
 * players' health (then everyone shows full life).
 */
public class NametagHealthMod extends Mod {

	public static NametagHealthMod instance;

	private final Option.Choice style = choice("Style", 0, "13 ❤", "❤❤❤❤❤", "13 / 20 ❤", "65%");
	private final Option.Bool halfHearts = bool("Count In Hearts (6.5)", false);
	private final Option.Bool absorption = bool("Show Absorption", true);
	private final Option.Bool colourByHealth = bool("Colour By Health", false);
	private final Option.Bool keepServer = bool("Keep The Server's Own Line", true);
	private final Option.Header mobsHeader = header("Mobs");
	private final Option.Bool mobs = bool("Show On Mobs Too", true);
	private final Option.Bool onlyHurt = bool("Only Hurt Mobs", false);
	private final Option.Num mobRange = num("Mob Range", 16, 4, 48, 2, " blocks");

	public NametagHealthMod() {
		super("Nametag Health", "nametag_health", "Hearts under players' names and above mobs (like 13 ❤).", Category.VISUAL);
		instance = this;
		onlyHurt.showIf(mobs::on);
		mobRange.showIf(mobs::on);
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}

	/** True when the server already shows something under the name and we should leave it. */
	public static boolean keepServerLine() {
		return instance.keepServer.on();
	}

	/** Should this mob get a health label? (players have their own line under the name) */
	public static boolean showOnMob(LivingEntity e, double squaredDistance) {
		NametagHealthMod m = instance;
		if (m == null || !m.enabled || !m.mobs.on() || e.isInvisible() || e instanceof net.minecraft.entity.decoration.ArmorStandEntity) {
			return false;
		}
		if (m.onlyHurt.on() && e.getHealth() >= e.getMaxHealth()) {
			return false;
		}
		float r = m.mobRange.f();
		return squaredDistance <= r * r;
	}

	private record Cached(float hp, float max, float abs, long until, Text text) {
	}

	private static final java.util.Map<Integer, Cached> LINES = new java.util.HashMap<>();

	/** The "13 ❤" text, rebuilt only when the health changed (or every 300 ms for setting changes), not every frame. */
	public static Text line(LivingEntity e) {
		long now = System.currentTimeMillis();
		Cached c = LINES.get(e.getId());
		float hp = e.getHealth(), max = e.getMaxHealth(), abs = e.getAbsorptionAmount();
		if (c != null && c.until > now && c.hp == hp && c.max == max && c.abs == abs) {
			return c.text;
		}
		Text t = build(e);
		if (LINES.size() > 256) {
			LINES.clear();
		}
		LINES.put(e.getId(), new Cached(hp, max, abs, now + 300L, t));
		return t;
	}

	private static Text build(LivingEntity e) {
		NametagHealthMod m = instance;
		float hp = Math.max(0F, e.getHealth()), max = Math.max(1F, e.getMaxHealth()), abs = Math.max(0F, e.getAbsorptionAmount());
		int numCol = 0xFFFFFF;
		if (m.colourByHealth.on()) {
			float k = hp / max;
			numCol = k > 0.6F ? 0x55FF55 : k > 0.3F ? 0xFFFF55 : 0xFF5555;
		}
		final int nc = numCol;
		MutableText out = Text.empty();
		switch (m.style.idx()) {
			case 1 -> {
				int full = Math.round(hp / max * 10F);
				out.append(Text.literal("❤".repeat(Math.max(0, full))).styled(s -> s.withColor(0xFF5555)));
				out.append(Text.literal("❤".repeat(Math.max(0, 10 - full))).styled(s -> s.withColor(0x555555)));
			}
			case 2 -> {
				out.append(Text.literal(num(hp, m) + " / " + num(max, m) + " ").styled(s -> s.withColor(nc)));
				out.append(Text.literal("❤").styled(s -> s.withColor(0xFF5555)));
			}
			case 3 -> out.append(Text.literal(Math.round(hp / max * 100F) + "%").styled(s -> s.withColor(nc)));
			default -> {
				out.append(Text.literal(num(hp, m) + " ").styled(s -> s.withColor(nc)));
				out.append(Text.literal("❤").styled(s -> s.withColor(0xFF5555)));
			}
		}
		if (m.absorption.on() && abs > 0) {
			out.append(Text.literal(" +" + num(abs, m)).styled(s -> s.withColor(0xFFCC33)));
		}
		return out;
	}

	private static String num(float v, NametagHealthMod m) {
		if (m.halfHearts.on()) {
			v = Math.round(v) / 2F;
			return v == Math.floor(v) ? Integer.toString((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
		}
		return Integer.toString((int) Math.ceil(v));
	}
}
