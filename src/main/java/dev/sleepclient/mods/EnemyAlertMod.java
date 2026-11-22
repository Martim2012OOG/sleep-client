package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.option.Option;
import dev.sleepclient.social.Party;
import dev.sleepclient.social.Social;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BannerItem;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.MathHelper;

/**
 * Enemy Alert: works out who is on your team and who is not (scoreboard team and its colour, the colour of leather armour, a banner on
 * the head or in the hand, your party and friends) and warns you when an enemy comes close: a panel with the name, distance and an
 * arrow, a sound when a new one shows up, and a stronger warning when they are running towards you. Made for Bedwars, PvPHQ and the like.
 */
public class EnemyAlertMod extends HudMod {

	private final Option.Num radius = num("Alert Radius", 24, 6, 64, 1, " blocks");
	private final Option.Bool unknown = bool("Warn About Unknown Players", true).tip("Players the client can not tell apart (no team, no colour): treated as enemies. Turn off to warn only about players it is sure about.");
	private final Option.Bool friends = bool("Friends And Party Are Allies", true);
	private final Option.Bool approachOnly = bool("Only When They Approach", false);
	private final Option.Bool showAllies = bool("Show Teammates Too", false);
	private final Option.Bool arrows = bool("Direction Arrows", true);
	private final Option.Bool sound = bool("Warning Sound", true);
	private final Option.Bool flash = bool("Flashing Border", true);

	/** side: 1 ally, -1 enemy, 0 not sure */
	private record Entry(String name, double dist, double dx, double dz, int side, boolean approaching, UUID id) {
	}

	private volatile List<Entry> list = List.of();
	private volatile int myColour = -1;
	private final Map<UUID, Double> lastDist = new HashMap<>();
	private final Set<UUID> known = new HashSet<>();
	private long lastBeep;

	public EnemyAlertMod() {
		super("Enemy Alert", "enemy_alert", "Tells teammates from enemies (team, armour colour, banner) and warns you when an enemy gets close.", 0.5F, 0.2F);
	}

	@Override
	public boolean enabledByDefault() {
		return false;
	}

	@Override
	public boolean visible() {
		return !list.isEmpty();
	}

	@Override
	public int width() {
		return 160;
	}

	@Override
	public int height() {
		return 14 + Math.max(1, Math.min(5, list.isEmpty() ? 3 : list.size())) * 12;
	}

	// ------------------------------------------------------------------ who is on my team
	private static int rgb(int c) {
		return c & 0xFFFFFF;
	}

	private static double colourDistance(int a, int b) {
		int dr = ((a >> 16) & 255) - ((b >> 16) & 255), dg = ((a >> 8) & 255) - ((b >> 8) & 255), db = (a & 255) - (b & 255);
		return Math.sqrt(dr * dr + dg * dg + db * db);
	}

	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** The dye colour of the leather armour a player wears (the most common one), or -1. */
	private static int armourColour(PlayerEntity p) {
		int best = -1, n = 0;
		for (EquipmentSlot s : ARMOUR) {
			ItemStack st = p.getEquippedStack(s);
			if (st.isEmpty()) {
				continue;
			}
			int c = DyedColorComponent.getColor(st, -1);
			if (c == -1 || c == 0) {
				continue;
			}
			if (best == -1) {
				best = rgb(c);
			}
			n++;
		}
		return n == 0 ? -1 : best;
	}

