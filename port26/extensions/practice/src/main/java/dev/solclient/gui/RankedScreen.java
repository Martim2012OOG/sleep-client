package dev.solclient.gui;

import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.addons.pvp.BotDifficulty;
import dev.solclient.addons.pvp.PracticeKit;
import dev.solclient.addons.pvp.PracticeSession;
import dev.solclient.addons.pvp.PracticeWorld;
import dev.solclient.addons.ranked.Ranked;
import dev.solclient.social.Social;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Ranked PvP menu: Ranked queue (1v1 / 2v2), Duel a friend (first to 1-10, not ranked), Bots (practice, not ranked),
 * Rankings (a podium + tier list per kit, or Overall = tier points over every kit) and your own tiers.
 */
public class RankedScreen extends Screen {

	/** Kit id of the Overall leaderboard (the site adds up the tier points of every kit). */
	private static final String OVERALL = "overall";

	private final Screen parent;
	/** Drawn inside the Sleep menu's panel: no own window, fills the area it gets. */
	private final boolean embedded;
	private final Map<String, Float> hover = new HashMap<>();
	private int tab;
	private String mode = "1v1", kit = "sword";
	private String teammate, opponent1, opponent2;
	private String rankKit = OVERALL, rankMode = "1v1";
	private int firstTo = 3, botDiff = 1, bots = 1;
	private long openedAt = System.currentTimeMillis(), rankAt = System.currentTimeMillis();
	private float scroll;
	private String tip;
	private int tipX, tipY;

	private record Hit(int x, int y, int w, int h, Runnable run) {
	}

	private final List<Hit> hits = new ArrayList<>();
	private static final Map<String, ItemStack> ICONS = new HashMap<>();

	public RankedScreen(Screen parent) {
		this(parent, false);
	}

	public RankedScreen(Screen parent, boolean embedded) {
		super(Component.literal("Ranked PvP"));
		this.parent = parent;
		this.embedded = embedded;
		Ranked.loadRankings(rankKit, rankMode);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		dev.solclient.compat.Mc.setScreen(Minecraft.getInstance(), parent);
	}

