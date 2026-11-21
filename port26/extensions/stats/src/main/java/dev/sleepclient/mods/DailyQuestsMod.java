package dev.sleepclient.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sleepclient.Gfx;
import dev.sleepclient.Keys;
import dev.sleepclient.fightcam.ClipToast;
import dev.sleepclient.gui.Theme;
import dev.sleepclient.option.Option;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Daily Quests: 3 new little goals every day (walk, mine, build, fight mobs, win fights, jump, play time) shown in a
 * HUD list with progress bars. Finish them for Sleep XP, levels and a daily streak. Counted on your computer only,
 * on any server or singleplayer.
 */
public class DailyQuestsMod extends HudMod {

	public static DailyQuestsMod instance;

	private static final String[] KIND = {"walk", "mine", "place", "mobs", "players", "jump", "minutes"};
	private static final String[] TITLE = {"Walk %d blocks", "Mine %d blocks", "Place %d blocks", "Defeat %d mobs", "Win %d player fights",
			"Jump %d times", "Play %d minutes"};
	private static final int[][] RANGE = {{800, 3000}, {64, 400}, {64, 400}, {5, 30}, {1, 5}, {100, 500}, {20, 90}};

	private final Option.Bool compact = bool("Compact", false);
	private final Option.Num boxW = num("Width", 140, 50, 440, 2, "");
	private final Option.Num gap = num("Gap", 1, 0, 6, 0.5F, "");
	private final Option.Num radius = num("Corner Radius", 2, 0, 10, 0.5F, "");
	private final Option.Col bg = colour("Background Colour", dev.sleepclient.gui.KeyTiles.BG);
	private final Option.Col textCol = colour("Text Colour", dev.sleepclient.gui.KeyTiles.TEXT);
	private final Option.Col fillCol = colour("Progress Colour", 0xFFFFFFFF);
	private final Option.Bool toasts = bool("Completion Card", true);

	private String day = "";
	private final int[] kind = new int[3], target = new int[3];
	private final double[] progress = new double[3];
	private final boolean[] done = new boolean[3];
	private int streak, xp;
	private String streakDay = "";
	private boolean loaded, dirty;
	private long lastSave;

	// tracking
	private double lastX = Double.NaN, lastZ;
	private boolean wasOnGround = true;
	private BlockPos breaking;
	private BlockState breakingState;
	private int breakingTicks;
	private int lastSlot = -1, lastCount = -1;
	private boolean wasRmb;
	private final Map<Entity, Long> hits = new HashMap<>();
	private int tickCounter;

	public DailyQuestsMod() {
		super("Daily Quests", "daily_quests", "3 new goals every day (walk, mine, build, fight, jump...) for XP, levels and a streak.", 0.01F, 0.35F);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return false; // only when you turn it on
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-quests.json");
	}

	// ------------------------------------------------------------------ save / load
	private void load() {
		loaded = true;
		try {
			if (Files.exists(file())) {
				JsonObject o = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
				day = o.has("day") ? o.get("day").getAsString() : "";
				streak = o.has("streak") ? o.get("streak").getAsInt() : 0;
				xp = o.has("xp") ? o.get("xp").getAsInt() : 0;
				streakDay = o.has("streakDay") ? o.get("streakDay").getAsString() : "";
				JsonArray q = o.has("quests") ? o.getAsJsonArray("quests") : new JsonArray();
				for (int i = 0; i < 3 && i < q.size(); i++) {
					JsonObject e = q.get(i).getAsJsonObject();
					kind[i] = e.get("kind").getAsInt();
					target[i] = e.get("target").getAsInt();
					progress[i] = e.get("progress").getAsDouble();
					done[i] = e.get("done").getAsBoolean();
				}
			}
		} catch (Exception e) {
			System.err.println("[Sleep] quests: " + e);
		}
		rollIfNewDay();
	}

