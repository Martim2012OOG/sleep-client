package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.Keys;
import dev.sleepclient.gui.Theme;
import dev.sleepclient.option.Option;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/**
 * Fight Recap: when a fight ends, a card shows how it went - win / loss, opponent, length, hits, best combo, accuracy,
 * damage taken, and the pearls, golden apples, potions and totems you used. Move it in the HUD editor.
 */
public class FightRecapMod extends HudMod {

	public static FightRecapMod instance;

	private final Option.Num showFor = num("Show For", 7, 3, 20, 1, " s");
	private final Option.Num endAfter = num("Fight Ends After", 6, 3, 15, 1, " s idle");
	private final Option.Bool chat = bool("Also In Chat", false);
	private final Option.Col accentC = colour("Accent", 0xFF7DD3FC);

	// running fight
	private boolean fighting;
	private long startMs, lastActionMs;
	private Entity opponent;
	private String opponentName = "";
	private int hits, combo, bestCombo, swings;
	private float damageTaken, lastHealth = -1;
	private final int[] startCounts = new int[4];
	private boolean won, lost;
	private boolean prevMouse;

	// finished fight shown on the card
	private long shownAt;
	private String rName = "", rResult = "";
	private int rHits, rCombo, rAcc, rSecs;
	private float rDamage;
	private final int[] rUsed = new int[4];

	private static final String[] USED_NAMES = {"Pearls", "Gapples", "Potions", "Totems"};

	public FightRecapMod() {
		super("Fight Recap", "fight_recap", "After every fight: win / loss, hits, best combo, accuracy, damage taken and what you used.", 0.5F, 0.25F);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	private int count(Item... items) {
		var inv = mc.player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.size(); i++) {
			var st = inv.getStack(i);
			for (Item it : items) {
				if (st.isOf(it)) {
					n += st.getCount();
				}
			}
		}
		return n;
	}

	private int[] counts() {
		return new int[] {count(Items.ENDER_PEARL), count(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE), count(Items.SPLASH_POTION, Items.POTION),
				count(Items.TOTEM_OF_UNDYING)};
	}

	public void onAttack(Entity e) {
		if (!(e instanceof LivingEntity) || mc.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!fighting) {
			begin(now);
		}
		if (e instanceof PlayerEntity && (opponent == null || opponent != e)) {
			opponent = e;
			opponentName = e.getName().getString();
		} else if (opponent == null) {
			opponent = e;
			opponentName = e.getName().getString();
		}
		hits++;
		combo++;
		bestCombo = Math.max(bestCombo, combo);
		lastActionMs = now;
	}

	private void begin(long now) {
		fighting = true;
		startMs = now;
		hits = combo = bestCombo = swings = 0;
		damageTaken = 0;
		won = lost = false;
		opponent = null;
		opponentName = "";
		int[] c = counts();
		System.arraycopy(c, 0, startCounts, 0, 4);
	}

	@Override
	public void frame() {
		// count swings (left clicks) for accuracy
		boolean down = mc.currentScreen == null && Keys.mouseDown(0);
		if (down && !prevMouse && fighting) {
			swings++;
		}
		prevMouse = down;
	}

	@Override
	public void tick() {
		var p = mc.player;
		if (p == null) {
			return;
		}
		long now = System.currentTimeMillis();
		float hp = p.getHealth();
		if (lastHealth >= 0 && hp < lastHealth - 0.01F) {
			// took damage: that starts (or continues) a fight and breaks your combo
			if (!fighting) {
				begin(now);
			}
			damageTaken += lastHealth - hp;
			combo = 0;
			lastActionMs = now;
		}
		lastHealth = hp;
		if (!fighting) {
			return;
		}
		if (p.isDead()) {
			lost = true;
			finish(now);
			return;
		}
		if (opponent instanceof LivingEntity le && (le.isDead() || le.getHealth() <= 0F)) {
			won = true;
			finish(now);
			return;
		}
		if (now - lastActionMs > endAfter.f() * 1000) {
			finish(now);
		}
	}

