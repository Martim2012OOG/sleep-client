package dev.solclient.video;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.solclient.mods.WatchPartyMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Watch Party: YouTube screens placed in the world. Who placed a screen sends its state (video, place, time, playing)
 * through the presence Worker; everyone on the same server with Watch Party on sees it and hears it (volume by
 * distance). Only YouTube video ids travel, never arbitrary links, so nobody can make you open other web pages.
 */
public final class VideoScreens {

	public static final class Screen {
		public final String owner; // uuid without dashes
		String vid, dim;
		double x, y, z;
		float yaw, w, h;
		double pos;
		boolean on;
		long base;
		// local playback
		Mcef.Browser browser;
		BrowserTexture tex;
		Identifier texId;
		String loadedVid;
		long nextSync;
		int bw, bh; // browser size, to detect quality changes
		double dur; // video length in seconds (reported by the page), 0 = unknown
		// live mode (the browser in the game cannot decode a live): ffmpeg decodes it, see LiveStream
		boolean live;
		volatile String liveUrl, liveFfmpeg, liveErr;
		volatile boolean liveBusy;
		boolean liveErrTold;
		FfVideo ff;
		LiveStream.Audio audio;
		AdMusic adMusic;

		Screen(String owner) {
			this.owner = owner;
		}

		double expected() {
			return on ? pos + (System.currentTimeMillis() - base) / 1000.0 : pos;
		}

		float h() {
			return h > 0 ? h : w * 9F / 16F;
		}
	}

	private static final Map<String, Screen> SCREENS = new HashMap<>();
	private static final Identifier WHITE = Identifier.of("minecraft", "textures/misc/white.png");
	private static final Pattern VID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
	private static final Pattern LINK = Pattern.compile("(?:youtu\\.be/|[?&]v=|/shorts/|/live/|/embed/)([A-Za-z0-9_-]{11})");
	private static int texCounter;

	private VideoScreens() {
	}

	private static MinecraftClient mc() {
		return MinecraftClient.getInstance();
	}

	private static String me() {
		UUID u = mc().getSession().getUuidOrNull();
		return u == null ? "me" : u.toString().replace("-", "");
	}

	/** YouTube video id from a link (youtube.com/watch?v=, youtu.be/, shorts, live) or a bare id; null if not YouTube. */
	public static String videoId(String s) {
		if (s == null) {
			return null;
		}
		s = s.trim();
		if (VID.matcher(s).matches()) {
			return s;
		}
		String low = s.toLowerCase(Locale.ROOT);
		if (!low.contains("youtube.com") && !low.contains("youtu.be")) {
			return null;
		}
		Matcher m = LINK.matcher(s);
		return m.find() ? m.group(1) : null;
	}

	private static String dim() {
		return mc().world == null ? "" : mc().world.getRegistryKey().getValue().toString();
	}

	// ------------------------------------------------------------------ my screen (commands)
	public static Screen mine() {
		return SCREENS.get(me());
	}

	private static void say(String msg, boolean error) {
		if (mc().inGameHud != null) {
			mc().inGameHud.getChatHud().addMessage(Text.empty().append(Text.literal("[Watch Party] ").formatted(Formatting.AQUA))
					.append(Text.literal(WatchPartyText.t(msg)).formatted(error ? Formatting.RED : Formatting.GRAY)));
		}
	}

	/** Puts a screen 4 blocks in front of you (or changes the video of yours). */
	public static void play(String link) {
		if (!WatchPartyMod.on()) {
			say("Turn on the Watch Party mod first (Right Shift > mods). The first time it downloads the video engine (~100 MB).", true);
			return;
		}
		if (link != null && link.contains("/live/")) {
			playLive(link);
			return;
		}
		String vid = videoId(link);
		if (vid == null) {
			say("That is not a YouTube link.", true);
			return;
		}
		var p = mc().player;
		if (p == null) {
			return;
		}
		Screen s = mine();
		if (s == null) {
			s = new Screen(me());
			SCREENS.put(s.owner, s);
			place(s);
			s.w = WatchPartyMod.instance.defaultWidth();
			s.h = s.w * 9F / 16F;
		}
		if (s.live) {
			s.live = false;
			releaseLive(s);
		}
		s.vid = vid;
		s.pos = 0;
		s.on = true;
		s.base = System.currentTimeMillis();
		broadcast(s);
		say("Playing on your screen. Friends with Sleep + Watch Party see it too.", false);
	}

	/**
	 * A YouTube live on your screen. The browser inside the game cannot play the video format lives use, so this decodes it with
	 * ffmpeg (the stream address comes from yt-dlp; both are fetched the first time). Only you see it.
	 */
	public static void playLive(String link) {
		if (!WatchPartyMod.on()) {
			say("Turn on the Watch Party mod first (Right Shift > mods). The first time it downloads the video engine (~100 MB).", true);
			return;
		}
		String vid = videoId(link);
		if (vid == null) {
			say("That is not a YouTube link.", true);
			return;
		}
		if (mc().player == null) {
			return;
		}
		Screen s = mine();
		if (s == null) {
			s = new Screen(me());
			SCREENS.put(s.owner, s);
			place(s);
			s.w = WatchPartyMod.instance.defaultWidth();
			s.h = s.w * 9F / 16F;
		}
		release(s);
		s.live = true;
		s.vid = vid;
		s.on = true;
		s.liveErr = null;
		s.liveErrTold = false;
		s.liveUrl = null;
		dev.solclient.social.Presence.sendScreen(null); // friends do not get a live (they would only see the error)
		say("Opening the live on your screen... the first time it gets the tools it needs, this can take a minute.", false);
		startLive(s);
	}

	private static void startLive(Screen s) {
		if (s.liveBusy) {
			return;
		}
		s.liveBusy = true;
		final String vid = s.vid;
		Thread t = new Thread(() -> {
			try {
				String ffm = dev.solclient.fightcam.Ffmpeg.findPath();
				if (ffm == null) {
					mc().execute(() -> say("Getting ffmpeg (one time)...", false));
					dev.solclient.fightcam.Ffmpeg.install();
					ffm = dev.solclient.fightcam.Ffmpeg.findPath();
				}
				if (ffm == null) {
					s.liveErr = "ffmpeg could not be found or downloaded.";
					return;
				}
				String[] err = new String[1];
				String url = LiveStream.resolve(vid, err);
				if (url == null) {
					s.liveErr = err[0] == null ? "Could not open this live." : err[0];
					return;
				}
				s.liveFfmpeg = ffm;
				s.liveUrl = url;
			} catch (Throwable e) {
				s.liveErr = "Could not open this live: " + e.getMessage();
			} finally {
				s.liveBusy = false;
			}
		}, "Sleep live resolve");
		t.setDaemon(true);
		t.start();
	}

