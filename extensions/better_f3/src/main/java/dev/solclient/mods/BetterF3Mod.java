package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

/**
 * Better F3: when you press F3 you get clean cards (position, world, performance, what you look at) instead of
 * Minecraft's wall of text (turn "Replace Minecraft's F3" off to see both).
 */
public class BetterF3Mod extends Mod {

	public static BetterF3Mod instance;
	private final Option.Bool replace = bool("Replace Minecraft's F3", true);
	private final Option.Bool showPerf = bool("Performance Card", true);
	private final Option.Bool showWorld = bool("World Card", true);
	private final Option.Bool showTarget = bool("Looking At Card", true);
	private final Option.Col accent = colour("Accent", 0xFF7DD3FC);
	private double lastX, lastZ;
	private long lastT;
	private float speed;

	public BetterF3Mod() {
		super("Better F3", "better_f3", "A clean, readable F3 screen with only what matters.", Category.UTILITY);
		instance = this;
	}

	public static boolean replacesVanilla() {
		return instance != null && instance.enabled && instance.replace.on();
	}

	public static boolean active() {
		var mc = net.minecraft.client.MinecraftClient.getInstance();
		return instance != null && instance.enabled && mc.getDebugHud().shouldShowDebugHud();
	}

	private record Row(String k, String v, int col) {
	}

	private int card(DrawContext c, int x, int y, String title, List<Row> rows, boolean right) {
		int w = 0;
		for (Row r : rows) {
			w = Math.max(w, Gfx.tw(r.k) + Gfx.twRaw(r.v) + 22);
		}
		w = Math.max(w, Gfx.tw(title) + 20);
		int h = 18 + rows.size() * 11 + 5;
		int cx = right ? x - w : x;
		Gfx.rrectFree(c, cx, y, w, h, 6, 0xC80D0F14);
		Gfx.rrectFree(c, cx, y, 3, h, 1.5F, accent.c());
		Gfx.text(c, title.toUpperCase(), cx + 9, y + 5, accent.c());
		int ry = y + 18;
		for (Row r : rows) {
			Gfx.text(c, r.k, cx + 9, ry, 0xFF8C90A0);
			Gfx.textRaw(c, r.v, cx + w - 8 - Gfx.twRaw(r.v), ry, r.col);
			ry += 11;
		}
		return h + 6;
	}

