package dev.solclient.mixin.social;

import dev.solclient.social.SolarBadge;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Plegumo logo in front of the nametag of players who use the client. */
@Mixin(EntityRenderer.class)
public abstract class NametagBadgeMixin {

	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
	private void plegumo$badge(Entity entity, CallbackInfoReturnable<Text> cir) {
		if (entity instanceof PlayerEntity p && cir.getReturnValue() != null) {
			Text name = dev.solclient.mods.NickHiderMod.apply(cir.getReturnValue());
			name = dev.solclient.mods.NameColourMod.apply(name);
			name = SolarBadge.uses(p.getUuid(), p.getName().getString()) ? SolarBadge.decorate(name) : name;
			name = dev.solclient.ext.KuboApi.decorateName(p, name);
			name = dev.solclient.social.ProfileBadges.decorate(p.getUuid(), p.getName().getString(), name);
			name = dev.solclient.mods.TotemPopsMod.decorate(p, name);
			cir.setReturnValue(dev.solclient.voice.VoiceIcons.decorate(p.getUuid(), name));
		} else if (entity instanceof net.minecraft.entity.decoration.ArmorStandEntity && cir.getReturnValue() != null) {
			// servers that show the rank + name on an invisible armor stand instead of the player's own nametag
			cir.setReturnValue(dev.solclient.social.NameInject.apply(cir.getReturnValue()));
		}
	}
}