	private static void releaseLive(Screen s) {
		if (s.ff != null) {
			s.ff.close();
			s.ff = null;
		}
		if (s.audio != null) {
			s.audio.close();
			s.audio = null;
		}
	}

	/** Called every tick for a live screen near you: starts the decoder once the address is known, keeps the sound level. */
	private static void tickLive(Screen s, double dist, double range) {
		if (s.liveErr != null) {
			if (!s.liveErrTold) {
				s.liveErrTold = true;
				say(s.liveErr, true);
			}
			return;
		}
		if (s.ff == null) {
			if (s.liveUrl != null) {
				// small picture and 24 fps on purpose: every frame is copied into the texture, the game must not lag
				int[] q = switch (WatchPartyMod.instance.resolution()[0]) {
					case 854 -> new int[] {640, 360};
					case 1920 -> new int[] {1280, 720};
					default -> new int[] {854, 480};
				};
				int w = q[0], h = Math.max(2, (Math.round(w * s.h() / Math.max(0.1F, s.w)) / 2) * 2);
				s.ff = new FfVideo(List.of("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5", "-i", s.liveUrl), "fps=24", w, h,
						Identifier.of("sol_client", "watch_party_live/" + (texCounter++)));
				if (!s.ff.start(s.liveFfmpeg)) {
					s.liveErr = "ffmpeg could not start.";
					releaseLive(s);
					return;
				}
				s.audio = new LiveStream.Audio();
				s.audio.start(s.liveFfmpeg, s.liveUrl);
			} else if (!s.liveBusy) {
				startLive(s);
			}
			return;
		}
		if (s.ff.failed()) {
			s.liveErr = "The live stopped or could not be read. Paste the link again to retry.";
			releaseLive(s);
			return;
		}
		if (s.audio != null) {
			s.audio.volume = s.on ? Math.max(0F, Math.min(1F, WatchPartyMod.instance.volume() / 100F * (float) Math.pow(Math.max(0, 1 - dist / range), 1.3))) : 0F;
		}
	}

	private static void place(Screen s) {
		var p = mc().player;
		float yaw = p.getYaw();
		double rad = Math.toRadians(yaw);
		double dx = -Math.sin(rad), dz = Math.cos(rad);
		s.x = p.getX() + dx * 4.0;
		s.y = Math.floor(p.getY()) + 0.5;
		s.z = p.getZ() + dz * 4.0;
		s.yaw = yaw;
		s.dim = dim();
	}

	// ------------------------------------------------------------------ corner tool (aim at blocks)
	private static boolean placing;
	private static final java.util.List<net.minecraft.util.hit.BlockHitResult> CORNERS = new java.util.ArrayList<>();

	public static boolean placing() {
		return placing;
	}

	/** Starts marking the 4 corners of a new screen on a wall. */
	public static void startPlacing() {
		if (!WatchPartyMod.on()) {
			say("Turn on the Watch Party mod first (Right Shift > mods). The first time it downloads the video engine (~100 MB).", true);
			return;
		}
		placing = true;
		CORNERS.clear();
		say("Aim at the 4 corner blocks of the screen on a wall and press " + WatchPartyMod.instance.keyName() + " on each one.", false);
	}

	public static void cancelPlacing() {
		if (placing) {
			placing = false;
			CORNERS.clear();
			say("Cancelled.", false);
		}
	}

