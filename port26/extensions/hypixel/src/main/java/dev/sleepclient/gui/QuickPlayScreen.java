package dev.sleepclient.gui;

import dev.sleepclient.Gfx;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Palette-style game picker for Hypixel. */
public class QuickPlayScreen extends Screen {

	private record Game(String game, String mode, String command) {
		String label() {
			return game + " - " + mode;
		}
	}

	private static final List<Game> GAMES = new ArrayList<>();

	private static void add(String game, String mode, String id) {
		GAMES.add(new Game(game, mode, "play " + id));
	}

	static {
		add("Bed Wars", "Solo", "bedwars_eight_one");
		add("Bed Wars", "Doubles", "bedwars_eight_two");
		add("Bed Wars", "3v3v3v3", "bedwars_four_three");
		add("Bed Wars", "4v4v4v4", "bedwars_four_four");
		add("Bed Wars", "4v4", "bedwars_two_four");
		add("SkyWars", "Solo Normal", "solo_normal");
		add("SkyWars", "Solo Insane", "solo_insane");
		add("SkyWars", "Teams Normal", "teams_normal");
		add("SkyWars", "Teams Insane", "teams_insane");
		add("SkyWars", "Ranked", "ranked_normal");
		add("Duels", "Classic", "duels_classic_duel");
		add("Duels", "Sumo", "duels_sumo_duel");
		add("Duels", "UHC", "duels_uhc_duel");
		add("Duels", "Bridge", "duels_bridge_duel");
		add("Duels", "OP", "duels_op_duel");
		add("Duels", "Combo", "duels_combo_duel");
		add("Duels", "Bow", "duels_bow_duel");
		add("Duels", "NoDebuff", "duels_potion_duel");
		add("Duels", "Boxing", "duels_boxing_duel");
		add("Murder Mystery", "Classic", "murder_classic");
		add("Murder Mystery", "Double Up", "murder_double_up");
		add("Murder Mystery", "Assassins", "murder_assassins");
		add("Murder Mystery", "Infection", "murder_infection");
		add("UHC Champions", "Solo", "uhc_solo");
		add("UHC Champions", "Teams", "uhc_teams");
		add("Speed UHC", "Solo", "speed_solo_normal");
		add("Speed UHC", "Teams", "speed_team_normal");
		add("Build Battle", "Solo", "build_battle_solo_normal");
		add("Build Battle", "Teams", "build_battle_teams_normal");
		add("Build Battle", "Pro", "build_battle_solo_pro");
		add("Build Battle", "Guess The Build", "build_battle_guess_the_build");
		add("Blitz SG", "Solo", "blitz_solo_normal");
		add("Blitz SG", "Teams", "blitz_teams_normal");
		add("TNT Games", "TNT Run", "tnt_tntrun");
		add("TNT Games", "PVP Run", "tnt_pvprun");
		add("TNT Games", "Bow Spleef", "tnt_bowspleef");
		add("TNT Games", "TNT Tag", "tnt_tntag");
		add("TNT Games", "Wizards", "tnt_capture");
		add("Mega Walls", "Standard", "mw_standard");
		add("Cops and Crims", "Defusal", "mcgo_normal");
		add("The Pit", "The Pit", "pit");
		add("SkyBlock", "SkyBlock", "SKYBLOCK");
	}

	private String search = "";
	private float scroll, scrollTarget, maxScroll;

	public QuickPlayScreen() {
		super(Component.literal("Quick Play"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private List<Game> filtered() {
		String q = search.toLowerCase(Locale.ROOT).trim();
		List<Game> out = new ArrayList<>();
		for (Game g : GAMES) {
			if (q.isEmpty() || g.label().toLowerCase(Locale.ROOT).contains(q)) {
				out.add(g);
			}
		}
		return out;
	}

	private int pw() {
		return Math.min(width - 16, 360);
	}

	private int px() {
		return (width - pw()) / 2;
	}

	private int py() {
		return Math.max(8, height / 2 - 130);
	}

	private int ph() {
		return Math.min(height - 16, 260);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
		super.extractRenderState(c, mouseX, mouseY, delta);
		scroll += (scrollTarget - scroll) * 0.35F;
		int x = px(), y = py(), w = pw(), h = ph();
		Gfx.rrect(c, x, y, w, h, 10, Theme.PANEL);
		Gfx.rrect(c, x + 10, y + 10, w - 20, 22, 7, Theme.FIELD);
		boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
		Gfx.textMid(c, search.isEmpty() ? "Search games..." : search + (caret ? "|" : ""), x + 20, y + 10, 22,
				search.isEmpty() ? Theme.DIM : Theme.TEXT);
		int ly = y + 40, ly1 = y + h - 10;
		List<Game> list = filtered();
		int rowH = 26;
		maxScroll = Math.max(0, list.size() * rowH - (ly1 - ly));
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		c.enableScissor(x, ly, x + w, ly1);
		for (int i = 0; i < list.size(); i++) {
			int ry = ly + i * rowH - Math.round(scroll);
			if (ry + rowH < ly || ry > ly1) {
				continue;
			}
			boolean hov = mouseX >= x + 10 && mouseX < x + w - 10 && mouseY >= ry && mouseY < ry + 22 && mouseY >= ly
					&& mouseY < ly1;
			Gfx.rrect(c, x + 10, ry, w - 20, 22, 7, hov ? Theme.accent() : Theme.CARD);
			Gfx.textMid(c, list.get(i).game(), x + 20, ry, 22, hov ? 0xFFFFFFFF : Theme.TEXT);
			String mode = list.get(i).mode();
			Gfx.textMid(c, mode, x + w - 20 - Gfx.tw(mode), ry, 22, hov ? 0xFFFFFFFF : Theme.DIM);
		}
		c.disableScissor();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		int x = px(), y = py(), w = pw(), h = ph();
		int ly = y + 40, ly1 = y + h - 10;
		List<Game> list = filtered();
		for (int i = 0; i < list.size(); i++) {
			int ry = ly + i * 26 - Math.round(scroll);
			if (click.x() >= x + 10 && click.x() < x + w - 10 && click.y() >= ry && click.y() < ry + 22
					&& click.y() >= ly && click.y() < ly1) {
				Minecraft mc = Minecraft.getInstance();
				if (mc.player != null) {
					mc.player.connection.sendCommand(list.get(i).command());
				}
				dev.sleepclient.compat.Mc.setScreen(mc, null);
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) vAmount * 30F));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (k == 256) {
			dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), null);
			return true;
		}
		if (k == 259 && !search.isEmpty()) {
			search = search.substring(0, search.length() - 1);
			scrollTarget = 0;
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		int cp = input.codepoint();
		if (cp >= 32 && cp != 127 && search.length() < 24) {
			search += new String(Character.toChars(cp));
			scrollTarget = 0;
		}
		return true;
	}
}
