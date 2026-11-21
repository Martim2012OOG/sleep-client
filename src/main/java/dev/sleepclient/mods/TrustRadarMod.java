package dev.sleepclient.mods;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.sleepclient.Lang;
import dev.sleepclient.anticheat.TrustEngine;
import dev.sleepclient.anticheat.TrustEngine.Check;
import dev.sleepclient.anticheat.TrustEngine.Profile;
import dev.sleepclient.gui.Notify;
import dev.sleepclient.option.Option;
import dev.sleepclient.social.TrustApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Trust Radar: tells you how much to trust the players around you. It watches how they move and fight (flying, speed, reach, aim,
 * auto clicker), gives each a trust %, warns you before a fight with someone suspicious and can share reports with the Sleep site so
 * the whole community gets a better picture. It is a guess, never proof: creative players, lag and knockback are ignored.
 * Commands: /suspect, /suspect &lt;player&gt;, /suspect reset.
 */
public class TrustRadarMod extends Mod {

	private static TrustRadarMod instance;

	private final Option.Choice strict = choice("Strictness", 0, "Careful", "Normal", "Strict");
	private final Option.Bool warn = bool("Warn Me Before A Fight", true).tip("A message when a player near you has a low trust %.");
	private final Option.Num warnAt = num("Warn Below", 55F, 20F, 85F, 5F, "% trust");
	private final Option.Bool share = bool("Share Reports With Sleep", true).tip("Sends only the player name and the numbers (no chat, no position) so everyone gets a better picture.");
	private final Option.Bool community = bool("Use Community Trust", true).tip("Also asks the Sleep site what other players found.");

	private final Map<UUID, Long> warned = new HashMap<>();
	private int clock;