	/** The colour of a banner on the head or in either hand, or null. */
	private static DyeColor banner(PlayerEntity p) {
		for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			ItemStack st = p.getEquippedStack(s);
			if (!st.isEmpty() && st.getItem() instanceof BannerItem b) {
				return b.getColor();
			}
		}
		return null;
	}

	private boolean allyByParty(PlayerEntity p) {
		if (!friends.on()) {
			return false;
		}
		String n = p.getName().getString();
		if (Party.active()) {
			for (Party.Member m : Party.MEMBERS) {
				if (m.nick.equalsIgnoreCase(n)) {
					return true;
				}
			}
		}
		for (Social.User u : Social.friends) {
			if (u.mcUsername != null && u.mcUsername.equalsIgnoreCase(n)) {
				return true;
			}
		}
		return false;
	}

	/** Positive = on my team, negative = enemy, 0 = not sure. */
	private int side(PlayerEntity me, PlayerEntity p, boolean teamUseful, int myArmour, DyeColor myBanner) {
		if (allyByParty(p)) {
			return 1;
		}
		int score = 0;
		Team mt = (Team) me.getScoreboardTeam(), pt = (Team) p.getScoreboardTeam();
		if (teamUseful && mt != null && pt != null) {
			if (mt == pt || mt.getName().equals(pt.getName())) {
				score += 2;
			} else {
				score -= 2;
				if (mt.getColor() == pt.getColor() && mt.getColor() != net.minecraft.util.Formatting.RESET) {
					score += 3; // two teams with the same colour (one team per player): same colour = same side
				}
			}
		}
		int pa = armourColour(p);
		if (myArmour != -1 && pa != -1) {
			score += colourDistance(myArmour, pa) < 70 ? 3 : -3;
		}
		DyeColor pb = banner(p);
		if (myBanner != null && pb != null) {
			score += myBanner == pb ? 3 : -3;
		}
		return Integer.compare(score, 0);
	}

	// ------------------------------------------------------------------ scanning
	@Override
	public void tick() {
		if (mc.player == null || mc.world == null) {
			list = List.of();
			return;
		}
		if (mc.player.age % 4 != 0) {
			return;
		}
		PlayerEntity me = mc.player;
		double r = radius.f(), r2 = r * r;
		List<? extends PlayerEntity> players = mc.world.getPlayers();
		// is the scoreboard team telling anything? (if nearly everybody is in my team, the server only uses it to hide names)
		Team mt = (Team) me.getScoreboardTeam();
		int sameTeam = 0, others = 0;
		for (PlayerEntity p : players) {
			if (p == me) {
				continue;
			}
			others++;
			Team pt = (Team) p.getScoreboardTeam();
			if (mt != null && pt != null && mt.getName().equals(pt.getName())) {
				sameTeam++;
			}
		}
		boolean teamUseful = mt != null && !(others >= 4 && sameTeam >= others * 0.75);
		int myArmour = armourColour(me);
		DyeColor myBanner = banner(me);
		myColour = myArmour;
		List<Entry> out = new ArrayList<>();
		Set<UUID> now = new HashSet<>();
		for (PlayerEntity p : players) {
			if (p == me || p.isSpectator() || p.isRemoved() || p.isInvisible() && false) {
				continue;
			}
			double dx = p.getX() - me.getX(), dy = p.getY() - me.getY(), dz = p.getZ() - me.getZ();
			double d2 = dx * dx + dy * dy + dz * dz;
			if (d2 > r2) {
				continue;
			}
			double d = Math.sqrt(d2);
			int s = side(me, p, teamUseful, myArmour, myBanner);
			Double prev = lastDist.get(p.getUuid());
			boolean approaching = prev != null && d < prev - 0.12;
			now.add(p.getUuid());
			lastDist.put(p.getUuid(), d);
			if (s > 0 && !showAllies.on()) {
				continue;
			}
			if (s == 0 && !unknown.on()) {
				continue;
			}
			if (approachOnly.on() && s <= 0 && !approaching) {
				continue;
			}
			out.add(new Entry(p.getName().getString(), d, dx, dz, s, approaching, p.getUuid()));
		}
		lastDist.keySet().retainAll(now);
		out.sort(Comparator.<Entry>comparingInt(e -> e.side() > 0 ? 1 : 0).thenComparingDouble(Entry::dist));
		if (out.size() > 5) {
			out = new ArrayList<>(out.subList(0, 5));
		}
		long t = System.currentTimeMillis();
		Set<UUID> keys = new HashSet<>();
		boolean fresh = false;
		for (Entry e : out) {
			keys.add(e.id());
			if (e.side() <= 0 && !known.contains(e.id())) {
				fresh = true;
			}
		}
		known.clear();
		known.addAll(keys);
		if (fresh && sound.on() && t - lastBeep > 1500L) {
			lastBeep = t;
			try {
				me.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.8F, 0.5F);
			} catch (RuntimeException ignored) {
				// no sound
			}
		}
		list = out;
	}

	// ------------------------------------------------------------------ HUD
	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		List<Entry> l = list;
		if (l.isEmpty() && edit) {
			l = List.of(new Entry("Enemy", 14, 3, 2, -1, true, UUID.randomUUID()), new Entry("Friend", 9, -3, 1, 1, false, UUID.randomUUID()));
		}
		int rows = Math.max(1, Math.min(5, l.size()));
		int w = width(), h = 14 + rows * 12;
		boolean danger = false;
		for (Entry e : l) {
			danger |= e.side() <= 0 && e.approaching();
		}
		int border = danger ? 0xFFE5453A : 0xFFF2A33A;
		if (flash.on() && danger && (System.currentTimeMillis() / 300) % 2 == 0) {
			border = 0xFFFFFFFF;
		}
		Gfx.rrect(c, 0, 0, w, h, 5, border);
		Gfx.rrect(c, 1, 1, w - 2, h - 2, 4, 0xE61A1214);
		c.drawText(mc.textRenderer, Lang.tr("ENEMIES", "INIMIGOS"), 6, 3, border, true);
		if (myColour != -1) { // your side's colour
			String t = Lang.tr("your team", "a tua equipa");
			int tw = mc.textRenderer.getWidth(t);
			c.fill(w - tw - 17, 4, w - tw - 7, 12, 0xFF000000 | myColour);
			c.drawText(mc.textRenderer, t, w - tw - 4, 3, 0xFFB9B2B4, false);
		}
		for (int i = 0; i < rows; i++) {
			Entry e = l.get(i);
			int y = 15 + i * 12;
			int col = e.side() > 0 ? 0xFF7CFF9B : e.side() < 0 ? (e.approaching() ? 0xFFFF6B5E : 0xFFFFA99E) : 0xFFFFE08A;
			if (arrows.on() && mc.player != null) {
				float target = (float) (MathHelper.atan2(-e.dx(), e.dz()) * 57.29578);
				float rel = MathHelper.wrapDegrees(target - mc.player.getYaw());
				arrow(c, 11, y + 4, (float) Math.toRadians(rel), col);
			} else {
				c.fill(9, y + 3, 13, y + 7, col);
			}
			String tag = e.side() > 0 ? "" : e.approaching() ? " >>" : "";
			String t = mc.textRenderer.trimToWidth(e.name() + tag, w - 52);
			c.drawText(mc.textRenderer, t, 20, y, col, false);
			String d = Math.round(e.dist()) + "m";
			c.drawText(mc.textRenderer, d, w - 4 - mc.textRenderer.getWidth(d), y, 0xFFB9B2B4, false);
		}
	}

	private static void arrow(DrawContext c, int cx, int cy, float angle, int col) {
		var m = c.getMatrices();
		m.pushMatrix();
		m.translate(cx, cy);
		m.rotate(angle);
		for (int r = 0; r < 5; r++) {
			c.fill(-r / 2 - (r > 0 ? 1 : 0), r - 3, r / 2 + (r > 0 ? 2 : 1), r - 2, col);
		}
		c.fill(0, 2, 1, 4, col);
		m.popMatrix();
	}
}
