package dev.solclient.parkour;

import dev.solclient.Gfx;
import dev.solclient.mods.TikTokLiveMod;
import dev.solclient.tiktok.TikEvent;
import dev.solclient.tiktok.TikGifts;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;

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
	private static final Identifier WATERMELON = Identifier.fromNamespaceAndPath("sol_client", "textures/parkour/watermelon.png");
	/** Your own SAVE picture (kubo-tiktok/save.png), chosen in the TikTok Live settings; null = the watermelon. */
	private static volatile Identifier customSave;
	private static volatile int savePicW, savePicH;
	private static volatile boolean saveChecked;

	/** The file of your own SAVE picture. */
	public static java.nio.file.Path savePictureFile() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("kubo-tiktok").resolve("save.png");
	}

	/** Loads the SAVE picture again (after you chose a new one or went back to the watermelon). */
	public static void reloadSavePicture() {
		customSave = null;
		saveChecked = false;
	}

	private static Identifier savePicture() {
		if (!saveChecked) {
			saveChecked = true;
			java.nio.file.Path f = savePictureFile();
			dev.solclient.social.Social.IO.execute(() -> {
				try {
					if (!java.nio.file.Files.isRegularFile(f)) {
						return;
					}
					com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(java.nio.file.Files.newInputStream(f));
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> {
						Identifier id = Identifier.fromNamespaceAndPath("sol_client", "parkour/custom_save_" + System.nanoTime());
						mc.getTextureManager().register(id, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "kubo_save_picture", img));
						savePicW = img.getWidth();
						savePicH = img.getHeight();
						customSave = id;
					});
				} catch (Exception e) {
					System.err.println("[Caos] SAVE picture: " + e);
				}
			});
		}
		Identifier c = customSave;
		return c != null ? c : WATERMELON;
	}

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
	private static int lastBarSec;
	/** When the jail opens (ms), 0 = free. */
	private static long jailEnd;
	/** Blocks of the cage that we placed (only where there was air), to take away again. Server thread only. */
	private static final java.util.List<net.minecraft.core.BlockPos> CAGE = new java.util.ArrayList<>();
	private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> cageWorld;
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
		Act a = actOf(m, e.gift()), fa = friendAct(m, e.gift());
		if (a == null && fa == null) {
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
			if (a != null) {
				apply(a, n, e.who());
			}
			if (fa != null) {
				applyFriend(fa, n, e.who());
			}
		}
	}

	/** Which action this gift does (null = none), from the gift names in the settings. */
	public static Act actFor(String gift) {
		TikTokLiveMod m = TikTokLiveMod.instance;
		if (m == null) {
			return null;
		}
		Act a = actOf(m, gift);
		return a != null ? a : friendAct(m, gift);
	}

	private static Act friendAct(TikTokLiveMod m, String gift) {
		String k = TikGifts.key(gift);
		if (k.isEmpty()) {
			return null;
		}
		for (Act a : Act.values()) {
			for (String name : m.pkFriendGifts(a).split("[,;]")) {
				if (k.equals(TikGifts.key(name))) {
					return a;
				}
			}
		}
		return null;
	}

	// ================================================================ gifts for your friend
	private static long friendResetEnd, friendJailEnd;
	private static int friendPunches, friendPunchCooldown, friendBarSec = -1;
	private static final java.util.Set<net.minecraft.core.BlockPos> FRIEND_CAGE = new java.util.HashSet<>();

	/** The friends the "friend" gifts hit (server thread): the name(s) you set, or everyone except you. */
	private static java.util.List<ServerPlayer> friends(MinecraftServer server, UUID me) {
		TikTokLiveMod m = mod();
		String names = m == null ? "" : m.pkFriendName.get().trim();
		java.util.List<ServerPlayer> out = new java.util.ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.getUUID().equals(me)) {
				continue;
			}
			if (names.isEmpty()) {
				out.add(p);
				continue;
			}
			for (String n : names.split("[,; ]+")) {
				if (n.equalsIgnoreCase(p.getName().getString())) {
					out.add(p);
				}
			}
		}
		return out;
	}

	private static void applyFriend(Act a, int n, String who) {
		Minecraft mc = Minecraft.getInstance();
		MinecraftServer server = mc.getSingleplayerServer();
		TikTokLiveMod m = mod();
		if (server == null || mc.player == null || m == null) {
			sweep("HOST ONLY", "Friend gifts need your Host World", 0xFFFF6B6B);
			return;
		}
		UUID me = mc.player.getUUID();
		long now = System.currentTimeMillis();
		switch (a) {
			case UP1, DOWN1, UP10, DOWN10 -> {
				if (friendResetEnd > 0 || friendJailEnd > 0) {
					sweep("BLOCKED", "Your friend is in RESET or JAIL", 0xFFFF5555);
					return;
				}
				int step = a == Act.UP1 ? 1 : a == Act.DOWN1 ? -1 : a == Act.UP10 ? 10 : -10;
				int delta = step * n;
				sweep("FRIEND " + (delta > 0 ? "+" : "") + delta + " LVL", who, delta > 0 ? 0xFF55FF7F : 0xFFFF6B6B);
				server.execute(() -> setFriendLevel(server, me, null, delta));
			}
			case RESET -> {
				if (friendResetEnd > 0) {
					return;
				}
				friendResetEnd = now + m.pkResetSeconds.i() * 1000L;
				friendBarSec = -1;
				sweep("FRIEND RESET", who, 0xFFFF4040);
			}
			case SAVE -> {
				if (friendResetEnd == 0) {
					return;
				}
				friendResetEnd = 0;
				sweep("FRIEND SAVED!", who, 0xFF55FF7F);
				server.execute(() -> actionbar(server, friends(server, me), "SAVED!", "green"));
			}
			case PUNCH -> {
				friendPunches = Math.min(friendPunches + n, 20);
				sweep("FRIEND PUNCH!", who, 0xFFFFD34D);
			}
			case JAIL -> {
				long secs = m.pkJailSeconds.i() * (long) n;
				sweep("FRIEND JAIL!", who, 0xFFB0B8C8);
				if (friendJailEnd > 0) {
					friendJailEnd = Math.min(friendJailEnd + secs * 1000L, now + 120_000L);
				} else {
					friendJailEnd = now + secs * 1000L;
					server.execute(() -> {
						var who2 = friends(server, me);
						for (ServerPlayer p : who2) {
							cage(server, p, FRIEND_CAGE);
						}
						actionbar(server, who2, "JAIL!", "gray");
					});
				}
			}
		}
	}

	/** Server thread. */
	private static void setFriendLevel(MinecraftServer server, UUID me, Integer to, int delta) {
		Objective obj = server.getScoreboard().getObjective("pk_level");
		if (obj == null) {
			return;
		}
		var who = friends(server, me);
		for (ServerPlayer p : who) {
			var sc = server.getScoreboard().getPlayerScoreInfo(p, obj);
			int now = Math.max(0, sc == null ? 0 : sc.value());
			int target = Math.max(1, Math.min(MAX, to != null ? to : now + delta));
			if (to == null && target == now) {
				continue;
			}
			String u = p.getStringUUID();
			command(server, "scoreboard players set " + u + " pk_level " + target);
			command(server, "execute as " + u + " at @s run function pk:to_checkpoint");
		}
		if (to != null) {
			actionbar(server, who, "RESET! Back to checkpoint 1", "red");
		} else {
			actionbar(server, who, (delta > 0 ? "+" : "") + delta + " LVL", delta > 0 ? "green" : "red");
		}
	}

	private static void releaseFriendCage(MinecraftServer server) {
		if (FRIEND_CAGE.isEmpty() || cageWorld == null) {
			FRIEND_CAGE.clear();
			return;
		}
		var world = server.getLevel(cageWorld);
		for (var pos : FRIEND_CAGE) {
			if (world != null) {
				var st = world.getBlockState(pos);
				if (st.is(net.minecraft.world.level.block.Blocks.IRON_BARS) || st.is(net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE)) {
					world.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
		}
		FRIEND_CAGE.clear();
	}

	private static void friendTick(Minecraft mc, MinecraftServer server) {
		if (mc.player == null) {
			return;
		}
		UUID me = mc.player.getUUID();
		long now = System.currentTimeMillis();
		if (friendResetEnd > 0) {
			int sec = (int) Math.ceil((friendResetEnd - now) / 1000.0);
			if (sec <= 0) {
				friendResetEnd = 0;
				server.execute(() -> setFriendLevel(server, me, 1, 0));
			} else if (sec != friendBarSec) {
				friendBarSec = sec;
				String save = giftOfFriend(Act.SAVE);
				server.execute(() -> actionbar(server, friends(server, me), "RESET " + sec + "s" + (save.isEmpty() ? "" : " - SAVE: " + save), "red"));
			}
		}
		if (friendJailEnd > 0 && now >= friendJailEnd) {
			friendJailEnd = 0;
			server.execute(() -> releaseFriendCage(server));
		}
		if (friendPunchCooldown > 0) {
			friendPunchCooldown--;
		} else if (friendPunches > 0) {
			friendPunches--;
			friendPunchCooldown = 12;
			TikTokLiveMod m = mod();
			double ks = (m == null ? 100 : m.pkPunchStrength.i()) / 100.0;
			server.execute(() -> {
				for (ServerPlayer t : friends(server, me)) {
					launch(server, t, ks);
				}
			});
		}
	}

	private static String giftOfFriend(Act a) {
		TikTokLiveMod m = TikTokLiveMod.instance;
		return m == null ? "" : m.pkFriendGifts(a).split("[,;]")[0].trim();
	}

	private static void launch(MinecraftServer server, ServerPlayer t, double ks) {
		Vec3 lk = t.getViewVector(1F);
		double x = -lk.x, z = -lk.z, l = Math.sqrt(x * x + z * z);
		if (l < 1e-4) {
			x = 1;
			z = 0;
			l = 1;
		}
		t.setDeltaMovement(x / l * 1.5 * ks, 0.55 * Math.min(1.5, ks), z / l * 1.5 * ks);
		t.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(t));
		String u = t.getStringUUID();
		command(server, "execute at " + u + " run playsound minecraft:block.note_block.bell master " + u + " ~ ~ ~ 1 1.2");
		command(server, "execute at " + u + " run playsound minecraft:entity.arrow.hit_player master " + u + " ~ ~ ~ 1 0.6");
		command(server, "execute at " + u + " run particle minecraft:crit ~ ~1 ~ 0.4 0.4 0.4 0.3 14");
	}

	/** The test button for the friend gifts. */
	public static void testFriend(Act a) {
		if (!inMap) {
			sweep("NOT IN THE MAP", "Open Parkour Paradise 3 in Add-ons", 0xFFFF6B6B);
			return;
		}
		applyFriend(a, 1, "Test");
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
		Minecraft mc = Minecraft.getInstance();
		boolean counting = resetEnd > 0;
		switch (a) {
			case UP1, DOWN1, UP10, DOWN10 -> {
				if (counting) {
					sweep("BLOCKED", "The RESET timer is running", 0xFFFF5555);
					return;
				}
				if (jailEnd > 0) {
					sweep("BLOCKED", "You are in JAIL", 0xFFFF5555);
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
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 0.8F, 0.6F));
			}
			case SAVE -> {
				if (!counting) {
					return;
				}
				resetEnd = 0;
				sweep("SAVED!", who, 0xFF55FF7F);
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2F, 0.7F));
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
			Objective obj = server.getScoreboard().getObjective("pk_level");
			ServerPlayer p = server.getPlayerList().getPlayer(me);
			if (obj == null || p == null) {
				inMap = false;
				return;
			}
			var score = server.getScoreboard().getPlayerScoreInfo(p, obj);
			level = score == null ? 0 : score.value();
			inMap = true;
		} catch (Throwable t) {
			inMap = false;
		}
	}

	private static void command(MinecraftServer server, String cmd) {
		try {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
		} catch (Throwable ignored) {
			// a command the map does not have
		}
	}

	/** One player at a time: the attribute command refuses "@a" as soon as there are two players. */
	private static void jumpAll(MinecraftServer server, String what) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			command(server, "attribute " + p.getStringUUID() + " minecraft:jump_strength base " + what);
		}
	}

	/** Who the gifts hit (server thread): you, everyone in the map, or only your friends. */
	private static java.util.List<ServerPlayer> targets(MinecraftServer server, UUID me) {
		TikTokLiveMod m = mod();
		int mode = m == null ? 0 : m.pkTargets.idx();
		java.util.List<ServerPlayer> out = new java.util.ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			boolean isMe = p.getUUID().equals(me);
			if (mode == 0 ? isMe : mode == 1 || !isMe) {
				out.add(p);
			}
		}
		return out;
	}

	/** A line above the hotbar of the players a gift hit (friends without Kubo see what happened too). */
	private static void actionbar(MinecraftServer server, java.util.List<ServerPlayer> who, String text, String colour) {
		String t = text.replaceAll("[\\\"]", "");
		for (ServerPlayer p : who) {
			command(server, "title " + p.getStringUUID() + " actionbar {text:\"" + t + "\",color:\"" + colour + "\",bold:true}");
		}
	}

	/** Moves you up / down and teleports you to that checkpoint (0 = the start of the parkour). */
	private static void changeLevel(Minecraft mc, int delta) {
		setLevel(mc, null, delta);
	}

	private static void setLevel(Minecraft mc, Integer to, int delta) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null || mc.player == null) {
			return;
		}
		UUID me = mc.player.getUUID();
		server.execute(() -> {
			read(server, me);
			if (!inMap) {
				return;
			}
			Objective obj = server.getScoreboard().getObjective("pk_level");
			var who = targets(server, me);
			release(server);
			for (ServerPlayer p : who) {
				var sc = server.getScoreboard().getPlayerScoreInfo(p, obj);
				int now = Math.max(0, sc == null ? 0 : sc.value());
				if (to == null && delta < 0 && now <= 1) {
					continue; // already at the start: nothing to go down to
				}
				int target = Math.max(1, Math.min(MAX, to != null ? to : now + delta));
				if (target == now && to == null) {
					continue;
				}
				String u = p.getStringUUID();
				command(server, "scoreboard players set " + u + " pk_level " + target);
				command(server, "execute as " + u + " at @s run function pk:to_checkpoint");
				if (p.getUUID().equals(me)) {
					level = target;
				}
			}
			if (to != null) {
				actionbar(server, who, "RESET! Back to checkpoint 1", "red");
			} else {
				actionbar(server, who, (delta > 0 ? "+" : "") + delta + " LVL", delta > 0 ? "green" : "red");
			}
		});
	}

	/** Builds the cage around you: iron bar walls, a floor and a roof, only where there is air. */
	private static void lockUp(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null || mc.player == null) {
			return;
		}
		UUID me = mc.player.getUUID();
		server.execute(() -> {
			release(server);
			var who = targets(server, me);
			for (ServerPlayer p : who) {
				cage(server, p, CAGE);
			}
			actionbar(server, who, "JAIL!", "gray");
		});
	}

	private static void cage(MinecraftServer server, ServerPlayer p, java.util.Collection<net.minecraft.core.BlockPos> blocks) {
		{
			var world = p.level();
			var c = p.blockPosition();
			String u = p.getStringUUID();
			// in the middle of the cage, standing still
			command(server, "tp " + u + " " + (c.getX() + 0.5) + " " + c.getY() + " " + (c.getZ() + 0.5));
			cageWorld = world.dimension();
			for (int dy = -1; dy <= 2; dy++) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						boolean wall = dy >= 0 && dy <= 1 && (dx != 0 || dz != 0);
						boolean cap = dy == -1 || dy == 2;
						if (!wall && !cap) {
							continue;
						}
						var pos = c.offset(dx, dy, dz);
						if (!world.getBlockState(pos).isAir()) {
							continue;
						}
						String block = wall ? "minecraft:iron_bars" : "minecraft:polished_deepslate";
						command(server, "setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block);
						if (!world.getBlockState(pos).isAir()) {
							blocks.add(pos.immutable());
						}
					}
				}
			}
			command(server, "execute at " + u + " run playsound minecraft:block.iron_door.close master " + u + " ~ ~ ~ 1 0.7");
		}
	}

	/** Takes the cage away again (only the blocks we put there and that are still bars / deepslate). Server thread. */
	public static void release(MinecraftServer server) {
		if (CAGE.isEmpty() || cageWorld == null) {
			return;
		}
		var world = server.getLevel(cageWorld);
		for (var pos : CAGE) {
			if (world != null) {
				var st = world.getBlockState(pos);
				if (st.is(net.minecraft.world.level.block.Blocks.IRON_BARS) || st.is(net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE)) {
					world.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
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
			// the level in the tab list: that also sends everyone's level to your friends' clients (Parkour Race)
			command(server, "scoreboard objectives setdisplay list pk_level");
		});
	}

	// ================================================================ race (Host World with friends)
	private static boolean raceOn;
	private static int raceCount = -1;
	private static final Map<String, Integer> RIVALS = new HashMap<>();

	/** Everyone in the parkour map with their level, best first (from the scoreboard every client gets). */
	public static java.util.List<Map.Entry<String, Integer>> standings() {
		Minecraft mc = Minecraft.getInstance();
		java.util.List<Map.Entry<String, Integer>> out = new java.util.ArrayList<>();
		if (mc.level == null || mc.getConnection() == null) {
			return out;
		}
		Objective obj = mc.level.getScoreboard().getObjective("pk_level");
		if (obj == null) {
			return out;
		}
		for (var e : mc.level.getScoreboard().listPlayerScores(obj)) {
			if (mc.getConnection().getPlayerInfo(e.owner()) != null) { // only players who are here now
				out.add(Map.entry(e.owner(), e.value()));
			}
		}
		out.sort((a, b) -> b.getValue() - a.getValue());
		return out;
	}

	public static boolean racing() {
		return raceOn;
	}

	/** /pkrace start: everyone back to checkpoint 1, 3-2-1-GO, first to level 100 wins. Only the host can. */
	public static void startRace() {
		Minecraft mc = Minecraft.getInstance();
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null || !inMap) {
			sweep("HOST ONLY", "Host the Parkour Paradise 3 map to start a race", 0xFFFF6B6B);
			return;
		}
		resetEnd = 0;
		jailEnd = 0;
		server.execute(() -> {
			release(server);
			command(server, "scoreboard objectives setdisplay list pk_level");
			command(server, "scoreboard players set @a pk_level 1");
			command(server, "execute as @a at @s run function pk:to_checkpoint");
			command(server, "effect give @a minecraft:slowness 5 255 true");
			jumpAll(server, "set 0");
			command(server, "title @a times 0 25 5");
		});
		raceOn = true;
		raceCount = 60; // 3, 2, 1 (one second each), then GO
	}

	public static void stopRace() {
		MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
		raceOn = false;
		raceCount = -1;
		if (server != null) {
			server.execute(() -> {
				jumpAll(server, "reset");
				command(server, "effect clear @a minecraft:slowness");
				command(server, "title @a title {text:\"RACE STOPPED\",color:\"gray\",bold:true}");
			});
		}
	}

	private static void raceTick(Minecraft mc, MinecraftServer server) {
		if (!raceOn) {
			return;
		}
		if (raceCount >= 0) {
			if (raceCount % 20 == 0) {
				int n = raceCount / 20;
				String t = "{text:\"" + n + "\",color:\"gold\",bold:true}";
				server.execute(() -> {
					if (n > 0) {
						command(server, "title @a title " + t);
						command(server, "execute as @a at @s run playsound minecraft:block.note_block.hat master @s ~ ~ ~ 1 1.2");
					} else {
						jumpAll(server, "reset");
						command(server, "effect clear @a minecraft:slowness");
						command(server, "title @a title {text:\"GO!\",color:\"green\",bold:true}");
						command(server, "execute as @a at @s run playsound minecraft:block.note_block.bell master @s ~ ~ ~ 1 1.6");
					}
				});
			}
			raceCount--;
			return;
		}
		if (ticks % 10 != 0) {
			return;
		}
		server.execute(() -> {
			Objective obj = server.getScoreboard().getObjective("pk_level");
			if (obj == null || !raceOn) {
				return;
			}
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				var sc = server.getScoreboard().getPlayerScoreInfo(p, obj);
				if (sc != null && sc.value() >= MAX) {
					raceOn = false;
					String name = p.getName().getString().replaceAll("[^A-Za-z0-9_]", "");
					command(server, "title @a times 5 80 20");
					command(server, "title @a subtitle {text:\"reached level 100 first!\",color:\"white\"}");
					command(server, "title @a title {text:\"" + name + " WINS!\",color:\"gold\",bold:true}");
					command(server, "execute as @a at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1");
					return;
				}
			}
		});
	}

	/** "X PASSED YOU!" / "YOU PASSED X!" when the order changes. */
	private static void watchRivals(Minecraft mc) {
		if (ticks % 10 != 0 || mc.player == null) {
			return;
		}
		String me = mc.player.getName().getString();
		var list = standings();
		if (list.size() < 2) {
			RIVALS.clear();
			return;
		}
		int mine = 0;
		for (var e : list) {
			if (e.getKey().equals(me)) {
				mine = e.getValue();
			}
		}
		for (var e : list) {
			String who = e.getKey();
			if (who.equals(me)) {
				continue;
			}
			Integer before = RIVALS.get(who);
			Integer myBefore = RIVALS.get(me);
			if (before != null && myBefore != null) {
				if (before <= myBefore && e.getValue() > mine) {
					sweep(who.toUpperCase(java.util.Locale.ROOT) + " PASSED YOU!", "LEVEL " + e.getValue(), 0xFFFF5555);
				} else if (before >= myBefore && e.getValue() < mine) {
					sweep("YOU PASSED " + who.toUpperCase(java.util.Locale.ROOT) + "!", "LEVEL " + mine, 0xFF55FF7F);
				}
			}
		}
		RIVALS.clear();
		for (var e : list) {
			RIVALS.put(e.getKey(), e.getValue());
		}
	}

	/** A friend in your hosted parkour map: your level comes from the scoreboard the host sends. */
	private static void guestTick(Minecraft mc) {
		Objective obj = mc.level.getScoreboard().getObjective("pk_level");
		if (obj == null) {
			inMap = false;
			lastLevel = -1;
			return;
		}
		inMap = true;
		var sc = mc.level.getScoreboard().getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly(mc.player.getName().getString()), obj);
		int lv = sc == null ? 0 : sc.value();
		level = lv;
		if (lastLevel >= 0 && lv != lastLevel) {
			levelPopAt = System.currentTimeMillis();
			if (lv > lastLevel) {
				sweep("CHECKPOINT!", "LEVEL " + lv, 0xFFFFD23C);
			}
		}
		lastLevel = lv;
		watchRivals(mc);
	}

	// ================================================================ tick
	public static void tick(Minecraft mc) {
		ticks++;
		TikGifts.saveSoon();
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null && mc.player != null && mc.level != null && mod() != null) {
			guestTick(mc);
			return;
		}
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
			UUID me = mc.player.getUUID();
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
		raceTick(mc, server);
		watchRivals(mc);
		// reached a new checkpoint (by playing or by a gift)
		int lv = level;
		if (lastLevel >= 0 && lv != lastLevel) {
			levelPopAt = System.currentTimeMillis();
			boolean byGift = giftNote != null && System.currentTimeMillis() - giftNoteAt < 3000L;
			if (lv > lastLevel && !byGift) {
				sweep("CHECKPOINT!", "LEVEL " + lv, 0xFFFFD23C);
			}
			giftNote = null;
		}
		lastLevel = lv;
		// RESET timer
		if (resetEnd > 0) {
			long left = resetEnd - System.currentTimeMillis();
			int sec = (int) Math.ceil(left / 1000.0);
			TikTokLiveMod tm = mod();
			if (sec != lastBarSec && sec > 0 && tm != null && tm.pkTargets.idx() != 0) {
				lastBarSec = sec; // friends see the countdown above their hotbar
				UUID me = mc.player.getUUID();
				int s0 = sec;
				String save = giftOf(Act.SAVE);
				server.execute(() -> actionbar(server, targets(server, me), "RESET " + s0 + "s - SAVE: " + save, "red"));
			}
			if (sec != lastBeep && sec <= 10 && sec > 0) {
				lastBeep = sec;
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT, sec <= 3 ? 1.6F : 1.2F));
			}
			if (left <= 0) {
				resetEnd = 0;
				setLevel(mc, 1, 0); // back to checkpoint 1
				sweep("RESET!", "Back to checkpoint 1", 0xFFFF4040);
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 0.5F, 0.8F));
			}
		}
		// JAIL: opens by itself
		if (jailEnd > 0 && System.currentTimeMillis() >= jailEnd) {
			jailEnd = 0;
			server.execute(() -> release(server));
			sweep("FREE!", "", 0xFF55FF7F);
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.IRON_DOOR_OPEN, 1F, 0.8F));
		}
		friendTick(mc, server);
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
	private static void punch(Minecraft mc) {
		var p = mc.player;
		if (p == null) {
			return;
		}
		TikTokLiveMod m = mod();
		MinecraftServer server = mc.getSingleplayerServer();
		if (m != null && m.pkTargets.idx() != 0 && server != null) {
			double ks = m.pkPunchStrength.i() / 100.0;
			UUID me = p.getUUID();
			server.execute(() -> {
				for (ServerPlayer t : targets(server, me)) {
					Vec3 lk = t.getViewVector(1F);
					double x = -lk.x, z = -lk.z, l = Math.sqrt(x * x + z * z);
					if (l < 1e-4) {
						x = 1;
						z = 0;
						l = 1;
					}
					t.setDeltaMovement(x / l * 1.5 * ks, 0.55 * Math.min(1.5, ks), z / l * 1.5 * ks);
					t.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(t));
					String u = t.getStringUUID();
					command(server, "execute at " + u + " run playsound minecraft:block.note_block.bell master " + u + " ~ ~ ~ 1 1.2");
					command(server, "execute at " + u + " run playsound minecraft:entity.arrow.hit_player master " + u + " ~ ~ ~ 1 0.6");
					command(server, "execute at " + u + " run particle minecraft:crit ~ ~1 ~ 0.4 0.4 0.4 0.3 14");
				}
			});
			return;
		}
		double k = (m == null ? 100 : m.pkPunchStrength.i()) / 100.0;
		Vec3 look = p.getViewVector(1F);
		double hx = -look.x, hz = -look.z, len = Math.sqrt(hx * hx + hz * hz);
		if (len < 1e-4) {
			hx = 1;
			hz = 0;
			len = 1;
		}
		p.setDeltaMovement(hx / len * 1.5 * k, 0.55 * Math.min(1.5, k), hz / len * 1.5 * k);
		p.needsSync = true;
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2F, 1F));
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 1F, 0.6F));
		if (mc.level != null) {
			for (int i = 0; i < 14; i++) {
				mc.level.addParticle(ParticleTypes.CRIT, p.getX() + (Math.random() - 0.5), p.getY() + 1 + Math.random() * 0.6,
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
			sweep("NOT IN THE MAP", "Open Parkour Paradise 3 in Add-ons", 0xFFFF6B6B);
			return;
		}
		apply(a, 1, "Test");
		TikTokLiveMod m = TikTokLiveMod.instance;
		if (m != null) {
			m.playParkourSound(a); // your sound for this gift, like a real one
		}
	}

	// ================================================================ drawing
	public static void render(GuiGraphicsExtractor c) {
		TikTokLiveMod m = mod();
		if (m == null || !inMap) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (dev.solclient.compat.Mc.hideGui(mc)) {
			return;
		}
		int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
		long now = System.currentTimeMillis();
		drawSweep(c, w, h, now);
		if (resetEnd > 0) {
			drawReset(c, w, h, now);
		}
	}

	/** True for the parkour map's world on the built-in server: nothing in it may be broken or trampled. */
	public static boolean protects(net.minecraft.world.level.Level world) {
		try {
			return world instanceof net.minecraft.server.level.ServerLevel sw && sw.getServer().getScoreboard().getObjective("pk_level") != null;
		} catch (Throwable t) {
			return false;
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
	private static void drawGift(GuiGraphicsExtractor c, Act a, int x, int y, int s) {
		if (a == Act.SAVE) {
			// the watermelon picture shipped with the client: the same as on the stream, even before TikTok connects
			Identifier pic = savePicture();
			int pw = pic == WATERMELON ? s : savePicW, ph = pic == WATERMELON ? s : savePicH;
			// keeps the picture's shape: fits it inside the s x s square
			float k = Math.min(s / (float) Math.max(1, pw), s / (float) Math.max(1, ph));
			int dw = Math.max(1, Math.round(pw * k)), dh = Math.max(1, Math.round(ph * k));
			c.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, pic, x + (s - dw) / 2, y + (s - dh) / 2, 0, 0, dw, dh, dw, dh);
			return;
		}
		Identifier icon = null;
		TikTokLiveMod mm = TikTokLiveMod.instance;
		for (String name : (mm == null ? giftOf(a) : mm.pkGifts(a)).split("[,;]")) {
			if (icon == null && !name.isBlank()) {
				icon = TikGifts.icon(name.trim());
			}
		}
		if (icon != null) {
			c.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, s, s, s, s);
		} else if (a == Act.SAVE) {
			watermelon(c, x, y, s);
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
			var tr = Minecraft.getInstance().font;
			c.text(tr, Component.literal(g), x + s / 2 - tr.width(g) / 2, y + s / 2 - 4, 0xFFFFFFFF, true);
		}
		if (a != Act.PUNCH && a != Act.JAIL && s <= 20) {
			String n = String.valueOf(COUNT[a.ordinal()]);
			var tr = Minecraft.getInstance().font;
			c.pose().pushMatrix();
			c.pose().translate(x + s / 2F, y + s - 3);
			c.pose().scale(0.75F, 0.75F);
			c.text(tr, Component.literal(n).withStyle(st -> st.withBold(true)), -tr.width(n) / 2, 0, 0xFFFFFFFF, true);
			c.pose().popMatrix();
		}
	}

	/** A round watermelon slice (green rind, red, black seeds) until TikTok gives us the real picture. */
	private static void watermelon(GuiGraphicsExtractor c, int x, int y, int s) {
		float cx = x + s / 2F, cy = y + s / 2F, r = s / 2F;
		Gfx.circle(c, cx, cy, r, 0xFF2E8B3A);
		Gfx.circle(c, cx, cy, r * 0.86F, 0xFFB8E68A);
		Gfx.circle(c, cx, cy, r * 0.78F, 0xFFF0465A);
		for (int i = 0; i < 7; i++) {
			double an = i * Math.PI * 2 / 7 + 0.3;
			Gfx.circle(c, cx + (float) Math.cos(an) * r * 0.45F, cy + (float) Math.sin(an) * r * 0.45F, Math.max(1F, r * 0.07F), 0xFF1A1A1A);
		}
	}

	/** Text that slides in from the left, waits in the middle and slides out to the right, on a coloured band. */
	private static void drawSweep(GuiGraphicsExtractor c, int w, int h, long now) {
		long age = now - sweepAt;
		if (sweepTitle.isEmpty() || age > 2100) {
			return;
		}
		var tr = Minecraft.getInstance().font;
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
		Component t = Component.literal(sweepTitle).withStyle(st -> st.withBold(true));
		int tw = tr.width(t);
		float sc = 2.4F;
		float x = -tw * sc + (w / 2F + tw * sc / 2F) * ein + (w / 2F + tw * sc) * eout;
		c.pose().pushMatrix();
		c.pose().translate(x, cy - 10);
		c.pose().scale(sc, sc);
		c.text(tr, t, 1, 1, 0xC0000000, false);
		c.text(tr, t, 0, 0, sweepColour | 0xFF000000, false);
		c.pose().popMatrix();
		if (!sweepSub.isEmpty()) {
			int sw = tr.width(sweepSub);
			float sx = w - (w / 2F + sw / 2F) * ein - (w / 2F + sw) * eout; // comes from the other side
			c.text(tr, Component.literal(sweepSub), (int) sx, cy + 10, 0xFFFFFFFF, true);
		}
	}

	/** RESET + the seconds left + the SAVE gift, in the middle of the screen. */
	private static void drawReset(GuiGraphicsExtractor c, int w, int h, long now) {
		var tr = Minecraft.getInstance().font;
		long left = Math.max(0, resetEnd - now);
		int sec = (int) Math.ceil(left / 1000.0);
		int cx = w / 2, cy = (int) (h * 0.42);
		boolean hurry = sec <= 10;
		int red = hurry && (now / 250) % 2 == 0 ? 0xFFFF8080 : 0xFFFF3B3B;
		c.pose().pushMatrix();
		c.pose().translate(cx, cy - 30);
		c.pose().scale(1.6F, 1.6F);
		c.text(tr, Component.literal("RESET"), -tr.width("RESET") / 2, 0, red, true);
		c.pose().popMatrix();
		String n = String.valueOf(sec);
		c.pose().pushMatrix();
		c.pose().translate(cx, cy - 14);
		c.pose().scale(3.2F, 3.2F);
		c.text(tr, Component.literal(n), -tr.width(n) / 2, 0, 0xFFFFFFFF, true);
		c.pose().popMatrix();
		// the SAVE gift, gently bobbing
		int bob = (int) Math.round(Math.sin(now / 300.0) * 2);
		drawGift(c, Act.SAVE, cx - 20, cy + 16 + bob, 40);
	}
}