	private static ItemStack icon(String item) {
		return ICONS.computeIfAbsent(item, i -> {
			try {
				return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(i)));
			} catch (Exception e) {
				return ItemStack.EMPTY;
			}
		});
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Smooth 0..1 hover amount per element (like the Sleep menu). */
	private float anim(String key, boolean on) {
		float v = hover.getOrDefault(key, 0F);
		v += ((on ? 1F : 0F) - v) * 0.35F;
		hover.put(key, v);
		return v;
	}

	/** A small toggle button: the same neutral / pressed button as the Sleep menu. */
	private void chip(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h, String label, boolean sel, Runnable run) {
		boolean hov = in(mx, my, x, y, w, h);
		KuboButton.bg(c, x, y, w, h, anim("c" + x + "," + y, hov), sel);
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 8) / 2, sel ? 0xFFFFFFFF : Gfx.blend(Theme.DIM, Theme.TEXT, 0.6F + 0.4F * anim("t" + x + "," + y, hov)));
		if (run != null) {
			hits.add(new Hit(x, y, w, h, run));
		}
	}

	/** Main buttons: the same as the mods' "Install" / "Installed" buttons. */
	private void bigButton(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, int h, String label, int col, Runnable run) {
		boolean hov = in(mx, my, x, y, w, h);
		if (col == Theme.accent()) {
			KuboButton.primary(c, x, y, w, h, label, hov);
		} else if (col == DISABLED) {
			KuboButton.neutral(c, x, y, w, h, label, hov);
		} else {
			KuboButton.draw(c, x, y, w, h, label, hov, col, Gfx.blend(col, 0xFFFFFFFF, 0.12F), 0xFFFFFFFF);
		}
		hits.add(new Hit(x, y, w, h, run));
	}

	private static final int DISABLED = 0xFF3A3D49;

	/** Tabs like the mods page ("All Mods | Mods | Extensions"): one bar, a sliding highlight on the open tab. */
	private void tabs(GuiGraphicsExtractor c, int mx, int my, int x, int y, String[] names) {
		int[] ws = new int[names.length];
		int total = 0;
		for (int i = 0; i < names.length; i++) {
			ws[i] = Gfx.tw(names[i]) + 18;
			total += ws[i] + (i > 0 ? 4 : 0);
		}
		Gfx.rrect(c, x - 2, y - 2, total + 4, 24, 9, Theme.ROW);
		int tx = x;
		for (int i = 0; i < names.length; i++) {
			if (i == tab) {
				SlideBg.draw(c, "rankedTab", tx, y, ws[i], 20, 7F, SlideBg.colour(), true);
			}
			tx += ws[i] + 4;
		}
		tx = x;
		for (int i = 0; i < names.length; i++) {
			boolean hov = in(mx, my, tx, y, ws[i], 20);
			Gfx.textFit(c, names[i], tx + ws[i] / 2, ws[i] - 6, y + 6, i == tab ? 0xFFFFFFFF : hov ? Theme.TEXT : Theme.DIM);
			final int t = i;
			hits.add(new Hit(tx, y, ws[i], 20, () -> {
				tab = t;
				scroll = 0;
				openedAt = System.currentTimeMillis() - 150;
				rankAt = System.currentTimeMillis();
			}));
			tx += ws[i] + 4;
		}
	}

	private static void tierBadge(GuiGraphicsExtractor c, int x, int y, String tier) {
		int col = Ranked.tierColour(tier);
		int tw = Gfx.tw(tier) + 8;
		Gfx.rrect(c, x, y, tw, 11, 4, Gfx.withAlpha(col & 0xFFFFFF, 0x40));
		Gfx.rrectBorder(c, x, y, tw, 11, 4, 0, col);
		Gfx.text(c, tier, x + 4, y + 2, col);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mx, int my, float delta) {
		super.extractRenderState(c, mx, my, delta);
		hits.clear();
		tip = null;
		float k = Math.min(1F, (System.currentTimeMillis() - openedAt) / 220F);
		int w, h, x, y, ix, iw;
		if (embedded) {
			// inside the Sleep menu's panel: use all of it, the menu already shows the title and "< Back"
			w = width;
			h = height;
			x = 0;
			y = Math.round((1F - k) * 6F) - 22;
			ix = 6;
			iw = w - 12;
		} else {
			w = Math.min(width - 16, 520);
			h = Math.min(height - 16, 330);
			x = (width - w) / 2;
			y = (height - h) / 2 + Math.round((1F - k) * 12F);
			Gfx.rrect(c, x, y, w, h, 10, Theme.PANEL);
			ix = x + 14;
			iw = w - 28;
			Gfx.text(c, "Ranked PvP", ix, y + 10, Theme.TEXT);
		}
		if (Ranked.siteMissing) {
			Gfx.text(c, Lang.tr("The Sleep site doesn't have Ranked PvP yet (Lovable prompt).", "O site do Sleep ainda não tem o Ranked PvP (prompt do Lovable)."), ix, y + h - 14, 0xFFFF8A8A);
		}
		String[] tabs = {"Ranked", Lang.tr("Duel", "Duelo"), "Bots", "Leaderboard", Lang.tr("My tiers", "Os meus tiers")};
		tabs(c, mx, my, ix + 2, y + 28, tabs);
		String hint = Lang.tr("HT1 is the best tier, LT5 the lowest", "HT1 é o melhor tier, LT5 o mais baixo");
		if (iw > 520) {
			Gfx.text(c, hint, ix + iw - Gfx.tw(hint), y + 34, Theme.DIM);
		}
		int cy = y + 58;
		switch (tab) {
			case 0 -> ranked(c, mx, my, ix, iw, cy);
			case 1 -> duel(c, mx, my, ix, iw, cy);
			case 2 -> botsTab(c, mx, my, ix, iw, cy);
			case 3 -> rankings(c, mx, my, ix, iw, cy, y + h);
			default -> profile(c, mx, my, ix, iw, cy);
		}
		// invites, on every tab
		int iy = y + h - 30;
		for (Ranked.Invite inv : Ranked.invites) {
			Gfx.rrect(c, ix, iy, iw, 22, 6, 0xFF2A2440);
			String txt = inv.fromName() + ("team".equals(inv.kind()) ? Lang.tr(" wants you as teammate: ", " quer-te como colega: ") : Lang.tr(" challenges you: ", " desafia-te: "))
					+ inv.mode() + " " + Ranked.kit(inv.kit()).name() + ("duel".equals(inv.kind()) ? Lang.tr(" · first to ", " · primeiro a ") + inv.firstTo() : "");
			Gfx.text(c, txt, ix + 8, iy + 7, 0xFFFFFFFF);
			bigButton(c, mx, my, ix + iw - 118, iy + 3, 54, 16, Lang.tr("Accept", "Aceitar"), 0xFF2E9E5B, () -> Ranked.answer(inv, true));
			bigButton(c, mx, my, ix + iw - 60, iy + 3, 54, 16, Lang.tr("Decline", "Recusar"), 0xFF8A2E3A, () -> Ranked.answer(inv, false));
			break; // one at a time
		}
		if (tip != null) {
			int pw = Gfx.tw(tip) + 10;
			int px = Math.max(2, Math.min(width - pw - 2, tipX - pw / 2));
			Gfx.rrect(c, px, tipY - 16, pw, 14, 4, 0xF0101218);
			Gfx.text(c, tip, px + 5, tipY - 13, 0xFFFFFFFF);
		}
	}

	// ---------------------------------------------------------------- pickers
	/** All kits as item tiles (2 rows). Returns the y under the grid. */
	private int kitGrid(GuiGraphicsExtractor c, int mx, int my, int ix, int iw, int cy, String sel, boolean overall, java.util.function.Consumer<String> pick) {
		int n = Ranked.KITS.size() + (overall ? 1 : 0);
		int size = 24, gap = 4;
		int cols = Math.max(1, Math.min(n, (iw + gap) / (size + gap)));
		if (cols < n && cols > (n + 1) / 2) {
			cols = (n + 1) / 2; // two even rows
		}
		long t = System.currentTimeMillis();
		for (int i = 0; i < n; i++) {
			String id, name, item;
			if (overall && i == 0) {
				id = OVERALL;
				name = Lang.tr("Overall", "Geral");
				item = "nether_star";
			} else {
				Ranked.Kit kk = Ranked.KITS.get(i - (overall ? 1 : 0));
				id = kk.id();
				name = kk.name();
				item = kk.icon();
			}
			int x = ix + (i % cols) * (size + gap), y = cy + (i / cols) * (size + gap);
			boolean s = id.equals(sel), hov = in(mx, my, x, y, size, size);
			float a = anim("k" + id + y, hov);
			int bg = s ? Gfx.blend(Theme.TILE, Theme.accent() | 0xFF000000, 0.28F) : Gfx.blend(Theme.TILE, Theme.TILE_HOVER, a);
			Gfx.rrectBorder(c, x, y, size, size, 6, bg, s ? Theme.accent() : Gfx.blend(Theme.CARD_BORDER, Theme.accent(), a));
			float bob = s ? (float) Math.sin(t / 220.0) * 1.2F : hov ? -1F : 0F;
			c.pose().pushMatrix();
			c.pose().translate(0, bob);
			c.item(icon(item), x + 4, y + 4);
			c.pose().popMatrix();
			if (hov) {
				tip = name;
				tipX = x + size / 2;
				tipY = y;
			}
			final String fid = id;
			hits.add(new Hit(x, y, size, size, () -> pick.accept(fid)));
		}
		return cy + ((n + cols - 1) / cols) * (size + gap);
	}

	private int modeKit(GuiGraphicsExtractor c, int mx, int my, int ix, int iw, int cy, boolean modes) {
		if (modes) {
			Gfx.text(c, Lang.tr("Mode", "Modo"), ix, cy + 4, Theme.DIM);
			chip(c, mx, my, ix + 40, cy, 50, 16, "1v1", mode.equals("1v1"), () -> mode = "1v1");
			chip(c, mx, my, ix + 94, cy, 50, 16, "2v2", mode.equals("2v2"), () -> mode = "2v2");
		}
		Ranked.Kit cur = Ranked.kit(kit);
		String label = "Kit: ";
		int lx = modes ? ix + 160 : ix;
		Gfx.text(c, label, lx, cy + 4, Theme.DIM);
		Gfx.text(c, cur.name(), lx + Gfx.tw(label), cy + 4, 0xFFFFFFFF);
		cy += 20;
		return kitGrid(c, mx, my, ix, iw, cy, kit, false, id -> kit = id) + 4;
	}

	/** Online friends as chips; the picked one is returned through the setter. */
	private int friends(GuiGraphicsExtractor c, int mx, int my, int ix, int iw, int cy, String label, String picked, java.util.function.Consumer<String> pick, String... exclude) {
		Gfx.text(c, label, ix, cy, Theme.DIM);
		int fx = ix + 70, fw = 72;
		List<Social.User> list = new ArrayList<>();
		for (Social.User u : Social.friends) {
			boolean ex = false;
			for (String e : exclude) {
				ex |= u.id != null && u.id.equals(e);
			}
			if (!ex) {
				list.add(u);
			}
		}
		list.sort((a, b) -> Boolean.compare(b.online(), a.online()));
		int n = 0;
		for (Social.User u : list) {
			if (fx + fw > ix + iw) {
				break;
			}
			chip(c, mx, my, fx, cy - 4, fw, 16, (u.online() ? "• " : "") + u.display(), u.id != null && u.id.equals(picked), () -> pick.accept(u.id));
			fx += fw + 4;
			n++;
		}
		if (n == 0) {
			Gfx.text(c, Lang.tr("No friends yet", "Ainda não tens amigos"), ix + 70, cy, Theme.DIM);
		}
		return cy + 20;
	}

	/** "label  [-]  n  [+]" stepper. */
	private void stepper(GuiGraphicsExtractor c, int mx, int my, int x, int y, String label, int value, int min, int max, java.util.function.IntConsumer set) {
		Gfx.text(c, label, x, y + 4, Theme.DIM);
		int bx = x + Gfx.tw(label) + 6;
		chip(c, mx, my, bx, y, 16, 16, "-", false, () -> set.accept(Math.max(min, value - 1)));
		Gfx.rrect(c, bx + 19, y, 26, 16, 5, 0xFF14161D);
		Gfx.textC(c, String.valueOf(value), bx + 32, y + 4, 0xFFFFD23C);
		chip(c, mx, my, bx + 48, y, 16, 16, "+", false, () -> set.accept(Math.min(max, value + 1)));
	}

	// ---------------------------------------------------------------- tabs
	private void ranked(GuiGraphicsExtractor c, int mx, int my, int ix, int iw, int cy) {
		cy = modeKit(c, mx, my, ix, iw, cy, true);
		if (mode.equals("2v2")) {
			cy = friends(c, mx, my, ix, iw, cy + 4, Lang.tr("Teammate", "Colega"), teammate, id -> teammate = id);
		}
		var r = Ranked.rating(kit, mode);
		String yt = Lang.tr("Your tier: ", "O teu tier: ");
		Gfx.text(c, yt, ix, cy + 4, Theme.DIM);
		tierBadge(c, ix + Gfx.tw(yt), cy + 2, r == null ? "LT5" : r.tier());
		Gfx.text(c, (r == null ? "1150" : String.valueOf(r.elo())) + " ELO" + (r == null ? "" : "  ·  " + r.wins() + "W " + r.losses() + "L")
				+ Lang.tr("  ·  first to 2 rounds", "  ·  primeiro a 2 rondas"), ix + Gfx.tw(yt) + 34, cy + 4, Theme.TEXT);
		cy += 20;
		boolean searching = !"idle".equals(Ranked.queueState);
		if (Ranked.inMatch()) {
			Gfx.text(c, Lang.tr("In a match - good luck!", "Numa partida - boa sorte!"), ix, cy + 6, 0xFF55FF7F);
			String lv = Lang.tr("Leave the match (counts as a loss)", "Sair da partida (conta como derrota)");
			int lw = Gfx.tw(lv) + 24;
			bigButton(c, mx, my, ix + iw - lw, cy, lw, 22, lv, 0xFF7A2E38, () -> Ranked.leaveMatch(net.minecraft.client.Minecraft.getInstance()));
		} else if (searching) {
			long s = (System.currentTimeMillis() - Ranked.queuedAt) / 1000;
			bigButton(c, mx, my, ix, cy, iw, 26, Lang.tr("Searching... ", "A procurar... ") + s / 60 + ":" + String.format("%02d", s % 60) + Lang.tr("  (click to cancel)", "  (clica para cancelar)"),
					0xFF5A3A8A, Ranked::leaveQueue);
		} else {
			boolean ok = mode.equals("1v1") || teammate != null;
			bigButton(c, mx, my, ix, cy, iw, 26, ok ? Lang.tr("Find a match", "Procurar partida") : Lang.tr("Pick a teammate first", "Escolhe primeiro um colega"),
					ok ? Theme.accent() : DISABLED, () -> {
						if (mode.equals("1v1") || teammate != null) {
							Ranked.queue(mode, kit, mode.equals("2v2") ? teammate : null);
						}
					});
		}
		cy += 32;
		Gfx.text(c, Lang.tr("The first player in the queue hosts. Leaving = losing (the host too). The host can't kick, ban or use commands.",
				"O primeiro na fila é o host. Sair = perder (o host também). O host não pode expulsar, banir nem usar comandos."), ix, cy, Theme.DIM);
	}

	private void duel(GuiGraphicsExtractor c, int mx, int my, int ix, int iw, int cy) {
		cy = modeKit(c, mx, my, ix, iw, cy, true);
		stepper(c, mx, my, ix, cy, Lang.tr("First to", "Primeiro a"), firstTo, 1, 10, v -> firstTo = v);
		Gfx.text(c, Lang.tr("rounds  (not ranked)", "rondas  (não conta para o ranking)"), ix + Gfx.tw(Lang.tr("First to", "Primeiro a")) + 76, cy + 4, Theme.DIM);
		cy += 24;
		if (mode.equals("2v2")) {
			cy = friends(c, mx, my, ix, iw, cy + 4, Lang.tr("Teammate", "Colega"), teammate, id -> teammate = id, opponent1, opponent2);
			cy = friends(c, mx, my, ix, iw, cy, Lang.tr("Opponent 1", "Adversário 1"), opponent1, id -> opponent1 = id, teammate, opponent2);
			cy = friends(c, mx, my, ix, iw, cy, Lang.tr("Opponent 2", "Adversário 2"), opponent2, id -> opponent2 = id, teammate, opponent1);
		} else {
			cy = friends(c, mx, my, ix, iw, cy + 4, Lang.tr("Opponent", "Adversário"), opponent1, id -> opponent1 = id);
		}
		boolean ok = mode.equals("1v1") ? opponent1 != null : teammate != null && opponent1 != null && opponent2 != null;
		bigButton(c, mx, my, ix, cy, iw, 26, ok ? Lang.tr("Send challenge · first to ", "Enviar desafio · primeiro a ") + firstTo : Lang.tr("Pick the players", "Escolhe os jogadores"),
				ok ? Theme.accent() : DISABLED, () -> {
					if (mode.equals("1v1") && opponent1 != null) {
						Ranked.challenge(mode, kit, null, List.of(opponent1), firstTo);
					} else if (teammate != null && opponent1 != null && opponent2 != null) {
						Ranked.challenge(mode, kit, teammate, List.of(opponent1, opponent2), firstTo);
					}
				});
	}

	private void botsTab(GuiGraphicsExtractor c, int mx, int my, int ix, int iw, int cy) {
		cy = modeKit(c, mx, my, ix, iw, cy, false);
		Gfx.text(c, Lang.tr("Difficulty", "Dificuldade"), ix, cy + 4, Theme.DIM);
		BotDifficulty[] ds = BotDifficulty.values();
		for (int i = 0; i < ds.length; i++) {
			final int d = i;
			chip(c, mx, my, ix + 70 + i * 64, cy, 60, 16, ds[i].label, botDiff == i, () -> botDiff = d);
		}
		cy += 22;
		stepper(c, mx, my, ix, cy, "Bots", bots, 1, 3, v -> bots = v);
		cy += 26;
		bigButton(c, mx, my, ix, cy, iw, 26, Lang.tr("Fight bots · ", "Lutar contra bots · ") + Ranked.kit(kit).name(), 0xFF2E9E5B, this::startBots);
		cy += 32;
		Gfx.text(c, Lang.tr("Practice right here, nothing to install. Bot fights never count for the rankings.",
				"Treina aqui mesmo, sem instalar nada. Lutas contra bots nunca contam para o ranking."), ix, cy, Theme.DIM);
		Gfx.text(c, Lang.tr("Bots fight with the kit's melee weapons (no crystals, carts, pots or bows).",
				"Os bots lutam com as armas corpo-a-corpo do kit (sem cristais, carts, poções nem arcos)."), ix, cy + 11, Theme.DIM);
	}

	private void startBots() {
		Ranked.Kit kk = Ranked.kit(kit);
		PracticeSession.kit = PracticeKit.fromFunction(kk.name(), Ranked.kitLines(kk.id()));
		if (PracticeSession.arena == null) {
			PracticeSession.arena = dev.solclient.addons.pvp.PracticeArena.ALL.get(0);
		}
		PracticeSession.difficulty = BotDifficulty.values()[botDiff];
		PracticeSession.botCount = bots;
		PracticeWorld.go(embedded ? parent : this, PracticeWorld.PVP, "PvP Practice", PracticeSession::begin);
	}

	// ---------------------------------------------------------------- leaderboard
	private void loadRank() {
		Ranked.loadRankings(rankKit, rankMode);
		rankAt = System.currentTimeMillis();
		scroll = 0;
	}

	private static final int GOLD = 0xFFFFD23C, SILVER = 0xFFD9E1EC, BRONZE = 0xFFE0904F;

	private void rankings(GuiGraphicsExtractor c, int mx, int my, int ix, int iw, int cy, int bottom) {
		boolean overall = OVERALL.equals(rankKit);
		int gridW = iw - 112;
		int gy = kitGrid(c, mx, my, ix, gridW, cy, rankKit, true, id -> {
			rankKit = id;
			loadRank();
		});
		chip(c, mx, my, ix + iw - 104, cy, 50, 16, "1v1", rankMode.equals("1v1"), () -> {
			rankMode = "1v1";
			loadRank();
		});
		chip(c, mx, my, ix + iw - 50, cy, 50, 16, "2v2", rankMode.equals("2v2"), () -> {
			rankMode = "2v2";
			loadRank();
		});
		String title = overall ? Lang.tr("Overall", "Geral") : Ranked.kit(rankKit).name();
		Gfx.text(c, title, ix + iw - 104, cy + 22, 0xFFFFFFFF);
		Gfx.text(c, overall ? Lang.tr("tier points", "pontos de tier") : "ELO", ix + iw - 104, cy + 33, Theme.DIM);
		cy = gy + 4;

		List<Ranked.RankRow> rows = Ranked.rankings;
		float age = (System.currentTimeMillis() - rankAt) / 1000F;
		if (rows.isEmpty()) {
			Gfx.textC(c, Lang.tr("Nobody ranked here yet - be the first!", "Ainda ninguém aqui - sê o primeiro!"), ix + iw / 2, cy + 40, Theme.DIM);
			return;
		}
		// podium: 2nd | 1st | 3rd, rising in
		int ph = 130, pw = 96;
		int[] order = {1, 0, 2};
		int[] heights = {40, 54, 30};
		int[] cols = {SILVER, GOLD, BRONZE};
		int px0 = ix + (iw - pw * 3 - 16) / 2;
		long t = System.currentTimeMillis();
		for (int s = 0; s < 3; s++) {
			int idx = order[s];
			if (idx >= rows.size()) {
				continue;
			}
			Ranked.RankRow r = rows.get(idx);
			float p = Math.max(0F, Math.min(1F, (age - s * 0.08F) / 0.45F));
			p = 1F - (1F - p) * (1F - p) * (1F - p);
			int bh = Math.round(heights[idx] * p);
			int px = px0 + s * (pw + 8), base = cy + ph + 22;
			int col = cols[idx];
			// block
			Gfx.rrect(c, px, base - bh, pw, bh, 6, Gfx.withAlpha(col & 0xFFFFFF, 0x30));
			Gfx.rrectBorder(c, px, base - bh, pw, bh, 6, 0, Gfx.withAlpha(col & 0xFFFFFF, 0xAA));
			if (bh > 14) {
				Gfx.textC(c, String.valueOf(idx + 1), px + pw / 2, base - bh + 4, col);
			}
			// the player in 3D standing on the block, name under the feet
			int bodyH = idx == 0 ? 84 : 72, bodyW = 70;
			int by = base - bh - 22 - bodyH + (idx == 0 ? Math.round((float) Math.sin(t / 400.0) * 1.5F) : 0);
			if (idx == 0) {
				// glow behind the champion
				float g = 0.5F + 0.5F * (float) Math.sin(t / 300.0);
				Gfx.rrect(c, px + (pw - 56) / 2, by + 6, 56, bodyH - 6, 10, Gfx.withAlpha(GOLD & 0xFFFFFF, 0x20 + Math.round(g * 0x20)));
			}
			if (p < 0.95F || !Faces.body(c, r.name(), px + (pw - bodyW) / 2, by, bodyW, bodyH)) {
				int hs = idx == 0 ? 28 : 22;
				Faces.draw(c, r.name(), px + (pw - hs) / 2, base - bh - 22 - hs, hs, Math.round(255 * p));
			}
			Gfx.textFit(c, r.name(), px + pw / 2, pw - 4, base - bh - 19, r.name().equalsIgnoreCase(Ranked.me()) ? 0xFF7FD8FF : 0xFFFFFFFF);
			String val = overall ? r.elo() + " pts" : r.elo() + " ELO";
			int bw = Gfx.tw(r.tier()) + 8;
			int vw = Gfx.tw(val);
			int sx = px + (pw - bw - 4 - vw) / 2;
			tierBadge(c, sx, base - bh - 10, r.tier());
			Gfx.text(c, val, sx + bw + 4, base - bh - 8, Theme.DIM);
		}
		cy += ph + 28;

		// the rest: rows sliding in
		int listBottom = bottom - 34;
		c.enableScissor(ix, cy, ix + iw, listBottom);
		int ry = cy - Math.round(scroll);
		String me = Ranked.me();
		int best = Math.max(1, rows.get(0).elo());
		for (int i = 3; i < rows.size(); i++) {
			Ranked.RankRow r = rows.get(i);
			float p = Math.max(0F, Math.min(1F, (age - 0.3F - (i - 3) * 0.04F) / 0.3F));
			int off = Math.round((1F - p) * 30F);
			boolean mine = r.name().equalsIgnoreCase(me);
			boolean hov = in(mx, my, ix, ry, iw, 20) && my < listBottom;
			int rx = ix + off;
			Gfx.rrect(c, rx, ry, iw, 20, 6, mine ? 0x3335D0F5 : hov ? 0x22FFFFFF : 0x10FFFFFF);
			int tc = Ranked.tierColour(r.tier());
			Gfx.text(c, "#" + r.position(), rx + 8, ry + 6, Theme.DIM);
			Faces.draw(c, r.name(), rx + 36, ry + 2, 16, Math.round(255 * p));
			Gfx.text(c, r.name(), rx + 58, ry + 6, mine ? 0xFF7FD8FF : 0xFFFFFFFF);
			// score bar
			int barX = rx + iw - 236, barW = 80;
			float f = Math.max(0.04F, Math.min(1F, r.elo() / (float) best)) * p;
			Gfx.rrect(c, barX, ry + 8, barW, 4, 2, 0x22FFFFFF);
			Gfx.rrect(c, barX, ry + 8, Math.round(barW * f), 4, 2, tc);
			tierBadge(c, rx + iw - 146, ry + 4, r.tier());
			Gfx.text(c, overall ? r.elo() + " pts" : r.elo() + " ELO", rx + iw - 112, ry + 6, Theme.TEXT);
			int games = r.wins() + r.losses();
			Gfx.text(c, r.wins() + "W " + r.losses() + "L" + (games > 0 ? "  " + (100 * r.wins() / games) + "%" : ""), rx + iw - 58, ry + 6, Theme.DIM);
			ry += 22;
		}
		c.disableScissor();
		float max = Math.max(0, (rows.size() - 3) * 22 - (listBottom - cy));
		scroll = Math.max(0, Math.min(max, scroll));
	}

	private void profile(GuiGraphicsExtractor c, int mx, int my, int ix, int iw, int cy) {
		Faces.draw(c, Ranked.me(), ix, cy - 2, 14, 255);
		Gfx.text(c, Ranked.me(), ix + 18, cy + 2, 0xFFFFFFFF);
		chip(c, mx, my, ix + iw - 104, cy - 2, 50, 16, "1v1", rankMode.equals("1v1"), () -> rankMode = "1v1");
		chip(c, mx, my, ix + iw - 50, cy - 2, 50, 16, "2v2", rankMode.equals("2v2"), () -> rankMode = "2v2");
		cy += 20;
		int cols = 7, gap = 5;
		int cw = (iw - gap * (cols - 1)) / cols, ch = 40;
		for (int i = 0; i < Ranked.KITS.size(); i++) {
			Ranked.Kit kk = Ranked.KITS.get(i);
			var r = Ranked.rating(kk.id(), rankMode);
			int x = ix + (i % cols) * (cw + gap), y = cy + (i / cols) * (ch + gap);
			String tier = r == null ? "-" : r.tier();
			int tc = r == null ? 0xFF5A5F6E : Ranked.tierColour(tier);
			Gfx.rrect(c, x, y, cw, ch, 7, 0xFF1C1F29);
			Gfx.rrectBorder(c, x, y, cw, ch, 7, 0, Gfx.withAlpha(tc & 0xFFFFFF, 0x99));
			c.item(icon(kk.icon()), x + 4, y + 4);
			Gfx.textFit(c, kk.name(), x + cw / 2 + 9, cw - 24, y + 8, Theme.DIM);
			var tr = Minecraft.getInstance().font;
			Component tt = Component.literal(tier).withStyle(s -> s.withBold(true));
			c.text(tr, tt, x + (cw - tr.width(tt)) / 2, y + 22, tc, true);
			if (in(mx, my, x, y, cw, ch)) {
				tip = r == null ? Lang.tr("Not ranked yet", "Ainda sem tier") : r.elo() + " ELO · " + r.wins() + "W " + r.losses() + "L";
				tipX = x + cw / 2;
				tipY = y;
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x(), my = click.y();
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit hh = hits.get(i);
			if (mx >= hh.x && mx < hh.x + hh.w && my >= hh.y && my < hh.y + hh.h) {
				Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
				hh.run.run();
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double h, double v) {
		scroll -= (float) v * 20F;
		return true;
	}
}