	private void save() {
		try {
			JsonObject o = new JsonObject();
			o.addProperty("day", day);
			o.addProperty("streak", streak);
			o.addProperty("xp", xp);
			o.addProperty("streakDay", streakDay);
			JsonArray q = new JsonArray();
			for (int i = 0; i < 3; i++) {
				JsonObject e = new JsonObject();
				e.addProperty("kind", kind[i]);
				e.addProperty("target", target[i]);
				e.addProperty("progress", progress[i]);
				e.addProperty("done", done[i]);
				q.add(e);
			}
			o.add("quests", q);
			Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
			dirty = false;
		} catch (Exception e) {
			System.err.println("[Sleep] quests save: " + e);
		}
	}

	private void rollIfNewDay() {
		String today = LocalDate.now().toString();
		if (today.equals(day)) {
			return;
		}
		day = today;
		Random r = new Random(today.hashCode() * 31L + 7);
		boolean[] used = new boolean[KIND.length];
		for (int i = 0; i < 3; i++) {
			int k;
			do {
				k = r.nextInt(KIND.length);
			} while (used[k]);
			used[k] = true;
			kind[i] = k;
			int lo = RANGE[k][0], hi = RANGE[k][1];
			int step = hi >= 100 ? 50 : hi >= 20 ? 5 : 1;
			target[i] = Math.max(lo, (lo + r.nextInt(hi - lo + 1)) / step * step);
			progress[i] = 0;
			done[i] = false;
		}
		// streak breaks if yesterday was not finished
		if (!streakDay.isEmpty() && !streakDay.equals(LocalDate.now().minusDays(1).toString()) && !streakDay.equals(today)) {
			streak = 0;
		}
		dirty = true;
	}

	public int level() {
		return 1 + (int) Math.floor(Math.sqrt(xp / 50.0));
	}

	// ------------------------------------------------------------------ progress
	private void add(int k, double amount) {
		for (int i = 0; i < 3; i++) {
			if (kind[i] == k && !done[i]) {
				progress[i] += amount;
				dirty = true;
				if (progress[i] >= target[i]) {
					progress[i] = target[i];
					done[i] = true;
					int gain = 40 + target[i] / Math.max(1, RANGE[k][1] / 60);
					xp += gain;
					if (toasts.on()) {
						ClipToast.show("Quest complete!", String.format(dev.sleepclient.Lang.t(TITLE[k]), target[i]) + "  +" + gain + " XP", null, false);
					}
					if (done[0] && done[1] && done[2]) {
						String today = LocalDate.now().toString();
						if (!today.equals(streakDay)) {
							streak++;
							streakDay = today;
							xp += 100;
							ClipToast.show("All quests done!", dev.sleepclient.Lang.tr("Streak: " + streak + " day" + (streak == 1 ? "" : "s") + "  +100 XP  ·  Level " + level(),
									"Sequência: " + streak + " dia" + (streak == 1 ? "" : "s") + "  +100 XP  ·  Nível " + level()), null,
									false);
						}
					}
					save();
				}
			}
		}
	}

	public void onAttack(Entity e) {
		if (e instanceof LivingEntity) {
			hits.put(e, System.currentTimeMillis());
		}
	}