	/** The screen under the crosshair (ray vs. the screen rectangle), or null. */
	private static Screen aimed() {
		MinecraftClient mc = mc();
		if (mc.player == null || mc.world == null) {
			return null;
		}
		Vec3d o = mc.player.getCameraPosVec(1F);
		Vec3d d = mc.player.getRotationVec(1F);
		String dm = dim();
		Screen best = null;
		double bt = 1e9;
		for (Screen s : SCREENS.values()) {
			if (s.vid == null || !dm.equals(s.dim) || !allowed(s)) {
				continue;
			}
			double rad = Math.toRadians(s.yaw);
			double nx = Math.sin(rad), nz = -Math.cos(rad), rx = -Math.cos(rad), rz = -Math.sin(rad);
			double den = d.x * nx + d.z * nz;
			if (Math.abs(den) < 1e-6) {
				continue;
			}
			double t = ((s.x - o.x) * nx + (s.z - o.z) * nz) / den;
			if (t <= 0 || t > 48 || t >= bt) {
				continue;
			}
			if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
					&& mc.crosshairTarget.getPos().distanceTo(o) + 0.1 < t) {
				continue; // a block is in front of the screen
			}
			double hx = o.x + d.x * t - s.x, hy = o.y + d.y * t - s.y, hz = o.z + d.z * t - s.z;
			double u = hx * rx + hz * rz;
			if (Math.abs(u) <= s.w / 2 + 0.08 && hy >= -0.08 && hy <= s.h() + 0.08) {
				bt = t;
				best = s;
			}
		}
		return best;
	}

	private static long lastOwnerMsg;

	/** Right click while aiming at a screen: pause / play (only the owner of the screen can). */
	public static void onClick() {
		if (placing || mc().player == null || mc().currentScreen != null) {
			return;
		}
		Screen s = aimed();
		if (s == null) {
			return;
		}
		if (s == mine()) {
			pause(s.on);
		} else {
			long nowMs = System.currentTimeMillis();
			if (nowMs - lastOwnerMsg > 6000L) {
				lastOwnerMsg = nowMs;
				say("Only the owner of this screen can pause it.", true);
			}
		}
	}

	/** Left / right arrow while aiming at your own screen: skip 10 seconds back / forward. */
	public static void onSeekKey(int dir) {
		if (placing || mc().player == null || mc().currentScreen != null) {
			return;
		}
		Screen s = mine();
		if (s == null || s.vid == null || aimed() != s) {
			return;
		}
		skip(dir * 10);
		say((dir > 0 ? "+10s" : "-10s") + "  (" + nowPlaying().replaceFirst("^youtu\\.be/\\S+\\s+", "") + ")", false);
	}

	/** The Watch Party key: mark a corner, or open the panel of your screen, or start a new screen. */
	public static void onKey() {
		MinecraftClient mc = mc();
		if (mc.player == null || mc.world == null) {
			return;
		}
		if (placing) {
			if (!(mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)
					|| hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) {
				say("Aim at a block.", true);
				return;
			}
			if (!hit.getSide().getAxis().isHorizontal()) {
				say("Screens go on walls: aim at the side of a block.", true);
				return;
			}
			if (!CORNERS.isEmpty() && CORNERS.get(0).getSide() != hit.getSide()) {
				say("All corners must be on the same wall (same side of the blocks).", true);
				return;
			}
			CORNERS.add(hit);
			if (CORNERS.size() < 4) {
				say("Corner " + CORNERS.size() + "/4 marked.", false);
				return;
			}
			finishPlacing();
			return;
		}
		if (mine() != null) {
			mc.setScreen(new WatchPartyScreen());
		} else {
			startPlacing();
		}
	}

	private static void finishPlacing() {
		var side = CORNERS.get(0).getSide();
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (var c : CORNERS) {
			var b = c.getBlockPos();
			minX = Math.min(minX, b.getX());
			minY = Math.min(minY, b.getY());
			minZ = Math.min(minZ, b.getZ());
			maxX = Math.max(maxX, b.getX());
			maxY = Math.max(maxY, b.getY());
			maxZ = Math.max(maxZ, b.getZ());
		}
		placing = false;
		CORNERS.clear();
		float w = side.getAxis() == net.minecraft.util.math.Direction.Axis.Z ? maxX - minX + 1 : maxZ - minZ + 1;
		float h = maxY - minY + 1;
		if (w > 32 || h > 32) {
			say("Too big: the most is 32 x 32 blocks.", true);
			return;
		}
		if (w < 1 || h < 1) {
			say("Too small.", true);
			return;
		}
		Screen s = mine();
		if (s == null) {
			s = new Screen(me());
			SCREENS.put(s.owner, s);
		}
		double out = 0.03;
		double cx = (minX + maxX + 1) / 2.0, cz = (minZ + maxZ + 1) / 2.0;
		switch (side) {
			case NORTH -> { cz = minZ - out; s.yaw = 0; }
			case SOUTH -> { cz = maxZ + 1 + out; s.yaw = 180; }
			case WEST -> { cx = minX - out; s.yaw = -90; }
			default -> { cx = maxX + 1 + out; s.yaw = 90; }
		}
		s.x = cx;
		s.y = minY;
		s.z = cz;
		s.w = w;
		s.h = h;
		s.dim = dim();
		release(s); // new shape: new browser size
		if (s.vid != null) {
			broadcast(s);
		}
		say("Screen ready (" + (int) w + " x " + (int) h + "). Paste a YouTube link.", false);
		mc().setScreen(new WatchPartyScreen());
	}

	/** Shown at the top of the HUD while marking corners. */
	public static void drawHud(net.minecraft.client.gui.DrawContext c) {
		if (!placing) {
			return;
		}
		String t = "Watch Party: aim at corner " + (CORNERS.size() + 1) + "/4 and press " + WatchPartyMod.instance.keyName() + "   (/kubo video cancel to stop)";
		int w = mc().textRenderer.getWidth(t);
		int x = mc().getWindow().getScaledWidth() / 2 - w / 2;
		c.fill(x - 6, 26, x + w + 6, 40, 0xA0000000);
		c.drawText(mc().textRenderer, t, x, 29, 0xFF7FD8FF, true);
	}

	public static void skip(double seconds) {
		Screen s = mine();
		if (s == null) {
			return;
		}
		s.pos = Math.max(0, s.expected() + seconds);
		s.base = System.currentTimeMillis();
		s.nextSync = 0;
		broadcast(s);
	}

	public static boolean playing() {
		Screen s = mine();
		return s != null && s.on;
	}

	public static String nowPlaying() {
		Screen s = mine();
		if (s == null || s.vid == null) {
			return null;
		}
		int t = (int) s.expected();
		return "youtu.be/" + s.vid + "  " + (t / 60) + ":" + String.format("%02d", t % 60) + (s.on ? "" : " (paused)");
	}

	public static void move() {
		startPlacing();
	}

	public static void pause(boolean pause) {
		Screen s = mine();
		if (s == null) {
			say("You have no screen.", true);
			return;
		}
		s.pos = s.expected();
		s.base = System.currentTimeMillis();
		s.on = !pause;
		s.nextSync = 0;
		broadcast(s);
	}

	public static void seek(String t) {
		Screen s = mine();
		if (s == null) {
			say("You have no screen.", true);
			return;
		}
		double sec;
		try {
			String[] parts = t.trim().split(":");
			sec = 0;
			for (String part : parts) {
				sec = sec * 60 + Double.parseDouble(part);
			}
		} catch (RuntimeException e) {
			say("Use seconds or mm:ss, e.g. /kubo video seek 1:30", true);
			return;
		}
		s.pos = Math.max(0, Math.min(86_000, sec));
		s.base = System.currentTimeMillis();
		s.nextSync = 0;
		broadcast(s);
	}

	public static void size(int w) {
		Screen s = mine();
		if (s == null) {
			say("You have no screen.", true);
			return;
		}
		float aspect = s.h() / Math.max(0.1F, s.w);
		s.w = Math.max(1, Math.min(32, w));
		s.h = Math.max(1, Math.min(32, s.w * aspect));
		broadcast(s);
	}

	public static void remove() {
		Screen s = SCREENS.remove(me());
		if (s != null) {
			release(s);
			dev.solclient.social.Presence.sendScreen(null);
			say("Screen removed.", false);
		}
	}

	private static void broadcast(Screen s) {
		if (s.live) {
			return;
		}
		dev.solclient.social.Presence.sendScreen(payload(s));
	}

	/** My screen as sent to the room right now (null = no screen). Used to carry Sleep party / ping data too. */
	public static JsonObject currentPayload() {
		Screen s = mine();
		return s != null && s.vid != null && !s.live ? payload(s) : null;
	}

	private static JsonObject payload(Screen s) {
		JsonObject o = new JsonObject();
		o.addProperty("v", s.vid);
		o.addProperty("d", s.dim);
		o.addProperty("x", Math.round(s.x * 100) / 100.0);
		o.addProperty("y", Math.round(s.y * 100) / 100.0);
		o.addProperty("z", Math.round(s.z * 100) / 100.0);
		o.addProperty("f", Math.round(net.minecraft.util.math.MathHelper.wrapDegrees(s.yaw) * 10) / 10.0);
		o.addProperty("w", s.w);
		o.addProperty("h", s.h());
		o.addProperty("p", Math.round(s.expected() * 10) / 10.0);
		o.addProperty("on", s.on);
		return o;
	}

	/** The server room (re)connected: tell it about my screen again. */
	public static void resendMine() {
		Screen s = mine();
		if (s != null && s.vid != null) {
			mc().execute(() -> broadcast(s));
		}
	}

	// ------------------------------------------------------------------ remote screens
	/** {"t":"screen","u","s":{...}|null,"now"} from the Worker (client thread). */
	public static void onRemote(String owner, JsonElement data, long workerNow) {
		if (owner == null || owner.equals(me())) {
			return;
		}
		JsonObject o = data != null && data.isJsonObject() ? data.getAsJsonObject() : null;
		if (o == null) {
			Screen old = SCREENS.remove(owner);
			if (old != null) {
				release(old);
			}
			return;
		}
		try {
			String vid = o.get("v").getAsString();
			if (!VID.matcher(vid).matches()) {
				return;
			}
			Screen s = SCREENS.computeIfAbsent(owner, Screen::new);
			s.vid = vid;
			s.dim = o.get("d").getAsString();
			s.x = o.get("x").getAsDouble();
			s.y = o.get("y").getAsDouble();
			s.z = o.get("z").getAsDouble();
			s.yaw = o.get("f").getAsFloat();
			s.w = Math.max(1F, Math.min(32F, o.get("w").getAsFloat()));
			s.h = o.has("h") ? Math.max(1F, Math.min(32F, o.get("h").getAsFloat())) : s.w * 9F / 16F;
			s.on = o.has("on") && o.get("on").getAsBoolean();
			double p = o.get("p").getAsDouble();
			long at = o.has("at") ? o.get("at").getAsLong() : workerNow;
			if (s.on && workerNow > 0 && at > 0) {
				p += Math.max(0, workerNow - at) / 1000.0;
			}
			s.pos = p;
			s.base = System.currentTimeMillis();
			s.nextSync = 0;
		} catch (RuntimeException ignored) {
		}
	}

	/** Left the server / the room closed: forget everybody else's screens. */
	public static void clearRemote() {
		mc().execute(() -> {
			String me = me();
			Iterator<Map.Entry<String, Screen>> it = SCREENS.entrySet().iterator();
			while (it.hasNext()) {
				Screen s = it.next().getValue();
				if (!s.owner.equals(me)) {
					release(s);
					it.remove();
				}
			}
		});
	}

	public static void clearAll() {
		for (Screen s : SCREENS.values()) {
			release(s);
		}
		SCREENS.clear();
	}

	private static boolean allowed(Screen s) {
		if (s.owner.equals(me())) {
			return true;
		}
		if (!WatchPartyMod.instance.friendsOnly()) {
			return true;
		}
		for (dev.solclient.social.Social.User u : dev.solclient.social.Social.friends) {
			if (u.mcUuid != null && u.mcUuid.replace("-", "").equalsIgnoreCase(s.owner)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ playback (client tick)
	public static void tick() {
		MinecraftClient mc = mc();
		if (mc.world == null || mc.player == null) {
			if (!SCREENS.isEmpty()) {
				clearAll();
			}
			return;
		}
		if (!WatchPartyMod.on()) {
			for (Screen s : SCREENS.values()) {
				release(s);
			}
			return;
		}
		String d = dim();
		double range = WatchPartyMod.instance.range();
		long now = System.currentTimeMillis();
		// at most 3 browsers (the nearest screens): each one decodes and uploads video, so more would multiply the cost
		List<Screen> eligible = new ArrayList<>();
		for (Screen s : SCREENS.values()) {
			double dist = Math.sqrt(mc.player.squaredDistanceTo(s.x, s.y + s.h() / 2, s.z));
			boolean want = s.vid != null && d.equals(s.dim) && dist < range + 8 && allowed(s);
			if (!want) {
				if ((s.browser != null || s.ff != null) && (dist > range + 16 || !d.equals(s.dim) || !allowed(s))) {
					release(s);
				}
				continue;
			}
			eligible.add(s);
		}
		eligible.sort((a, b) -> Double.compare(mc.player.squaredDistanceTo(a.x, a.y, a.z), mc.player.squaredDistanceTo(b.x, b.y, b.z)));
		for (int i = 3; i < eligible.size(); i++) {
			release(eligible.get(i));
		}
		for (Screen s : eligible.subList(0, Math.min(3, eligible.size()))) {
			double dist = Math.sqrt(mc.player.squaredDistanceTo(s.x, s.y + s.h() / 2, s.z));
			if (s.live) {
				tickLive(s, dist, range);
				continue;
			}
			int[] res = WatchPartyMod.instance.resolution();
			int bw = res[0], bh = Math.max(120, Math.min(1080, Math.round(bw * s.h() / Math.max(0.1F, s.w))));
			if (s.browser != null && (s.bw != bw || s.bh != bh)) {
				// quality changed: recreate the browser at the new size
				release(s);
			}
			if (s.browser == null) {
				Mcef.start();
				s.browser = Mcef.create(url(s.vid, s.expected()), bw, bh);
				s.bw = bw;
				s.bh = bh;
				s.loadedVid = s.vid;
				s.nextSync = now + 2500;
				continue;
			}
			if (!s.vid.equals(s.loadedVid)) {
				s.loadedVid = s.vid;
				s.dur = 0;
				s.browser.js("location.href=" + jsStr(url(s.vid, s.expected())) + ";");
				s.nextSync = now + 2500;
				continue;
			}
			s.dur = readDuration(s);
			adMusic(s, adPlaying(s), dist, range);
			if (now >= s.nextSync) {
				s.nextSync = now + 1000;
				float vol = WatchPartyMod.instance.volume() * (float) Math.pow(Math.max(0, 1 - dist / range), 1.3);
				s.browser.js(waitJs());
				s.browser.js("window.__kMus=" + WatchPartyMod.instance.adMusicOn() + ";document.documentElement.classList.toggle('kubo-music'," + WatchPartyMod.instance.adMusicOn() + ");");
				s.browser.js(String.format(Locale.ROOT, SYNC_JS, s.expected(), s.on ? "true" : "false", vol, bw >= 1900 ? "hd1080" : bw >= 1200 ? "hd720" : "large",
						WatchPartyMod.instance.captions() ? "true" : "false", WatchPartyMod.instance.debug() ? "true" : "false"));
			}
		}
	}

	/** The waiting screen of the ads (animated), made once inside the page; its text follows the Watch Party language. */
	private static String waitJs() {
		return WAIT_JS.replace("@T@", jsStr(WatchPartyText.t("Wait a Second While We Skip The Ads")));
	}

	private static final String WAIT_JS = """
			(function(){try{
			var el=document.getElementById('kubo-wait');
			if(!el){
			var st=document.createElement('style');st.id='kubo-wait-css';st.textContent=`
			#kubo-wait{position:fixed;inset:0;z-index:2147483647;display:none;overflow:hidden;align-items:center;justify-content:center;
			background:#000;font-family:sans-serif}
			html.kubo-wait #kubo-wait,html:has(.ad-showing) #kubo-wait{display:flex}
			html.kubo-wait #kubo-pause,html:has(.ad-showing) #kubo-pause{display:none!important}
			.kw-c{position:relative;display:flex;flex-direction:column;align-items:center;gap:3.2vh}
			.kw-ring{position:relative;width:15vh;height:15vh;border-radius:50%;background:conic-gradient(from 0deg,rgba(255,255,255,0),rgba(255,255,255,.95));animation:kwspin 1.5s linear infinite;
			-webkit-mask:radial-gradient(farthest-side,transparent calc(100% - 1.1vh),#000 calc(100% - 1.1vh));mask:radial-gradient(farthest-side,transparent calc(100% - 1.1vh),#000 calc(100% - 1.1vh))}
			@keyframes kwspin{to{transform:rotate(360deg)}}
			.kw-play{position:absolute;left:50%;top:50%;width:0;height:0;margin:-3.2vh 0 0 -1.8vh;border-left:6vh solid rgba(255,255,255,.92);border-top:3.2vh solid transparent;border-bottom:3.2vh solid transparent}
			.kw-t{color:#fff;font-weight:700;font-size:3.4vh;letter-spacing:.02em;text-align:center;text-shadow:0 .4vh 2vh rgba(0,0,0,.5);padding:0 6vw}
			.kw-cr{position:absolute;left:0;right:0;bottom:1.8vh;text-align:center;color:rgba(255,255,255,.30);font-size:2vh;letter-spacing:.05em;display:none}
			html.kubo-music .kw-cr{display:block}`;
			document.documentElement.appendChild(st);
			el=document.createElement('div');el.id='kubo-wait';
			el.innerHTML='<div class="kw-c"><div style="position:relative;width:15vh;height:15vh"><div class="kw-ring"></div><div class="kw-play"></div></div><div class="kw-t" id="kubo-wait-t"></div></div>'
			+'<div class="kw-cr">\u266a  massobeats - honey jam</div>';
			document.documentElement.appendChild(el);
			}
			var t=document.getElementById('kubo-wait-t');if(t&&t.textContent!==@T@){t.textContent=@T@;}
			}catch(e){}})();
			""";

	/** The page writes #kad=1 into its address while an ad plays. */
	private static boolean adPlaying(Screen s) {
		try {
			String u = s.browser == null ? null : s.browser.url();
			return u != null && u.contains("#kad=1");
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Plays the credited music while an ad is skipped (it fades out when the ad is over). */
	private static void adMusic(Screen s, boolean ad, double dist, double range) {
		boolean want = ad && s.on && WatchPartyMod.instance.adMusicOn();
		if (want) {
			if (s.adMusic == null) {
				s.adMusic = new AdMusic();
				s.adMusic.start();
			}
			float vol = WatchPartyMod.instance.volume() / 100F * (float) Math.pow(Math.max(0, 1 - dist / range), 1.3);
			s.adMusic.volume = Math.max(0F, Math.min(1F, vol * 0.8F));
		} else if (s.adMusic != null) {
			s.adMusic.stop();
			s.adMusic = null;
		}
	}

	/** Video length written by the page into its address (#kd=videoId:seconds); 0 if not known yet. */
	private static double readDuration(Screen s) {
		try {
			String u = s.browser == null ? null : s.browser.url();
			if (u == null) {
				return s.dur;
			}
			int i = u.indexOf("#kd=");
			if (i < 0) {
				return s.dur;
			}
			String[] parts = u.substring(i + 4).split(":");
			if (parts.length == 2 && parts[0].equals(s.vid)) {
				return Math.max(0, Double.parseDouble(parts[1]));
			}
		} catch (RuntimeException ignored) {
		}
		return s.dur;
	}

	/** Length of my video in seconds (0 = unknown). */
	public static double duration() {
		Screen s = mine();
		return s == null ? 0 : s.dur;
	}

	/** Where my video is now, in seconds. */
	public static double position() {
		Screen s = mine();
		return s == null ? 0 : s.expected();
	}

	/** Jumps my video to an exact second (used by the slider). */
	public static void seekTo(double sec) {
		Screen s = mine();
		if (s == null) {
			return;
		}
		s.pos = Math.max(0, s.dur > 0 ? Math.min(s.dur, sec) : sec);
		s.base = System.currentTimeMillis();
		s.nextSync = 0;
		broadcast(s);
	}

	private static String url(String vid, double pos) {
		// Do NOT use /embed/ here: YouTube refuses embeds that are opened directly (no Referer header) with
		// "Error 153 - Video player configuration error". The normal watch page always works; SYNC_JS hides
		// everything except the <video> element, so only the picture is rendered on the screen.
		return "https://www.youtube.com/watch?v=" + vid + "&t=" + (int) Math.max(0, pos) + "s";
	}

	private static String jsStr(String s) {
		return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
	}

	private static void release(Screen s) {
		releaseLive(s);
		if (s.adMusic != null) {
			s.adMusic.close();
			s.adMusic = null;
		}
		if (s.browser != null) {
			s.browser.close();
			s.browser = null;
		}
		if (s.texId != null) {
			try {
				mc().getTextureManager().destroyTexture(s.texId);
			} catch (RuntimeException ignored) {
			}
			s.texId = null;
			s.tex = null;
		}
		s.loadedVid = null;
	}

	/**
	 * Runs inside the YouTube page every second: accepts the cookie page, shows only the video (full page, black
	 * background), skips ads when possible, keeps the time in sync and sets the volume.
	 */
	private static final String SYNC_JS = "(function(p,on,vol,q,cc,dbg){try{"
			// YouTube's cookie page ("Before you continue"): say no (or yes if there is no "no") in any language, and submit the form if no button is found
			+ "if(location.hostname.indexOf('consent.')===0){"
			+ "var els=document.querySelectorAll('button,input[type=submit],[role=button]');var rej=null,acc=null;"
			+ "var rx=/reject|rejeitar|recusar|rechazar|refuser|ablehnen|rifiuta|afwijzen|odm[ií]tnout|odrzu[cć]|avvis|avsl[åa]|avvisa|afvis|hylk[äa][äa]|\\u03b1\\u03c0\\u03bf\\u03c1\\u03c1|\\u043e\\u0442\\u043a\\u043b\\u043e\\u043d|\\u62d2\\u7edd|\\u62d2\\u5426|\\uac70\\ubd80/i;"
			+ "var ax=/accept|aceitar|aceptar|accepter|akzeptieren|accetta|accepteren|zaakceptuj|godk[äa]nn|accept[ée]r|hyv[äa]ksy|\\u03b1\\u03c0\\u03bf\\u03b4\\u03bf\\u03c7|\\u043f\\u0440\\u0438\\u043d\\u044f\\u0442|\\u63a5\\u53d7|\\u540c\\u610f|\\ub3d9\\uc758/i;"
			+ "for(var i=0;i<els.length;i++){var t=(els[i].getAttribute('aria-label')||'')+' '+(els[i].textContent||'')+' '+(els[i].value||'');"
			+ "if(!rej&&rx.test(t)){rej=els[i];}if(!acc&&ax.test(t)){acc=els[i];}}"
			+ "var cb=rej||acc;"
			+ "if(cb){try{cb.click();}catch(e8){}try{['mousedown','mouseup','click'].forEach(function(n){cb.dispatchEvent(new MouseEvent(n,{bubbles:true,cancelable:true,view:window}));});}catch(e9){}}"
			+ "else{var cf=document.querySelector('form[action*=\"consent\"]');if(cf){try{cf.submit();}catch(e7){}}}"
			+ "return;}"
			// the page must never think it is hidden (YouTube / Chromium pause or throttle hidden pages)
			+ "try{Object.defineProperty(document,'hidden',{configurable:true,get:function(){return false;}});"
			+ "Object.defineProperty(document,'visibilityState',{configurable:true,get:function(){return 'visible';}});}catch(e0){}"
			+ "if(!window.__kV){window.__kV=1;document.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation();},true);}"
			// nothing is hidden with visibility/opacity any more: the video is just stretched over the page, controls are removed
			+ "if(!document.getElementById('kubo-css')){var s=document.createElement('style');s.id='kubo-css';"
			+ "s.textContent='html,body{background:#000!important;overflow:hidden!important}"
			+ "#masthead-container,#secondary,#comments,#related,#below,ytd-watch-metadata,ytd-merch-shelf-renderer,tp-yt-paper-dialog,"
			+ ".ytp-chrome-bottom,.ytp-chrome-top,.ytp-gradient-bottom,.ytp-gradient-top,.ytp-pause-overlay,.ytp-ce-element,.ytp-cards-teaser,"
			+ ".ytp-endscreen-content,.ytp-paid-content-overlay,.ytp-popup,.ytp-watermark,.annotation{display:none!important}"
			// YouTube loading/buffering layer: the spinner and the dark veil stay on screen even while the video plays, so hide every
			// player layer except the one holding the <video>
			+ "#movie_player>*:not(.html5-video-container):not(.ytp-caption-window-container){display:none!important}.ytp-caption-window-container{display:block!important;position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;z-index:2147483647!important;pointer-events:none!important}"
			+ ".ytp-spinner,.ytp-spinner-container,.ytp-bezel,.ytp-bezel-text-wrapper,.ytp-cued-thumbnail-overlay,.ytp-large-play-button,"
			+ ".ytp-offline-slate,.ytp-scrim,.ytp-player-content,.ytp-iv-player-content,.ytp-ce-covering-overlay,.ytp-autonav-endscreen,"
			+ ".html5-endscreen,.ytp-storyboard-framepreview,ytd-popup-container,tp-yt-iron-overlay-backdrop,.ytp-ad-overlay-container,"
			+ ".ytp-ad-player-overlay,.ytp-ad-persistent-progress-bar-container{display:none!important;visibility:hidden!important;opacity:0!important}"
			+ ".html5-video-container,.html5-video-player{filter:none!important;background:#000!important}"
			// black screen with a message while an ad plays (and while the video loads right after it)
			+ "html.kubo-wait #kubo-pause,html:has(.ad-showing) #kubo-pause{display:none!important}"
			+ "video{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;"
			+ "object-fit:contain!important;z-index:2147483646!important;background:#000!important}"
			+ "#kubo-dbg{position:fixed;left:6px;top:4px;z-index:2147483647;font:bold 16px monospace;color:#7CFC00;background:rgba(0,0,0,.6);padding:2px 6px}';"
			+ "document.documentElement.appendChild(s);}"
			+ "var pl=document.getElementById('movie_player');"
			// fixed quality = the one chosen in the mod settings; re-applied whenever YouTube changes it (never automatic)
			+ "if(pl&&pl.setPlaybackQualityRange&&pl.getPlaybackQuality&&pl.getPlaybackQuality()!==q){try{pl.setPlaybackQualityRange(q,q);}catch(e2){}try{pl.setPlaybackQuality(q);}catch(e3){}}"
			// captions: on/off follows the mod setting (YouTube resets them with every video, so it is checked every second)
			+ "if(pl&&pl.getOption&&pl.loadModule){var CK=window.__kC||(window.__kC={t:0});var nc=Date.now();"
			+ "var tr=null;try{tr=pl.getOption('captions','track');}catch(e9){}var hasTr=!!(tr&&tr.languageCode);"
			+ "if(cc&&!hasTr&&nc-CK.t>2500){CK.t=nc;try{pl.loadModule('captions');}catch(ea){}"
			+ "try{if(pl.toggleSubtitlesOn){pl.toggleSubtitlesOn();}}catch(eb){}"
			+ "try{var tl=pl.getOption('captions','tracklist');if(tl&&tl.length&&!(pl.getOption('captions','track')||{}).languageCode){pl.setOption('captions','track',tl[0]);}}catch(ec){}}"
			+ "else if(!cc&&hasTr){try{pl.unloadModule('captions');}catch(ed){}try{if(pl.toggleSubtitles){pl.toggleSubtitles();}}catch(ee){}}}"
			+ "var sk=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-ad-skip-button-container button,.ytp-skip-ad button,button[class*=\"skip-button\"]');"
			+ "if(sk){try{sk.click();}catch(e6){}try{['mousedown','mouseup','click'].forEach(function(n){sk.dispatchEvent(new MouseEvent(n,{bubbles:true,cancelable:true,view:window}));});}catch(e7){}}"
			+ "var v=document.querySelector('video');if(!v)return;"
			+ "var K=window.__kS||(window.__kS={t:-1,n:0,m:0});window.__kOn=on;"
			+ "if(!v.__kH){v.__kH=1;var op=v.pause.bind(v);v.pause=function(){if(window.__kAllow){op();}};"
			+ "v.addEventListener('pause',function(){if(window.__kOn&&!window.__kAllow&&!v.ended){var rr=v.play();"
			+ "if(rr&&rr.catch)rr.catch(function(){K.m=1;v.muted=true;var r9=v.play();if(r9&&r9.catch)r9.catch(function(){});});}});}"
			+ "v.volume=Math.max(0,Math.min(1,vol));v.muted=(vol<=0.001)||K.m===1||(!!window.__kMus&&!!document.querySelector('.ad-showing'));"
			// small status text on the screen (helps to see what is happening when a video freezes)
			+ "if(!window.__kF){window.__kF={c:0,f:0,t:performance.now()};(function lp(){window.__kF.c++;requestAnimationFrame(lp);})();}"
			+ "var F=window.__kF,nw=performance.now();if(nw-F.t>=900){F.f=Math.round(F.c*1000/(nw-F.t));F.c=0;F.t=nw;}"
			+ "if(dbg){var d=document.getElementById('kubo-dbg');if(!d){d=document.createElement('div');d.id='kubo-dbg';document.documentElement.appendChild(d);}"
			+ "d.textContent='cap='+(K.q||q)+' raf='+F.f+' ad='+(document.querySelector('.ad-showing')?1:0)+' t='+v.currentTime.toFixed(1)+' exp='+p.toFixed(1)+' rs='+v.readyState+' paused='+(v.paused?1:0)+' mute='+(v.muted?1:0)"
			+ "+' stall='+K.n+' q='+(pl&&pl.getPlaybackQuality?pl.getPlaybackQuality():'?')+' drop='+(v.getVideoPlaybackQuality?v.getVideoPlaybackQuality().droppedVideoFrames:'?');}else{var d0=document.getElementById('kubo-dbg');if(d0){d0.remove();}}"
			+ "var dq=v.getVideoPlaybackQuality?v.getVideoPlaybackQuality().droppedVideoFrames:0;if(K.d===undefined){K.d=dq;}var dd=dq-K.d;K.d=dq;"
			+ "var ad=document.querySelector('.ad-showing')?1:0;"
			// time lost in ads / loading right after them is not counted, so the video never jumps far ahead afterwards
			+ "var tn=Date.now();var dtk=K.lt?Math.min(3,(tn-K.lt)/1000):0;K.lt=tn;if(K.sk===undefined){K.sk=0;}"
			+ "if(ad){K.pst=1;}var busy=ad||(K.pst&&(v.seeking||v.readyState<3));"
			+ "if(on&&busy){K.sk+=dtk;}if(K.pst&&!busy){K.pst=0;}"
			+ "document.documentElement.classList.toggle('kubo-wait',!!busy);"
			// paused: dimmed picture with a big pause button in the middle
			+ "var kpd=document.getElementById('kubo-pause');if(!kpd){kpd=document.createElement('div');kpd.id='kubo-pause';"
			+ "kpd.style.cssText='position:fixed;left:0;top:0;right:0;bottom:0;z-index:2147483646;background:rgba(0,0,0,.45);align-items:center;justify-content:center;pointer-events:none;display:none';"
			+ "var kci=document.createElement('div');kci.style.cssText='width:26vh;height:26vh;border-radius:999px;background:rgba(190,190,190,.85);display:flex;align-items:center;justify-content:center;gap:3.2vh';"
			+ "for(var kbi=0;kbi<2;kbi++){var kb=document.createElement('div');kb.style.cssText='width:3.6vh;height:11vh;background:rgba(95,95,95,.95);border-radius:.4vh';kci.appendChild(kb);}"
			+ "kpd.appendChild(kci);document.documentElement.appendChild(kpd);}"
			+ "kpd.style.display=on?'none':'flex';"
			+ "if(!ad){K.a=0;}"
			+ "if(ad){window.__kDu=null;try{history.replaceState(history.state,'',location.pathname+location.search+'#kad=1');}catch(eg){}}"
			+ "if(ad){if(sk){K.a=(K.a||0)+1;if(K.a>=2&&isFinite(v.duration)&&v.duration>0){v.currentTime=Math.max(0,v.duration-0.1);}}"
			+ "if(on&&v.paused){if(pl&&pl.playVideo){try{pl.playVideo();}catch(e4){}}var ra=v.play();if(ra&&ra.catch)ra.catch(function(){});}return;}"
			// tell the game how long the video is (written into the page #hash, which the game reads back)
			+ "if(isFinite(v.duration)&&v.duration>0){var kdu=Math.round(v.duration);var kvi=new URLSearchParams(location.search).get('v')||'';"
			+ "if(window.__kDu!==kdu||window.__kDv!==kvi){window.__kDu=kdu;window.__kDv=kvi;"
			+ "try{history.replaceState(history.state,'',location.pathname+location.search+'#kd='+kvi+':'+kdu);}catch(ef){}}}"
			+ "if(v.seeking||v.readyState<3){if(on&&v.paused){var r0=v.play();if(r0&&r0.catch)r0.catch(function(){});}return;}"
			+ "var pe=Math.max(0,p-K.sk);var df=v.currentTime-pe;"
			+ "if(Math.abs(df)>5){v.currentTime=pe;}"
			+ "if(v.playbackRate!==1){v.playbackRate=1;}"
			+ "if(on&&v.paused){if(pl&&pl.playVideo){try{pl.playVideo();}catch(e5){}}var r=v.play();if(r&&r.catch)r.catch(function(){K.m=1;v.muted=true;var r2=v.play();if(r2&&r2.catch)r2.catch(function(){});});}else if(!on&&!v.paused){window.__kAllow=1;v.pause();window.__kAllow=0;}"
			+ "if(on&&!v.paused&&!v.ended){if(Math.abs(v.currentTime-K.t)<0.01){K.n++;}else{K.n=0;}K.t=v.currentTime;"
			+ "if(K.n===3){K.m=1;v.muted=true;var r3=v.play();if(r3&&r3.catch)r3.catch(function(){});}"
			+ "else if(K.n>=7){K.n=0;K.m=1;location.reload();}}else{K.n=0;K.t=v.currentTime;}"
			+ "}catch(e){}})(%.2f,%s,%.3f,'%s',%s,%s);";

	// ------------------------------------------------------------------ world rendering
	/** Draws the screens (called before entities, so they are depth-tested like everything else). */
	public static void render(MatrixStack matrices, net.minecraft.client.render.command.OrderedRenderCommandQueue queue, Vec3d cam) {
		if ((SCREENS.isEmpty() && CORNERS.isEmpty()) || !WatchPartyMod.on() || mc().world == null) {
			return;
		}
		String d = dim();
		double range = WatchPartyMod.instance.range() + 32;
		for (var c : CORNERS) {
			var b = c.getBlockPos();
			var n = c.getSide();
			matrices.push();
			matrices.translate(b.getX() + 0.5 + n.getOffsetX() * 0.505 - cam.x, b.getY() - cam.y, b.getZ() + 0.5 + n.getOffsetZ() * 0.505 - cam.z);
			float yaw = switch (n) { case NORTH -> 0; case SOUTH -> 180; case WEST -> -90; default -> 90; };
			double rad = Math.toRadians(yaw);
			float rx = (float) -Math.cos(rad), rz = (float) -Math.sin(rad), nx = (float) Math.sin(rad), nz = (float) -Math.cos(rad);
			queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(WHITE), (e, vc) -> quad(vc, e, rx, rz, nx, nz, 1F, 1F, 0, 0F, 0xFF3FC8FF));
			matrices.pop();
		}
		for (Screen s : SCREENS.values()) {
			if (s.vid == null || !d.equals(s.dim) || !allowed(s)) {
				continue;
			}
			double cx = s.x - cam.x, cy = s.y - cam.y, cz = s.z - cam.z;
			if (cx * cx + cy * cy + cz * cz > range * range) {
				continue;
			}
			boolean video = false;
			Identifier drawId = s.texId;
			if (s.live) {
				if (s.ff != null && s.ff.upload()) {
					video = true;
					drawId = s.ff.id();
				}
			} else if (s.browser != null) {
				var view = s.browser.texture();
				if (view != null) {
					if (s.tex == null) {
						s.tex = new BrowserTexture();
						s.texId = Identifier.of("sol_client", "watch_party/" + (texCounter++));
						mc().getTextureManager().registerTexture(s.texId, s.tex);
					}
					video = s.tex.set(view);
				}
			}
			double rad = Math.toRadians(s.yaw);
			float rx = (float) -Math.cos(rad), rz = (float) -Math.sin(rad); // the viewer's right
			float nx = (float) Math.sin(rad), nz = (float) -Math.cos(rad); // towards the viewer
			float w = s.w, h = s.h();
			matrices.push();
			matrices.translate(cx, cy, cz);
			final float frame = 0.08F;
			// black frame (slightly behind) and the picture
			queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(WHITE),
					(e, vc) -> quad(vc, e, rx, rz, nx, nz, w + frame * 2, h + frame * 2, -frame, -0.02F, 0xFF0B0C10));
			if (video) {
				// read the id only now: the browser's texture id is created a few lines above, the first time it has a picture
				final Identifier tid = s.live ? (s.ff != null ? s.ff.id() : null) : s.texId;
				if (tid == null) {
					matrices.pop();
					continue;
				}
				queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(tid),
						(e, vc) -> quad(vc, e, rx, rz, nx, nz, w, h, 0, 0F, 0xFFFFFFFF));
			}
			matrices.pop();
		}
	}

	/** Quad with bottom-centre at the origin (moved 'lift' down and 'push' along the normal). */
	private static void quad(VertexConsumer vc, MatrixStack.Entry e, float rx, float rz, float nx, float nz, float w, float h, float lift,
			float push, int color) {
		float hx = rx * w / 2, hz = rz * w / 2;
		float ox = nx * push, oz = nz * push;
		float y0 = lift, y1 = lift + h;
		int light = 0xF000F0;
		vc.vertex(e, -hx + ox, y0, -hz + oz).color(color).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, 0, nz);
		vc.vertex(e, hx + ox, y0, hz + oz).color(color).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, 0, nz);
		vc.vertex(e, hx + ox, y1, hz + oz).color(color).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, 0, nz);
		vc.vertex(e, -hx + ox, y1, -hz + oz).color(color).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, 0, nz);
	}
}