	private void finish(long now) {
		fighting = false;
		if (hits == 0 && damageTaken < 1) {
			return; // not a real fight
		}
		rName = opponentName.isEmpty() ? "Unknown" : opponentName;
		rResult = won ? "VICTORY" : lost ? "DEFEAT" : "FIGHT OVER";
		rHits = hits;
		rCombo = bestCombo;
		dev.sleepclient.stats.KuboStats.max("bestCombo", bestCombo);
		rAcc = swings <= 0 ? 100 : Math.min(100, Math.round(hits * 100F / Math.max(hits, swings)));
		rSecs = (int) ((lastActionMs - startMs) / 1000);
		rDamage = damageTaken;
		int[] c = mc.player == null ? startCounts : counts();
		for (int i = 0; i < 4; i++) {
			rUsed[i] = Math.max(0, startCounts[i] - c[i]);
		}
		shownAt = now;
		if (chat.on() && mc.player != null) {
			mc.player.sendMessage(Text.literal("§b[Fight] §f" + rResult + " vs " + rName + " §7| " + rHits + " hits, combo " + rCombo + ", "
					+ rAcc + "% acc, " + String.format("%.1f", rDamage / 2F) + "❤ taken"), false);
		}
	}

	@Override
	public int width() {
		return 200;
	}

	@Override
	public int height() {
		return 82;
	}

	@Override
	public boolean visible() {
		return shownAt > 0 && System.currentTimeMillis() - shownAt < showFor.f() * 1000;
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		long age = System.currentTimeMillis() - shownAt;
		float k = edit ? 1F : Math.min(1F, Math.min(age / 250F, (showFor.f() * 1000 - age) / 400F));
		k = Math.max(0F, k);
		boolean demo = edit && shownAt == 0;
		String name = demo ? "Opponent" : rName, result = demo ? "VICTORY" : rResult;
		int w = width(), h = height();
		var m = c.getMatrices();
		m.pushMatrix();
		m.translate(0, (1 - k) * -10);
		int a = Math.round(230 * k);
		Gfx.rrectFree(c, 0, 0, w, h, 8, Gfx.withAlpha(0x12141A, a));
		int ac = result.equals("DEFEAT") ? 0xFFE5484D : result.equals("VICTORY") ? 0xFF4ADE80 : accentC.c();
		Gfx.rrectFree(c, 0, 0, 4, h, 2, Gfx.withAlpha(ac, Math.round(255 * k)));
		int ta = Math.round(255 * k);
		if (ta > 8) {
			c.drawText(mc.textRenderer, dev.sleepclient.Lang.t(result), 12, 8, Gfx.withAlpha(ac, ta), true);
			String vs = "vs " + name;
			c.drawText(mc.textRenderer, vs, w - 10 - mc.textRenderer.getWidth(vs), 8, Gfx.withAlpha(0xFFFFFF, ta), false);
			int secs = demo ? 23 : rSecs;
			c.drawText(mc.textRenderer, secs / 60 + ":" + String.format("%02d", secs % 60), 12, 20, Gfx.withAlpha(0x9AA0AE, ta), false);
			// stat tiles
			String[] labels = {dev.sleepclient.Lang.t("Hits"), dev.sleepclient.Lang.t("Best combo"), dev.sleepclient.Lang.t("Accuracy"), dev.sleepclient.Lang.t("Taken")};
			String[] vals = demo ? new String[] {"18", "6", "72%", "3.5❤"}
					: new String[] {Integer.toString(rHits), Integer.toString(rCombo), rAcc + "%", String.format("%.1f❤", rDamage / 2F)};
			int tw = (w - 24 - 9) / 4;
			for (int i = 0; i < 4; i++) {
				int tx = 12 + i * (tw + 3);
				Gfx.rrectFree(c, tx, 32, tw, 26, 4, Gfx.withAlpha(0x1E222C, a));
				c.drawText(mc.textRenderer, vals[i], tx + tw / 2 - mc.textRenderer.getWidth(vals[i]) / 2, 36, Gfx.withAlpha(0xFFFFFF, ta), false);
				String l = labels[i];
				float sc = 0.7F;
				m.pushMatrix();
				m.translate(tx + tw / 2F - mc.textRenderer.getWidth(l) * sc / 2F, 48);
				m.scale(sc, sc);
				c.drawText(mc.textRenderer, l, 0, 0, Gfx.withAlpha(0x9AA0AE, ta), false);
				m.popMatrix();
			}
			StringBuilder used = new StringBuilder();
			for (int i = 0; i < 4; i++) {
				int u = demo ? new int[] {3, 2, 1, 0}[i] : rUsed[i];
				if (u > 0) {
					if (used.length() > 0) {
						used.append("  ");
					}
					used.append(u).append(' ').append(dev.sleepclient.Lang.t(USED_NAMES[i]));
				}
			}
			String ul = used.length() == 0 ? dev.sleepclient.Lang.t("Nothing used") : dev.sleepclient.Lang.tr("Used: ", "Usado: ") + used;
			c.drawText(mc.textRenderer, ul, 12, 66, Gfx.withAlpha(Theme.accent(), ta), false);
		}
		m.popMatrix();
	}
}
