package dev.sleepclient.mixin.social;

import dev.sleepclient.social.SolarBadge;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Plegumo logo in front of the name in the tab list (vanilla tab and the Tab List mod both use getPlayerName). */
@Mixin(PlayerTabOverlay.class)
public abstract class TabBadgeMixin {

	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
	private void plegumo$badge(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
		if (entry != null && cir.getReturnValue() != null) {
			Component name = dev.sleepclient.mods.NickHiderMod.apply(cir.getReturnValue());
			name = dev.sleepclient.mods.NameColourMod.apply(name);
			// same check as the nametag: by account uuid OR by game nick (offline-mode servers give players other uuids)
			name = SolarBadge.uses(SolarBadge.idOf(entry.getProfile()), SolarBadge.nameOf(entry.getProfile())) ? SolarBadge.decorate(name) : name;
			name = dev.sleepclient.social.ProfileBadges.decorate(SolarBadge.idOf(entry.getProfile()), SolarBadge.nameOf(entry.getProfile()), name);
			cir.setReturnValue(dev.sleepclient.ext.KuboApi.decorateTab(SolarBadge.idOf(entry.getProfile()), name));
		}
	}
}