	public void draw(DrawContext c) {
		if (mc.player == null || mc.world == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastT > 250) {
			double dx = mc.player.getX() - lastX, dz = mc.player.getZ() - lastZ;
			speed = lastT == 0 ? 0 : (float) (Math.sqrt(dx * dx + dz * dz) / ((now - lastT) / 1000.0));
			lastX = mc.player.getX();
			lastZ = mc.player.getZ();
			lastT = now;
		}
		int sw = mc.getWindow().getScaledWidth();
		BlockPos bp = mc.player.getBlockPos();
		int white = 0xFFFFFFFF;
		// ---- left: position
		List<Row> pos = new ArrayList<>();
		pos.add(new Row("X", String.format("%.1f", mc.player.getX()), 0xFFFF8A8A));
		pos.add(new Row("Y", String.format("%.1f", mc.player.getY()), 0xFF8AFF9E));
		pos.add(new Row("Z", String.format("%.1f", mc.player.getZ()), 0xFF8AB8FF));
		String[] dirs = {"South (+Z)", "West (-X)", "North (-Z)", "East (+X)"};
		float yaw = net.minecraft.util.math.MathHelper.wrapDegrees(mc.player.getYaw());
		int di = Math.floorMod(Math.round(yaw / 90F), 4);
		pos.add(new Row(Lang.tr("Facing", "Direção"), Lang.t(dirs[di]) + String.format("  %.0f / %.0f", yaw, mc.player.getPitch()), white));
		pos.add(new Row("Chunk", (bp.getX() >> 4) + ", " + (bp.getZ() >> 4) + "   (" + (bp.getX() & 15) + ", " + (bp.getZ() & 15) + ")", white));
		pos.add(new Row(Lang.tr("Speed", "Velocidade"), String.format("%.1f m/s", speed), white));
		int y = 6;
		y += card(c, 6, y, Lang.tr("Position", "Posição"), pos, false);
		if (showWorld.on()) {
			List<Row> w = new ArrayList<>();
			String biome = mc.world.getBiome(bp).getKey().map(k -> k.getValue().getPath()).orElse("?");
			w.add(new Row("Biome", biome.replace('_', ' '), 0xFF8AFF9E));
			w.add(new Row(Lang.tr("Dimension", "Dimensão"), mc.world.getRegistryKey().getValue().getPath().replace('_', ' '), white));
			int bl = mc.world.getLightLevel(LightType.BLOCK, bp), sl = mc.world.getLightLevel(LightType.SKY, bp);
			w.add(new Row(Lang.tr("Light", "Luz"), bl + " block  ·  " + sl + " sky", bl < 1 ? 0xFFFF8A8A : white));
			long day = mc.world.getTimeOfDay() / 24000L, tod = mc.world.getTimeOfDay() % 24000L;
			int hh = (int) ((tod / 1000 + 6) % 24), mm = (int) (tod % 1000 * 60 / 1000);
			w.add(new Row(Lang.tr("Day", "Dia"), (day + 1) + String.format("   %02d:%02d", hh, mm), white));
			w.add(new Row(Lang.tr("Entities", "Entidades"), Integer.toString(mc.world.getRegularEntityCount()), white));
			y += card(c, 6, y, Lang.tr("World", "Mundo"), w, false);
		}
		// ---- right: performance + target
		int ry = 6;
		if (showPerf.on()) {
			List<Row> p = new ArrayList<>();
			int fps = mc.getCurrentFps();
			p.add(new Row("FPS", Integer.toString(fps), fps >= 60 ? 0xFF8AFF9E : fps >= 30 ? 0xFFFFE08A : 0xFFFF8A8A));
			var nh = mc.getNetworkHandler();
			var e = nh == null ? null : nh.getPlayerListEntry(mc.player.getUuid());
			int ping = e == null ? 0 : e.getLatency();
			p.add(new Row("Ping", mc.isInSingleplayer() ? "-" : ping + " ms", ping < 80 ? 0xFF8AFF9E : ping < 160 ? 0xFFFFE08A : 0xFFFF8A8A));
			Runtime r = Runtime.getRuntime();
			long used = (r.totalMemory() - r.freeMemory()) >> 20, max = r.maxMemory() >> 20;
			p.add(new Row(Lang.tr("Memory", "Memória"), used + " / " + max + " MB  (" + used * 100 / Math.max(1, max) + "%)", used * 100 / Math.max(1, max) > 85 ? 0xFFFF8A8A : white));
			p.add(new Row("Java", System.getProperty("java.version", "?"), white));
			p.add(new Row("CPU", Runtime.getRuntime().availableProcessors() + " threads", white));
			ry += card(c, sw - 6, ry, Lang.tr("Performance", "Desempenho"), p, true);
		}
		if (showTarget.on()) {
			HitResult hr = mc.crosshairTarget;
			List<Row> t = new ArrayList<>();
			if (hr instanceof BlockHitResult b && hr.getType() == HitResult.Type.BLOCK) {
				var st = mc.world.getBlockState(b.getBlockPos());
				t.add(new Row(Lang.tr("Block", "Bloco"), st.getBlock().getName().getString(), white));
				t.add(new Row("ID", net.minecraft.registry.Registries.BLOCK.getId(st.getBlock()).toString(), 0xFF8C90A0));
				BlockPos tp = b.getBlockPos();
				t.add(new Row(Lang.tr("At", "Em"), tp.getX() + " " + tp.getY() + " " + tp.getZ(), white));
				t.add(new Row(Lang.tr("Side", "Lado"), b.getSide().asString(), white));
			} else if (hr instanceof net.minecraft.util.hit.EntityHitResult eh) {
				t.add(new Row(Lang.tr("Entity", "Entidade"), eh.getEntity().getName().getString(), white));
				if (eh.getEntity() instanceof net.minecraft.entity.LivingEntity le) {
					t.add(new Row(Lang.tr("Health", "Vida"), String.format("%.1f / %.0f", le.getHealth(), le.getMaxHealth()), 0xFFFF8A8A));
				}
				t.add(new Row(Lang.tr("Distance", "Distância"), String.format("%.2f", Math.sqrt(mc.player.squaredDistanceTo(eh.getEntity()))), white));
			}
			if (!t.isEmpty()) {
				card(c, sw - 6, ry, Lang.tr("Looking at", "A olhar para"), t, true);
			}
		}
	}
}
