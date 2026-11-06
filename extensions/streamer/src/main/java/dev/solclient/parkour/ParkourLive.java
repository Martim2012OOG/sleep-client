package dev.solclient.parkour;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.mods.TikTokLiveMod;
import dev.solclient.tiktok.TikEvent;
import dev.solclient.tiktok.TikGifts;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Parkour Live: TikTok gifts play the Parkour Paradise 3 map with you.
 *
 * The map's checkpoint datapack keeps your level in the scoreboard "pk_level" and teleports with pk:to_checkpoint, so
 * this only works in single player (the client runs the commands on its own built-in server) and only in a world that
 * has that scoreboard. On screen: the title, LEVEL X / 100 (instead of the map's boss bar, which is hidden), the gift
 * panel on the right, a sweep when you reach a checkpoint, and the 60 s RESET timer that a SAVE gift stops.
 */
public final class ParkourLive {

	/** What a gift does, in the order of the panel. */
	public enum Act {
		UP1("UP 1 LVL"), DOWN1("DOWN 1 LVL"), UP10("UP 10 LVL"), DOWN10("DOWN 10 LVL"), RESET("RESET"), SAVE("SAVE"), PUNCH("PUNCH"), JAIL("JAIL");

		public final String label;

		Act(String label) {
			this.label = label;
		}
	}

	private static final int MAX = 100;

	// ---- synced with the map (written on the server thread, read on the client thread)
	private static volatile boolean inMap;
	private static volatile int level = -1;
	private static boolean wasInMap;
	private static int lastLevel = -1;
	private static int ticks;

	// ---- live state (client thread)
	private static final int[] COUNT = new int[Act.values().length];
	private static final Map<String, Integer> STREAK = new HashMap<>();
	private static long resetEnd;
	private static int lastBeep;
	private static String resetBy = "";
	private static int punches;
	/** When the jail opens (ms), 0 = free. */
	private static long jailEnd;
	/** Blocks of the cage that we placed (only where there was air), to take away again. Server thread only. */
	private static final java.util.List<net.minecraft.util.math.BlockPos> CAGE = new java.util.ArrayList<>();
	private static net.minecraft.registry.RegistryKey<net.minecraft.world.World> cageWorld;
	private static int punchCooldown;
	/** A level change asked by a gift: its text replaces "CHECKPOINT!" on the sweep that follows. */
	private static String giftNote;
	private static long giftNoteAt;

	// ---- sweeps
	private static String sweepTitle = "", sweepSub = "";
	private static int sweepColour;
	private static long sweepAt;
	private static long levelPopAt;

	private ParkourLive() {
	}

	private static TikTokLiveMod mod() {
		TikTokLiveMod m = TikTokLiveMod.instance;
		return m != null && m.enabled && m.pkOn.on() ? m : null;
	}

	/** True while you are in the parkour map with Parkour Live on. */
	public static boolean active() {
		return inMap && mod() != null;
	}

	// ================================================================ gifts
	/** Every TikTok gift goes through here (client thread). Streaks are counted once per gift sent. */
	public static void onGift(TikEvent e) {
		TikTokLiveMod m = mod();
		if (m == null || !inMap || e.type() != TikEvent.Type.GIFT) {
			return;
		}
		Act a = actOf(m, e.gift());
		if (a == null) {
			return;
		}
		String key = e.streakKey();
		int done = STREAK.getOrDefault(key, 0);
		int n = Math.max(1, e.repeat()) - done;
		if (e.streaking()) {
			STREAK.put(key, Math.max(1, e.repeat()));
		} else {
			STREAK.remove(key);
		}
		if (n > 0) {
			apply(a, n, e.who());
		}
	}

	private static Act actOf(TikTokLiveMod m, String gift) {
		String k = TikGifts.key(gift);
		if (k.isEmpty()) {
			return null;
		}
		for (Act a : Act.values()) {
			for (String name : m.pkGifts(a).split("[,;]")) {
				if (k.equals(TikGifts.key(name))) {
					return a;
				}
			}
		}
		return null;
	}

	/** First gift name set for an action (shown on the panel and used by the test buttons). */
	public static String giftOf(Act a) {
		TikTokLiveMod m = TikTokLiveMod.instance;
		if (m == null) {
			return "";
		}
		String s = m.pkGifts(a).split("[,;]")[0].trim();
		return s;
	}

	private static void apply(Act a, int n, String who) {
		COUNT[a.ordinal()] += n;
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean counting = resetEnd > 0;
		switch (a) {
			case UP1, DOWN1, UP10, DOWN10 -> {
				if (counting) {
					sweep(Lang.tr("BLOCKED", "BLOQUEADO"), Lang.tr("The RESET timer is running", "O tempo de RESET está a contar"), 0xFFFF5555);
					return;
				}
				if (jailEnd > 0) {
					sweep(Lang.tr("BLOCKED", "BLOQUEADO"), Lang.tr("You are in JAIL", "Estás na JAULA"), 0xFFFF5555);
					return;
				}
				int step = a == Act.UP1 ? 1 : a == Act.DOWN1 ? -1 : a == Act.UP10 ? 10 : -10;
				int delta = step * n;
				giftNote = (delta > 0 ? "+" : "") + delta + " LVL";
				giftNoteAt = System.currentTimeMillis();
				sweep(giftNote, who, delta > 0 ? 0xFF55FF7F : 0xFFFF6B6B);
				changeLevel(mc, delta);
			}
			case RESET -> {
				if (counting) {
					return; // one timer at a time
				}
				TikTokLiveMod m = mod();
				resetEnd = System.currentTimeMillis() + (m == null ? 60 : m.pkResetSeconds.i()) * 1000L;
				lastBeep = -1;
				resetBy = who == null ? "" : who;
				mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_ANVIL_LAND, 0.8F, 0.6F));
			}
			case SAVE -> {
				if (!counting) {
					return;
				}
				resetEnd = 0;
				sweep(Lang.tr("SAVED!", "SALVO!"), who, 0xFF55FF7F);
				mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2F, 0.7F));
			}
			case PUNCH -> punches = Math.min(punches + n, 20);
			case JAIL -> {
				TikTokLiveMod m = mod();
				long secs = (m == null ? 20 : m.pkJailSeconds.i()) * (long) n;
				long now = System.currentTimeMillis();
				if (jailEnd > 0) {
					jailEnd = Math.min(jailEnd + secs * 1000L, now + 120_000L); // more jail gifts: more time
				} else {
					jailEnd = now + secs * 1000L;
					lockUp(mc);
				}
				sweep("JAIL!", who, 0xFFB0B8C8);
			}
		}
	}

	// ================================================================ the map
	/** Runs on the server thread: in the map? which level? */
	private static void read(MinecraftServer server, UUID me) {
		try {
			ScoreboardObjective obj = server.getScoreboard().getNullableObjective("pk_level");
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(me);
			if (obj == null || p == null) {
				inMap = false;
				return;
			}
			var score = server.getScoreboard().getScore(p, obj);
			level = score == null ? 0 : score.getScore();
			inMap = true;
		} catch (Throwable t) {
			inMap = false;
		}
	}

	private static void command(MinecraftServer server, String cmd) {
		try {
			server.getCommandManager().parseAndExecute(server.getCommandSource().withSilent(), cmd);
		} catch (Throwable ignored) {
			// a command the map does not have
		}
	}

	/** Moves you up / down and teleports you to that checkpoint (0 = the start of the parkour). */
	private static void changeLevel(MinecraftClient mc, int delta) {
		setLevel(mc, null, delta);
	}

	private static void setLevel(MinecraftClient mc, Integer to, int delta) {
		MinecraftServer server = mc.getServer();
		if (server == null || mc.player == null) {
			return;
		}
		UUID me = mc.player.getUuid();
		server.execute(() -> {
			read(server, me);
			if (!inMap) {
				return;
			}
			int now = Math.max(0, level);
			int target = Math.max(0, Math.min(MAX, to != null ? to : now + delta));
			if (target == now && to == null) {
				return;
			}
			String u = me.toString();
			release(server);
			command(server, "scoreboard players set " + u + " pk_level " + target);
			if (target == 0) {
				command(server, "execute as " + u + " at @s run function main:player/tp/to_start");
			} else {
				command(server, "execute as " + u + " at @s run function pk:to_checkpoint");
			}
			level = target;
		});
	}

	/** Builds the cage around you: iron bar walls, a floor and a roof, only where there is air. */
	private static void lockUp(MinecraftClient mc) {
		MinecraftServer server = mc.getServer();
		if (server == null || mc.player == null) {
			return;
		}
		UUID me = mc.player.getUuid();
		server.execute(() -> {
			release(server);
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(me);
			if (p == null) {
				return;
			}
			var world = p.getEntityWorld();
			var c = p.getBlockPos();
			String u = me.toString();
			// in the middle of the cage, standing still
			command(server, "tp " + u + " " + (c.getX() + 0.5) + " " + c.getY() + " " + (c.getZ() + 0.5));
			cageWorld = world.getRegistryKey();
			for (int dy = -1; dy <= 2; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						boolean wall = dy >= 0 && dy <= 1 && (dx != 0 || dz != 0);
						boolean cap = dy == -1 || dy == 2;
						if (!wall && !cap) {
							continue;
						}
						var pos = c.add(dx, dy, dz);
						if (!world.getBlockState(pos).isAir()) {
							continue;
						}
						String block = wall ? "minecraft:iron_bars" : "minecraft:polished_deepslate";
						command(server, "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block);
						if (!world.getBlockState(pos).isAir()) {
							CAGE.add(pos.toImmutable());
						}
					}
				}
			}
			command(server, "execute at " + u + " run playsound minecraft:block.iron_door.close master " + u + " ~ ~ ~ 1 0.7");
		});
	}

	/** Takes the cage away again (only the blocks we put there and that are still bars / deepslate). Server thread. */
	public static void release(MinecraftServer server) {
		if (CAGE.isEmpty() || cageWorld == null) {
			return;
		}
		var world = server.getWorld(cageWorld);
		for (var pos : CAGE) {
			if (world != null) {
				var st = world.getBlockState(pos);
				if (st.isOf(net.minecraft.block.Blocks.IRON_BARS) || st.isOf(net.minecraft.block.Blocks.POLISHED_DEEPSLATE)) {
					world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());
				}
			}
		}
		CAGE.clear();
		cageWorld = null;
	}

	/** The map's own "LEVEL X / 100" boss bars stay hidden: the client draws the level itself. */
	private static void hideBars(MinecraftServer server) {
		server.execute(() -> {
			for (int i = 0; i <= MAX; i++) {
				command(server, "bossbar set pk:lvl_" + i + " visible false");
			}
		});
	}

	// ================================================================ tick
	public static void tick(MinecraftClient mc) {
		ticks++;
		TikGifts.saveSoon();
		MinecraftServer server = mc.getServer();
		if (server == null || mc.player == null || mod() == null) {
			inMap = false;
			wasInMap = false;
			resetEnd = 0;
			punches = 0;
			jailEnd = 0;
			lastLevel = -1;
			return;
		}
		if (ticks % 5 == 0) {
			UUID me = mc.player.getUuid();
			server.execute(() -> read(server, me));
		}
		if (!inMap) {
			wasInMap = false;
			return;
		}
		if (!wasInMap || ticks % 600 == 0) {
			hideBars(server);
		}
		wasInMap = true;
		// reached a new checkpoint (by playing or by a gift)
		int lv = level;
		if (lastLevel >= 0 && lv != lastLevel) {
			levelPopAt = System.currentTimeMillis();
			boolean byGift = giftNote != null && System.currentTimeMillis() - giftNoteAt < 3000L;
			if (lv > lastLevel && !byGift) {
				sweep(Lang.tr("CHECKPOINT!", "CHECKPOINT!"), "LEVEL " + lv, 0xFFFFD23C);
			}
			giftNote = null;
		}
		lastLevel = lv;
		// RESET timer
		if (resetEnd > 0) {
			long left = resetEnd - System.currentTimeMillis();
			int sec = (int) Math.ceil(left / 1000.0);
			if (sec != lastBeep && sec <= 10 && sec > 0) {
				lastBeep = sec;
				mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_HAT, sec <= 3 ? 1.6F : 1.2F));
			}
			if (left <= 0) {
				resetEnd = 0;
				setLevel(mc, 0, 0);
				sweep("RESET!", Lang.tr("Back to level 0", "De volta ao nível 0"), 0xFFFF4040);
				mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_ANVIL_LAND, 0.5F, 0.8F));
			}
		}
		// JAIL: opens by itself
		if (jailEnd > 0 && System.currentTimeMillis() >= jailEnd) {
			jailEnd = 0;
			server.execute(() -> release(server));
			sweep(Lang.tr("FREE!", "LIVRE!"), "", 0xFF55FF7F);
			mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_IRON_DOOR_OPEN, 1F, 0.8F));
		}
		// golden punches: one after the other
		if (punchCooldown > 0) {
			punchCooldown--;
		} else if (punches > 0) {
			punches--;
			punchCooldown = 12;
			punch(mc);
		}
	}

	/** Like being hit by a Punch arrow: thrown back and up, with the map's checkpoint sound. */
	private static void punch(MinecraftClient mc) {
		var p = mc.player;
		if (p == null) {
			return;
		}
		TikTokLiveMod m = mod();
		double k = (m == null ? 100 : m.pkPunchStrength.i()) / 100.0;
		Vec3d look = p.getRotationVec(1F);
		double hx = -look.x, hz = -look.z, len = Math.sqrt(hx * hx + hz * hz);
		if (len < 1e-4) {
			hx = 1;
			hz = 0;
			len = 1;
		}
		p.setVelocity(hx / len * 1.5 * k, 0.55 * Math.min(1.5, k), hz / len * 1.5 * k);
		p.velocityDirty = true;
		mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.2F, 1F));
		mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_ARROW_HIT_PLAYER, 1F, 0.6F));
		if (mc.world != null) {
			for (int i = 0; i < 14; i++) {
				mc.world.addParticleClient(ParticleTypes.CRIT, p.getX() + (Math.random() - 0.5), p.getY() + 1 + Math.random() * 0.6,
						p.getZ() + (Math.random() - 0.5), hx * 0.3, 0.2, hz * 0.3);
			}
		}
	}

	private static void sweep(String title, String sub, int colour) {
		sweepTitle = title;
		sweepSub = sub == null ? "" : sub;
		sweepColour = colour;
		sweepAt = System.currentTimeMillis();
	}

	// ================================================================ tests (no live needed)
	public static void test(Act a) {
		if (!inMap) {
			sweep(Lang.tr("NOT IN THE MAP", "NÃO ESTÁS NO MAPA"), Lang.tr("Open Parkour Paradise 3 in Add-ons", "Abre o Parkour Paradise 3 nos Add-ons"), 0xFFFF6B6B);
			return;
		}
		apply(a, 1, Lang.tr("Test", "Teste"));
	}

	// ================================================================ drawing
	public static void render(DrawContext c) {
		TikTokLiveMod m = mod();
		if (m == null || !inMap) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.options.hudHidden) {
			return;
		}
		int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
		long now = System.currentTimeMillis();
		drawSweep(c, w, h, now);
		if (resetEnd > 0) {
			drawReset(c, w, h, now);
		}
	}

	// ---- for the "Parkour Level" HUD element
	/** In the parkour map with Parkour Live on. */
	public static boolean showing() {
		return inMap && mod() != null;
	}

	public static int level() {
		return Math.max(0, level);
	}

	public static long levelChangedAt() {
		return levelPopAt;
	}

	/** Seconds of JAIL left, 0 = free. */
	public static int jailLeft() {
		return jailEnd <= 0 ? 0 : (int) Math.ceil(Math.max(0, jailEnd - System.currentTimeMillis()) / 1000.0);
	}

	/** The gift's real picture when known, else a coloured badge; the count is drawn over its bottom. */
	private static void drawGift(DrawContext c, Act a, int x, int y, int s) {
		Identifier icon = TikGifts.icon(giftOf(a));
		if (icon != null) {
			c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, s, s, s, s);
		} else {
			int col = switch (a) {
				case UP1, UP10 -> 0xFF3FB950;
				case DOWN1, DOWN10 -> 0xFFE5534B;
				case RESET -> 0xFF4C8DF6;
				case SAVE -> 0xFFF0B429;
				case PUNCH -> 0xFFE8A93A;
				case JAIL -> 0xFF8A93A6;
			};
			Gfx.circle(c, x + s / 2F, y + s / 2F, s / 2F, col);
			String g = switch (a) {
				case UP1 -> "+1";
				case DOWN1 -> "-1";
				case UP10 -> "+10";
				case DOWN10 -> "-10";
				case RESET -> "R";
				case SAVE -> "S";
				case PUNCH -> "P";
				case JAIL -> "J";
			};
			var tr = MinecraftClient.getInstance().textRenderer;
			c.drawText(tr, Text.literal(g), x + s / 2 - tr.getWidth(g) / 2, y + s / 2 - 4, 0xFFFFFFFF, true);
		}
		if (a != Act.PUNCH && a != Act.JAIL && s <= 20) {
			String n = String.valueOf(COUNT[a.ordinal()]);
			var tr = MinecraftClient.getInstance().textRenderer;
			c.getMatrices().pushMatrix();
			c.getMatrices().translate(x + s / 2F, y + s - 3);
			c.getMatrices().scale(0.75F, 0.75F);
			c.drawText(tr, Text.literal(n).styled(st -> st.withBold(true)), -tr.getWidth(n) / 2, 0, 0xFFFFFFFF, true);
			c.getMatrices().popMatrix();
		}
	}

	/** Text that slides in from the left, waits in the middle and slides out to the right, on a coloured band. */
	private static void drawSweep(DrawContext c, int w, int h, long now) {
		long age = now - sweepAt;
		if (sweepTitle.isEmpty() || age > 2100) {
			return;
		}
		var tr = MinecraftClient.getInstance().textRenderer;
		float in = Math.min(1F, age / 380F), out = Math.max(0F, (age - 1500) / 600F);
		float ein = 1 - (1 - in) * (1 - in) * (1 - in), eout = out * out * out;
		int cy = (int) (h * 0.42);
		// the band
		int bandW = (int) (w * ein);
		int bandX = (int) (w * eout);
		int alpha = (int) (150 * (1 - out));
		Gfx.gradientH(c, bandX, cy - 14, Math.max(0, bandW - bandX), 34, Gfx.withAlpha(sweepColour, 0), Gfx.withAlpha(sweepColour, alpha / 2));
		Gfx.fill(c, bandX, cy - 15, bandW, cy - 14, Gfx.withAlpha(sweepColour, alpha));
		Gfx.fill(c, bandX, cy + 20, bandW, cy + 21, Gfx.withAlpha(sweepColour, alpha));
		// the title
		Text t = Text.literal(sweepTitle).styled(st -> st.withBold(true));
		int tw = tr.getWidth(t);
		float sc = 2.4F;
		float x = -tw * sc + (w / 2F + tw * sc / 2F) * ein + (w / 2F + tw * sc) * eout;
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(x, cy - 10);
		c.getMatrices().scale(sc, sc);
		c.drawText(tr, t, 1, 1, 0xC0000000, false);
		c.drawText(tr, t, 0, 0, sweepColour | 0xFF000000, false);
		c.getMatrices().popMatrix();
		if (!sweepSub.isEmpty()) {
			int sw = tr.getWidth(sweepSub);
			float sx = w - (w / 2F + sw / 2F) * ein - (w / 2F + sw) * eout; // comes from the other side
			c.drawText(tr, Text.literal(sweepSub), (int) sx, cy + 10, 0xFFFFFFFF, true);
		}
	}

	/** RESET + the seconds left + the SAVE gift, in the middle of the screen. */
	private static void drawReset(DrawContext c, int w, int h, long now) {
		var tr = MinecraftClient.getInstance().textRenderer;
		long left = Math.max(0, resetEnd - now);
		int sec = (int) Math.ceil(left / 1000.0);
		int cx = w / 2, cy = (int) (h * 0.5);
		boolean hurry = sec <= 10;
		float blink = hurry ? (float) (0.5 + 0.5 * Math.sin(now / 90.0)) : 1F;
		String hint = "SAVE: " + giftOf(Act.SAVE);
		int bw = Math.max(92, tr.getWidth(hint) + 14), bh = 92;
		Gfx.rrect(c, cx - bw / 2, cy - 46, bw, bh, 6, 0x99101014);
		if (hurry) {
			Gfx.rrectBorder(c, cx - bw / 2, cy - 46, bw, bh, 6, 0, Gfx.withAlpha(0xFF3030, (int) (255 * blink)));
		}
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(cx, cy - 40);
		c.getMatrices().scale(1.4F, 1.4F);
		c.drawText(tr, Text.literal("RESET"), -tr.getWidth("RESET") / 2, 0, 0xFFFF3B3B, true);
		c.getMatrices().popMatrix();
		String n = String.valueOf(sec);
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(cx, cy - 24);
		c.getMatrices().scale(3F, 3F);
		c.drawText(tr, Text.literal(n), -tr.getWidth(n) / 2, 0, 0xFFFFFFFF, true);
		c.getMatrices().popMatrix();
		drawGift(c, Act.SAVE, cx - 14, cy + 4, 28);
		c.drawText(tr, Text.literal(hint), cx - tr.getWidth(hint) / 2, cy + 35, 0xFFFFD23C, true);
		if (!resetBy.isEmpty()) {
			Gfx.textC(c, resetBy, cx, cy + 50, 0xAAFFFFFF);
		}
	}
}
