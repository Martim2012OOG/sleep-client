package dev.sleepclient.mods;

import dev.sleepclient.Lang;
import dev.sleepclient.option.Option;
import dev.sleepclient.stats.KuboStats;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Sleep Achievements: medals for playing with Sleep ("100 kills", "7 days in a row", "10 km walked", minigame
 * records...). A card pops up when you unlock one; see them all with "Open".
 */
public class AchievementsMod extends Mod {

	public static AchievementsMod instance;
	private final Option.Bool popups = bool("Pop-up When Unlocked", true);
	private static long lastCheck;

	public record Ach(String id, String name, String desc, int tier, double goal, DoubleSupplier value) {
		public double progress() {
			return Math.max(0, Math.min(1, value.getAsDouble() / goal));
		}

		public boolean done() {
			return KuboStats.get("ach." + id) > 0;
		}
	}

	private static int best(String game) {
		return dev.sleepclient.gui.MinigamesScreen.bestScore(game);
	}

	public static final List<Ach> ALL = List.of(
			new Ach("kill1", "First Blood", "Get your first kill", 0, 1, () -> KuboStats.get("kills")),
			new Ach("kill100", "Warrior", "100 kills", 1, 100, () -> KuboStats.get("kills")),
			new Ach("kill1000", "Legend", "1000 kills", 2, 1000, () -> KuboStats.get("kills")),
			new Ach("pk50", "Duelist", "Kill 50 players", 1, 50, () -> KuboStats.get("playerKills")),
			new Ach("blocks1k", "Miner", "Break 1000 blocks", 0, 1000, () -> KuboStats.get("blocks")),
			new Ach("blocks10k", "Excavator", "Break 10 000 blocks", 1, 10000, () -> KuboStats.get("blocks")),
			new Ach("walk10", "Explorer", "Walk 10 km", 0, 10000, () -> KuboStats.get("walked")),
			new Ach("walk100", "Marathon", "Walk 100 km", 2, 100000, () -> KuboStats.get("walked")),
			new Ach("time10", "Regular", "Play 10 hours with Sleep", 0, 36000, () -> KuboStats.get("seconds")),
			new Ach("time100", "No Life", "Play 100 hours with Sleep", 2, 360000, () -> KuboStats.get("seconds")),
			new Ach("streak7", "On Fire", "Play 7 days in a row", 1, 7, () -> KuboStats.get("bestStreak")),
			new Ach("streak30", "Unstoppable", "Play 30 days in a row", 2, 30, () -> KuboStats.get("bestStreak")),
			new Ach("clip10", "Content Creator", "Save 10 clips", 1, 10, () -> KuboStats.get("clips")),
			new Ach("combo10", "Combo King", "Get a 10 hit combo", 1, 10, () -> KuboStats.get("bestCombo")),
			new Ach("friends5", "Social", "Have 5 Sleep friends", 0, 5, () -> dev.sleepclient.social.Social.friends.size()),
			new Ach("ping25", "Shot Caller", "Ping 25 times", 0, 25, () -> KuboStats.get("pings")),
			new Ach("g2048", "2048!", "Reach 2048 points in 2048", 1, 2048, () -> best("2048")),
			new Ach("snake30", "Snake Charmer", "Eat 30 apples in Snake", 1, 30, () -> best("snake")),
			new Ach("tetris10k", "Line Clearer", "10 000 points in Tetris", 2, 10000, () -> best("tetris")),
			new Ach("bird20", "Frequent Flyer", "Pass 20 pipes in Sleep Bird", 1, 20, () -> best("bird")));

	public AchievementsMod() {
		super("Achievements", "achievements", "Sleep medals for kills, streaks, distance, clips and minigame records.", Category.UTILITY);
		instance = this;
		action("Your medals", "Open", () -> dev.sleepclient.compat.Mc.setScreen(mc, new dev.sleepclient.gui.AchievementsScreen(dev.sleepclient.compat.Mc.screen(mc))), () -> unlocked() + " / " + ALL.size());
	}

	@Override
	public boolean enabledByDefault() {
		return false; // only when you turn it on
	}

	public static int unlocked() {
		int n = 0;
		for (Ach a : ALL) {
			if (a.done()) {
				n++;
			}
		}
		return n;
	}

	/** Called when a counter changes (cheap: looks at most every 2 seconds). */
	public static void check() {
		long now = System.currentTimeMillis();
		if (instance == null || !instance.enabled || now - lastCheck < 2000L) {
			return;
		}
		lastCheck = now;
		for (Ach a : ALL) {
			if (!a.done() && a.value().getAsDouble() >= a.goal()) {
				KuboStats.set("ach." + a.id(), now / 1000.0);
				if (instance.popups.on()) {
					dev.sleepclient.fightcam.ClipToast.show(Lang.tr("Achievement unlocked!", "Conquista desbloqueada!"), Lang.t(a.name()) + "  -  " + Lang.t(a.desc()),
							null, false);
				}
			}
		}
	}
}
