package dev.kuboext.stats;

import dev.solclient.mods.SessionStatsMod;
import dev.solclient.mods.DailyQuestsMod;
import dev.solclient.mods.AchievementsMod;
import dev.solclient.mods.FightRecapMod;
import dev.solclient.ext.KuboApi;
import dev.solclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Caos extension "Stats & Missions": Session Stats, Daily Quests, Achievements and Fight Recap. */
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
		api.command("Achievements", "Your Caos medals", "achievements",
				() -> MinecraftClient.getInstance().setScreen(new dev.solclient.gui.AchievementsScreen(MinecraftClient.getInstance().currentScreen)));
	}
}
