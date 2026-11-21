package dev.kuboext.stats;

import dev.sleepclient.mods.SessionStatsMod;
import dev.sleepclient.mods.DailyQuestsMod;
import dev.sleepclient.mods.AchievementsMod;
import dev.sleepclient.mods.FightRecapMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/** Sleep extension "Stats & Missions": Session Stats, Daily Quests, Achievements and Fight Recap. */
public class StatsExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new SessionStatsMod());
		api.addMod(new DailyQuestsMod());
		api.addMod(new AchievementsMod());
		api.addMod(new FightRecapMod());
		api.onAttack(e -> {
			if (SessionStatsMod.instance != null && SessionStatsMod.instance.enabled) SessionStatsMod.instance.onAttack(e);
			if (DailyQuestsMod.instance != null && DailyQuestsMod.instance.enabled) DailyQuestsMod.instance.onAttack(e);
			if (FightRecapMod.instance != null && FightRecapMod.instance.enabled) FightRecapMod.instance.onAttack(e);
		});
		api.onStatsChanged(AchievementsMod::check);
		api.command("Achievements", "Your Sleep medals", "achievements",
				() -> dev.sleepclient.compat.Mc.setScreen(Minecraft.getInstance(), new dev.sleepclient.gui.AchievementsScreen(dev.sleepclient.compat.Mc.screen(Minecraft.getInstance()))));
	}
}
