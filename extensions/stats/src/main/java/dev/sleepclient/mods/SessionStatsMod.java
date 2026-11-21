package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.Lang;
import dev.sleepclient.option.Option;
import dev.sleepclient.stats.KuboStats;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Session Stats: kills, deaths, K/D, time played, blocks broken and how far you walked this session. When you leave
 * the server you get a summary card, and "Copy" puts it in your clipboard to share.
 */
public class SessionStatsMod extends HudMod {

	public static SessionStatsMod instance;
	private final Option.Bool summary = bool("Summary When Leaving", true);
	private final Option.Bool compact = bool("Compact", false);
	private final Option.Num tileW = num("Box Width", 34, 12, 120, 1, "");
	private final Option.Num tileH = num("Box Height", 22, 8, 72, 1, "");
	private final Option.Num gap = num("Gap", 1, 0, 6, 0.5F, "");
	private final Option.Num radius = num("Corner Radius", 2, 0, 10, 0.5F, "");
	private final Option.Col bg = colour("Background Colour", dev.sleepclient.gui.KeyTiles.BG);
	private final Option.Col textCol = colour("Text Colour", dev.sleepclient.gui.KeyTiles.TEXT);
	private final Option.Col labelCol = colour("Label Colour", dev.sleepclient.gui.KeyTiles.LABEL);
	private int kills, deaths, hits, blocks;
	private double walked;
	private long start = System.currentTimeMillis();
	private double lx = Double.NaN, lz;
	private boolean wasDead;
	private final Map<Integer, Long> hitAt = new HashMap<>();
	private static boolean hooked;

	public SessionStatsMod() {
		super("Session Stats", "session_stats", "Kills, deaths, K/D, time, blocks and distance this session, with a summary card.", 0.01F, 0.55F);
		instance = this;
		action("Share this session", "Copy", () -> mc.keyboard.setClipboard(text()), () -> "");
		action("Start a new session", "Reset", this::reset, () -> "");
	}

	private void reset() {
		kills = deaths = hits = blocks = 0;
		walked = 0;
		start = System.currentTimeMillis();
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
			if (instance != null && instance.enabled) {
				instance.blocks++;
				KuboStats.add("blocks", 1);
			}
		});
		ClientPlayConnectionEvents.JOIN.register((h, s, c) -> c.execute(() -> {
			if (instance != null) {
				instance.reset();
			}
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(() -> {
			if (instance != null && instance.enabled && instance.summary.on() && System.currentTimeMillis() - instance.start > 60_000L) {
				dev.sleepclient.fightcam.ClipToast.show(Lang.tr("Session over", "Sessão terminada"), instance.shortLine(), null, false);
			}
			KuboStats.save();
		}));
	}

	public void onAttack(Entity e) {
		if (e instanceof LivingEntity) {
			hits++;
			hitAt.put(e.getId(), System.currentTimeMillis());
		}
	}

	@Override
	public void tick() {
		var p = mc.player;
		if (p == null || mc.world == null) {
			return;
		}
		KuboStats.add("seconds", 0.05);
		if (!Double.isNaN(lx)) {
			double d = Math.hypot(p.getX() - lx, p.getZ() - lz);
			if (d < 20) { // teleports don't count
				walked += d;
				KuboStats.add("walked", d);
			}
		}
		lx = p.getX();
		lz = p.getZ();
		boolean dead = p.isDead() || p.getHealth() <= 0;
		if (dead && !wasDead) {
			deaths++;
			KuboStats.add("deaths", 1);
		}
		wasDead = dead;
		long now = System.currentTimeMillis();
		// a kill = something you hit in the last 3 s just died
		hitAt.entrySet().removeIf(en -> {
			Entity e = mc.world.getEntityById(en.getKey());
			if (now - en.getValue() > 3000L) {
				return true;
			}
			if (e instanceof LivingEntity le && (le.isDead() || le.getHealth() <= 0)) {
				kills++;
				KuboStats.add("kills", 1);
				if (e instanceof net.minecraft.entity.player.PlayerEntity) {
					KuboStats.add("playerKills", 1);
				}
				return true;
			}
			return e == null;
		});
	}

	private String time() {
		long s = (System.currentTimeMillis() - start) / 1000;
		return s >= 3600 ? String.format("%dh %02dm", s / 3600, s / 60 % 60) : String.format("%dm %02ds", s / 60, s % 60);
	}

	private String kd() {
		return String.format("%.2f", kills / (double) Math.max(1, deaths));
	}

	private String dist() {
		return walked >= 1000 ? String.format("%.2f km", walked / 1000) : Math.round(walked) + " m";
	}

	private String shortLine() {
		return kills + " kills  ·  " + deaths + Lang.tr(" deaths  ·  ", " mortes  ·  ") + time();
	}

	private String text() {
		return "Sleep Client - " + Lang.tr("my session", "a minha sessão") + "\n" + Lang.tr("Time: ", "Tempo: ") + time() + "\nKills: " + kills + "  |  "
				+ Lang.tr("Deaths: ", "Mortes: ") + deaths + "  |  K/D: " + kd() + "\nHits: " + hits + "\n" + Lang.tr("Blocks broken: ", "Blocos partidos: ")
				+ blocks + "\n" + Lang.tr("Walked: ", "Andei: ") + dist();
	}

	// ------------------------------------------------------------------ HUD (Keystrokes look: small tiles)
	private int cols() {
		return 3;
	}

	private int rows() {
		return compact.on() ? 1 : 2;
	}

	@Override
	public int width() {
		return Math.round(tileW.f() * cols() + gap.f() * (cols() - 1));
	}

	@Override
	public int height() {
		return Math.round(tileH.f() * rows() + gap.f() * (rows() - 1));
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		String[][] cells = compact.on()
				? new String[][] {{Integer.toString(kills), "KILLS"}, {Integer.toString(deaths), Lang.tr("DEATHS", "MORTES")}, {kd(), "K/D"}}
				: new String[][] {{Integer.toString(kills), "KILLS"}, {Integer.toString(deaths), Lang.tr("DEATHS", "MORTES")}, {kd(), "K/D"},
						{time(), Lang.tr("TIME", "TEMPO")}, {Integer.toString(blocks), Lang.tr("BLOCKS", "BLOCOS")}, {dist(), Lang.tr("WALKED", "ANDADO")}};
		float tw = tileW.f(), th = tileH.f(), g = gap.f();
		for (int i = 0; i < cells.length; i++) {
			float x = (i % 3) * (tw + g), y = (i / 3) * (th + g);
			String v = cells[i][0];
			// long values (times, km) get a smaller font so they fit the box
			if (Gfx.twRaw(v) > tw - 4) {
				v = v.replace(" ", "");
			}
			dev.sleepclient.gui.KeyTiles.tile(c, x, y, tw, th, radius.f(), bg.c(), v, cells[i][1], textCol.c(), labelCol.c());
		}
	}
}