	public TrustRadarMod() {
		super("Trust Radar", "trust_radar", "Shows how much you can trust the players around you. Type /suspect to see the list.", Category.UTILITY);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void init() {
		super.init();
		try {
			ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
				for (String root : new String[] {"suspect", "trust"}) {
					dispatcher.register(ClientCommandManager.literal(root)
							.executes(c -> {
								list();
								return 1;
							})
							.then(ClientCommandManager.literal("reset").executes(c -> {
								TrustEngine.clear();
								say(Text.literal(Lang.pt() ? "Lista limpa." : "List cleared.").formatted(Formatting.GRAY));
								return 1;
							}))
							.then(ClientCommandManager.argument("player", StringArgumentType.word()).executes(c -> {
								one(StringArgumentType.getString(c, "player"));
								return 1;
							})));
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] /suspect unavailable: " + t);
		}
	}

	private static void say(Text t) {
		var mc = net.minecraft.client.MinecraftClient.getInstance();
		if (mc.player != null) {
			mc.player.sendMessage(t, false);
		}
	}

	private static Formatting colour(int trust) {
		return trust >= 80 ? Formatting.GREEN : trust >= 55 ? Formatting.YELLOW : trust >= 35 ? Formatting.GOLD : Formatting.RED;
	}

	/** Mixes what this client saw with what the community says. */
	private static int combined(Profile p) {
		TrustApi.Community c = TrustApi.cached(p.id);
		int mine = p.trust();
		if (c == null || c.trust() < 0 || instance == null || !instance.community.on()) {
			return mine;
		}
		double w = Math.min(0.6, 0.15 * c.reporters());
		return (int) Math.round(mine * (1 - w) + c.trust() * w);
	}

	private static void list() {
		List<Profile> ps = new ArrayList<>(TrustEngine.all());
		ps.removeIf(p -> p.observedSeconds() < 5);
		ps.sort((a, b) -> Integer.compare(combined(a), combined(b)));
		boolean pt = Lang.pt();
		say(Text.literal(pt ? "Confiança dos jogadores à tua volta (mais suspeitos primeiro)" : "Trust of the players around you (most suspicious first)").formatted(Formatting.AQUA, Formatting.BOLD));
		if (ps.isEmpty()) {
			say(Text.literal(pt ? "Ainda ninguém observado. Fica uns segundos perto de outros jogadores." : "Nobody watched yet. Stay near other players for a few seconds.").formatted(Formatting.GRAY));
			return;
		}
		for (Profile p : ps.subList(0, Math.min(12, ps.size()))) {
			int t = combined(p);
			say(Text.literal(" " + t + "%  ").formatted(colour(t), Formatting.BOLD).append(Text.literal(p.name + "  ").formatted(Formatting.WHITE))
					.append(Text.literal(p.ping + " ms  " + summary(p)).formatted(Formatting.GRAY)));
		}
		say(Text.literal(pt ? "Usa /suspect <jogador> para ver os detalhes." : "Use /suspect <player> for the details.").formatted(Formatting.DARK_GRAY));
	}

	private static String summary(Profile p) {
		List<Check> f = p.flagged();
		if (f.isEmpty()) {
			return Lang.pt() ? "sem sinais" : "no signs";
		}
		StringBuilder sb = new StringBuilder();
		for (Check c : f) {
			sb.append(sb.length() == 0 ? "" : ", ").append(TrustEngine.label(c, Lang.pt()));
		}
		return sb.toString();
	}

	private static void one(String name) {
		boolean pt = Lang.pt();
		Profile p = TrustEngine.byName(name);
		if (p == null) {
			say(Text.literal(pt ? "Não vi esse jogador por aqui." : "I have not seen that player here.").formatted(Formatting.RED));
			return;
		}
		int t = combined(p);
		say(Text.literal(p.name + "  " + t + "% " + (pt ? "de confiança" : "trust")).formatted(colour(t), Formatting.BOLD));
		say(Text.literal(" Ping: " + p.ping + " ms   " + (pt ? "Observado: " : "Watched: ") + Math.round(p.observedSeconds()) + " s   "
				+ (pt ? "Modo: " : "Mode: ") + (p.mode == null ? "?" : p.mode.name().toLowerCase())).formatted(Formatting.GRAY));
		for (Check c : Check.values()) {
			int v = (int) Math.round(p.pts[c.ordinal()]);
			say(Text.literal("  " + TrustEngine.label(c, pt) + ": " + v + "%").formatted(v >= 40 ? Formatting.RED : v >= 12 ? Formatting.GOLD : Formatting.DARK_GRAY));
		}
		TrustApi.Community c = TrustApi.cached(p.id);
		if (c != null && c.trust() >= 0) {
			say(Text.literal(pt ? " Comunidade: " + c.trust() + "% (" + c.reporters() + " jogadores, " + c.reports() + " relatórios)"
					: " Community: " + c.trust() + "% (" + c.reporters() + " players, " + c.reports() + " reports)").formatted(Formatting.AQUA));
		} else {
			say(Text.literal(pt ? " Comunidade: sem relatórios" : " Community: no reports").formatted(Formatting.DARK_GRAY));
		}
		say(Text.literal(pt ? " É uma estimativa, não uma prova." : " This is an estimate, not proof.").formatted(Formatting.DARK_GRAY));
	}

	@Override
	public void tick() {
		TrustEngine.strictness = strict.idx() == 0 ? 0.35 : strict.idx() == 1 ? 0.7 : 1.1;
		TrustEngine.tick(mc);
		if (mc.player == null || mc.world == null || ++clock % 20 != 0) {
			return;
		}
		long now = System.currentTimeMillis();
		Map<UUID, String> ask = new LinkedHashMap<>();
		for (Profile p : TrustEngine.all()) {
			var e = mc.world.getPlayerByUuid(p.id);
			double dist = e == null ? 999 : e.distanceTo(mc.player);
			if (community.on() && dist < 64) {
				ask.put(p.id, p.name);
			}
			int t = combined(p);
			// share a report once the picture is clear enough
			if (share.on() && p.suspicion() >= 70 && p.observedSeconds() >= 60 && now - p.lastReportMs > 600_000L && p.suspicion() > p.lastReportScore + 10) {
				Map<String, Integer> cs = new LinkedHashMap<>();
				for (Check c : Check.values()) {
					cs.put(c.name(), (int) Math.round(p.pts[c.ordinal()]));
				}
				var info = mc.getCurrentServerEntry();
				TrustApi.report(p.id, p.name, p.trust(), (int) Math.round(p.suspicion()), cs, (int) p.observedSeconds(), p.ping, info == null ? "singleplayer" : info.address, "kubo");
				p.lastReportMs = now;
				p.lastReportScore = p.suspicion();
			}
			if (warn.on() && dist < 24 && t <= warnAt.get() && p.observedSeconds() >= 40 && now - warned.getOrDefault(p.id, 0L) > 180_000L) {
				warned.put(p.id, now);
				Notify.show(Notify.Kind.ERROR, (Lang.pt() ? "Cuidado: " : "Careful: ") + p.name + " " + t + "%",
						Lang.pt() ? "Pode estar a usar hacks (" + summary(p) + "). /suspect " + p.name : "May be using hacks (" + summary(p) + "). /suspect " + p.name);
			}
		}
		TrustApi.lookup(ask);
	}
}
