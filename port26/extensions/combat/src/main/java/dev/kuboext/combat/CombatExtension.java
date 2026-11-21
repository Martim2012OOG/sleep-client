package dev.kuboext.combat;

import dev.sleepclient.mods.TargetMarkerMod;
import dev.sleepclient.mods.TargetHudMod;
import dev.sleepclient.mods.DamageNumbersMod;
import dev.sleepclient.mods.DamageTintMod;
import dev.sleepclient.mods.LowHealthMod;
import dev.sleepclient.mods.HitSoundsMod;
import dev.sleepclient.mods.KillSoundsMod;
import dev.sleepclient.mods.AttackCooldownMod;
import dev.sleepclient.mods.ItemCooldownsMod;
import dev.sleepclient.mods.TrajectoriesMod;
import dev.sleepclient.mods.TridentAssistMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Sleep extension "Combat Extras": Target Marker, Target HUD, Damage Numbers, Damage Tint, Low Health, Hit Sounds, Kill Sounds, Attack Cooldown, Item Cooldowns, Trajectories and Trident Assist. */
public class CombatExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new TargetMarkerMod());
		api.addMod(new TargetHudMod());
		api.addMod(new DamageNumbersMod());
		api.addMod(new DamageTintMod());
		api.addMod(new LowHealthMod());
		api.addMod(new HitSoundsMod());
		api.addMod(new KillSoundsMod());
		api.addMod(new AttackCooldownMod());
		api.addMod(new ItemCooldownsMod());
		api.addMod(new TrajectoriesMod());
		api.addMod(new TridentAssistMod());
		api.onAttack(e -> {
			if (TargetMarkerMod.instance != null && TargetMarkerMod.instance.enabled) TargetMarkerMod.instance.onAttack(e);
			if (TargetHudMod.instance != null && TargetHudMod.instance.enabled) TargetHudMod.instance.onAttack(e);
			if (HitSoundsMod.instance != null && HitSoundsMod.instance.enabled) HitSoundsMod.instance.onAttack(e);
			if (KillSoundsMod.instance != null && KillSoundsMod.instance.enabled) KillSoundsMod.instance.onAttack(e);
			if (DamageNumbersMod.instance != null && DamageNumbersMod.instance.enabled) DamageNumbersMod.instance.onAttack(e);
		});
		api.onHud(c -> {
			if (DamageNumbersMod.instance != null && DamageNumbersMod.instance.enabled) DamageNumbersMod.instance.draw(c);
			if (TridentAssistMod.instance != null && TridentAssistMod.instance.enabled) TridentAssistMod.instance.draw(c);
		});
		api.onHudLast(c -> {
			if (DamageTintMod.instance != null && DamageTintMod.instance.enabled) DamageTintMod.instance.draw(c);
			if (LowHealthMod.instance != null && LowHealthMod.instance.enabled) LowHealthMod.instance.draw(c);
		});
	}
}
