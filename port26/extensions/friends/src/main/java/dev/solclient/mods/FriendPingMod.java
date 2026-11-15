package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.Keys;
import dev.solclient.Lang;
import dev.solclient.fx.Project;
import dev.solclient.fx.WorldDraw;
import dev.solclient.option.Option;
import dev.solclient.social.KuboLink;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Pings (like Apex / Valorant): press the key looking somewhere and your Sleep friends on the same server see a
 * marker there ("Look here", or "Enemy" when you look at a player / monster). Hold the key to pick the type.
 */
public class FriendPingMod extends Mod {

	public static FriendPingMod instance;
	private final Option.Key pingKey = key("Ping Key", 71); // G
	private final Option.Key dangerKey = key("Danger Ping Key", -1);
	private final Option.Choice who = choice("Show Pings From", 0, "Friends", "Every Sleep Player");
	private final Option.Bool sound = bool("Sound", true);
	private final Option.Num size = num("Marker Size", 100, 50, 200, 10, "%");
	private boolean prev, prevDanger;
	private long downAt;
	private int wheelSel = -1;
	private static boolean hooked;

	public FriendPingMod() {
		super("Pings", "friend_ping", "Mark places for your friends: press G and they see where you looked.", Category.UTILITY);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void onEnable() {
		KuboLink.wantPings = true;
	}

	@Override
	public void onDisable() {
		KuboLink.wantPings = false;
	}

	@Override
	public void init() {
		super.init();
		KuboLink.wantPings = enabled;
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
				if (instance != null && instance.enabled && !KuboLink.PINGS.isEmpty()) {
					try {
						instance.beams(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
					}
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Pings beams unavailable: " + t);
		}
	}

	private String dim() {
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	private boolean shown(KuboLink.Ping p) {
		return p.mine || who.idx() == 1 || KuboLink.isFriend(p.fromUuid, p.from);
	}

	@Override
	public void tick() {
		KuboLink.wantPings = true;
		boolean d = dev.solclient.compat.Mc.screen(mc) == null && Keys.down(pingKey.code());
		long now = System.currentTimeMillis();
		if (d && !prev) {
			downAt = now;
			wheelSel = -1;
		}
		if (!d && prev) {
			int type = wheelSel >= 0 ? wheelSel : autoType();
			place(type);
			wheelSel = -1;
		}
		prev = d;
		boolean dd = dev.solclient.compat.Mc.screen(mc) == null && Keys.down(dangerKey.code());
		if (dd && !prevDanger) {
			place(3);
		}
		prevDanger = dd;
	}

	/** While holding the key for a moment, moving the mouse picks a type on the little wheel. */
	private boolean wheelOpen() {
		return prev && System.currentTimeMillis() - downAt > 350L;
	}

	private int autoType() {
		HitResult hr = mc.hitResult;
		if (hr instanceof EntityHitResult eh && (eh.getEntity() instanceof Player || eh.getEntity() instanceof Monster)) {
			return 1;
		}
		return 0;
	}

	private void place(int type) {
		if (mc.player == null) {
			return;
		}
		Vec3 hit;
		if (mc.hitResult instanceof EntityHitResult eh) {
			hit = new Vec3(eh.getEntity().getX(), eh.getEntity().getY() + eh.getEntity().getBbHeight(), eh.getEntity().getZ());
		} else {
			HitResult hr = mc.player.pick(256, 1F, false);
			hit = hr.getLocation();
		}
		KuboLink.ping(hit.x, hit.y, hit.z, type);
		if (sound.on()) {
			mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.6F);
		}
	}

	/** A ping from someone else arrived. */
	public static void onIncoming(KuboLink.Ping p) {
		if (instance == null || !instance.enabled || !instance.shown(p)) {
			return;
		}
		var mc = instance.mc;
		if (instance.sound.on() && mc.player != null) {
			mc.player.playSound(p.type == 1 || p.type == 3 ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F,
					p.type == 1 ? 1.9F : 1.3F);
		}
	}

	private void beams(com.mojang.blaze3d.vertex.PoseStack m, net.minecraft.client.renderer.SubmitNodeCollector q, Vec3 cam) {
		WorldDraw.Lines l = new WorldDraw.Lines(cam);
		String d = dim();
		long now = System.currentTimeMillis();
		List<KuboLink.Ping> list;
		synchronized (KuboLink.PINGS) {
			list = new ArrayList<>(KuboLink.PINGS);
		}
		for (KuboLink.Ping p : list) {
			if (!d.equals(p.dim) || !shown(p)) {
				continue;
			}
			float life = Math.min(1F, (9000L - (now - p.at)) / 1000F);
			int col = Gfx.withAlpha(KuboLink.PING_COLOURS[p.type], Math.round(200 * Math.max(0, life)));
			l.line(p.x, p.y, p.z, p.x, p.y + 60, p.z, 0.08F, col, Gfx.withAlpha(col, 0));
		}
		l.draw(m, q);
	}

	/** HUD: markers with name, distance and who pinged; arrows at the screen edge for pings behind you. */
	public void draw(GuiGraphicsExtractor c) {
		if (mc.player == null) {
			return;
		}
		if (wheelOpen()) {
			drawWheel(c);
		}
		if (KuboLink.PINGS.isEmpty() || !Project.begin()) {
			return;
		}
		String d = dim();
		long now = System.currentTimeMillis();
		float ms = size.f() / 100F;
		List<KuboLink.Ping> list;
		synchronized (KuboLink.PINGS) {
			list = new ArrayList<>(KuboLink.PINGS);
		}
		for (KuboLink.Ping p : list) {
			if (!d.equals(p.dim) || !shown(p)) {
				continue;
			}
			long age = now - p.at;
			float in = Math.min(1F, age / 200F), out = Math.min(1F, (9000L - age) / 600F);
			float k = Math.max(0, Math.min(in, out));
			boolean on = Project.edge(p.x, p.y + 0.5, p.z, 24);
			int col = KuboLink.PING_COLOURS[p.type];
			var mm = c.pose();
			mm.pushMatrix();
			mm.translate(Project.sx, Project.sy);
			float pop = 1 + 0.4F * (float) Math.max(0, Math.sin(Math.min(1, age / 350F) * Math.PI));
			mm.scale(ms * pop, ms * pop);
			int a = Math.round(255 * k);
			// pulse ring
			float ring = (age % 1200) / 1200F;
			Gfx.circle(c, 0, 0, 6 + ring * 14, Gfx.withAlpha(col, Math.round(90 * (1 - ring) * k)));
			mm.pushMatrix();
			mm.rotate((float) Math.toRadians(45));
			c.fill(-7, -7, 7, 7, Gfx.withAlpha(0x0A0B10, a));
			c.fill(-5, -5, 5, 5, Gfx.withAlpha(col, a));
			mm.popMatrix();
			String sym = p.type == 1 ? "!" : p.type == 3 ? "⚠" : p.type == 2 ? "→" : p.type == 4 ? "★" : "•";
			Gfx.textRaw(c, sym, -Gfx.twRaw(sym) / 2, -4, Gfx.withAlpha(0xFFFFFF, a));
			if (on) {
				String name = Lang.t(KuboLink.PING_NAMES[p.type]) + "  " + Math.round(Project.dist) + "m";
				String by = p.mine ? Lang.tr("You", "Tu") : p.from;
				int w = Math.max(Gfx.tw(name), Gfx.twRaw(by)) + 10;
				Gfx.rrectFree(c, -w / 2F, -36, w, 22, 4, Gfx.withAlpha(0x0D0F14, Math.round(210 * k)));
				Gfx.rrectFree(c, -w / 2F, -15, w, 1, 0, Gfx.withAlpha(col, a));
				Gfx.text(c, name, -Gfx.tw(name) / 2, -34, Gfx.withAlpha(0xFFFFFF, a));
				Gfx.textRaw(c, by, -Gfx.twRaw(by) / 2, -24, Gfx.withAlpha(0xA0A6B4, a));
			}
			mm.popMatrix();
		}
	}

	private void drawWheel(GuiGraphicsExtractor c) {
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		int cx = sw / 2, cy = sh / 2;
		int n = KuboLink.PING_NAMES.length;
		float yawDelta = net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot() - downYaw());
		int sel = Math.floorMod(Math.round(yawDelta / 12F), n);
		wheelSel = sel;
		for (int i = 0; i < n; i++) {
			double a = Math.toRadians(-90 + i * 360.0 / n);
			int x = cx + (int) Math.round(Math.cos(a) * 46), y = cy + (int) Math.round(Math.sin(a) * 46);
			boolean s = i == sel;
			Gfx.circle(c, x, y, s ? 15 : 12, s ? KuboLink.PING_COLOURS[i] : 0xC80D0F14);
			String l = Lang.t(KuboLink.PING_NAMES[i]);
			Gfx.text(c, l, x - Gfx.tw(l) / 2, y + 17, s ? 0xFFFFFFFF : 0xFFA0A6B4);
		}
		Gfx.textC(c, Lang.tr("Turn to choose, let go to ping", "Roda a câmara para escolher, larga para marcar"), cx, cy + 80, 0xFFE6E8EE);
	}

	private float yawAtDown = Float.NaN;
	private long yawFor;

	private float downYaw() {
		if (yawFor != downAt) {
			yawFor = downAt;
			yawAtDown = mc.player.getYRot();
		}
		return yawAtDown;
	}
}