	@Override
	public void tick() {
		if (!loaded) {
			load();
		}
		var p = mc.player;
		if (p == null || mc.level == null) {
			return;
		}
		tickCounter++;
		if (tickCounter % 200 == 0) {
			rollIfNewDay();
		}
		// walk: horizontal distance on foot
		if (!Double.isNaN(lastX) && !p.isPassenger() && !p.getAbilities().flying) {
			double d = Math.hypot(p.getX() - lastX, p.getZ() - lastZ);
			if (d < 2) {
				add(0, d);
			}
		}
		lastX = p.getX();
		lastZ = p.getZ();
		// jump
		boolean ground = p.onGround();
		if (wasOnGround && !ground && p.getDeltaMovement().y > 0.3) {
			add(5, 1);
		}
		wasOnGround = ground;
		// play time
		if (tickCounter % 1200 == 0) {
			add(6, 1);
		}
		// mine: the block you were hitting turned into air
		boolean lmb = dev.sleepclient.compat.Mc.screen(mc) == null && Keys.mouseDown(0);
		HitResult hr = mc.hitResult;
		if (lmb && hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
			BlockPos bp = bh.getBlockPos();
			if (!bp.equals(breaking)) {
				breaking = bp.immutable();
				breakingState = mc.level.getBlockState(bp);
			}
			breakingTicks = 0;
		} else if (breaking != null && ++breakingTicks > 10) {
			breaking = null;
		}
		if (breaking != null && breakingState != null && !breakingState.isAir() && mc.level.getBlockState(breaking).isAir()) {
			add(1, 1);
			breaking = null;
		}
		// place: the block in your hand went down while right-clicking
		boolean rmb = dev.sleepclient.compat.Mc.screen(mc) == null && Keys.mouseDown(1);
		ItemStack hand = p.getMainHandItem();
		int slot = p.getInventory().getSelectedSlot();
		if (hand.getItem() instanceof BlockItem) {
			int cnt = hand.getCount();
			if ((rmb || wasRmb) && slot == lastSlot && lastCount > cnt && lastCount - cnt <= 4) {
				add(2, lastCount - cnt);
			}
			lastCount = cnt;
		} else {
			lastCount = hand.isEmpty() && (rmb || wasRmb) && slot == lastSlot && lastCount == 1 ? 0 : -1;
			if (lastCount == 0) {
				add(2, 1);
				lastCount = -1;
			}
		}
		lastSlot = slot;
		wasRmb = rmb;
		// fights: something you hit died
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Entity, Long>> it = hits.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Entity, Long> en = it.next();
			Entity e = en.getKey();
			if (now - en.getValue() > 5000) {
				it.remove();
				continue;
			}
			if (e instanceof LivingEntity le && (le.isDeadOrDying() || le.getHealth() <= 0F)) {
				it.remove();
				if (e instanceof Player) {
					add(4, 1);
				} else if (e instanceof Monster) {
					add(3, 1);
				}
			}
		}
		if (dirty && now - lastSave > 15000) {
			lastSave = now;
			save();
		}
	}

	// ------------------------------------------------------------------ HUD (Keystrokes look: one tile per quest)
	private float rowH() {
		return compact.on() ? 14F : 18F;
	}

	@Override
	public int width() {
		return Math.round(boxW.f());
	}

	@Override
	public int height() {
		return Math.round(rowH() * 4 + gap.f() * 3);
	}

	@Override
	protected void renderHud(GuiGraphicsExtractor c, boolean edit) {
		if (!loaded) {
			load();
		}
		float w = boxW.f(), rh = rowH(), g = gap.f();
		String lv = dev.sleepclient.Lang.tr("Lv ", "Nv ") + level() + (streak > 0 ? "  ★" + streak : "");
		dev.sleepclient.gui.KeyTiles.bar(c, 0, 0, w, rh, radius.f(), bg.c(), dev.sleepclient.Lang.t("Daily Quests"), lv, -1F, textCol.c(), fillCol.c());
		for (int i = 0; i < 3; i++) {
			int k = Math.max(0, Math.min(KIND.length - 1, kind[i]));
			String t = String.format(dev.sleepclient.Lang.t(TITLE[k]), target[i]);
			float pr = target[i] <= 0 ? 0 : (float) Math.min(1, progress[i] / target[i]);
			String left = (done[i] ? "✔ " : "") + t;
			int maxLeft = Math.round(w - 34);
			while (left.length() > 3 && Gfx.twRaw(left) > maxLeft) {
				left = left.substring(0, left.length() - 2);
			}
			dev.sleepclient.gui.KeyTiles.bar(c, 0, (i + 1) * (rh + g), w, rh, radius.f(), bg.c(), left, Math.round(pr * 100) + "%",
					compact.on() ? -1F : pr, done[i] ? 0xFF9BF0AE : textCol.c(), done[i] ? 0xFF9BF0AE : fillCol.c());
		}
	}
}
