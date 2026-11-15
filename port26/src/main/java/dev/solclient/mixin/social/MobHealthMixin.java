package dev.solclient.mixin.social;

import dev.solclient.mods.NametagHealthMod;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nametag Health on mobs: a "13 ❤" label above mobs near you (drawn by Minecraft's own name label). A mob that already
 * has a name keeps it, with the hearts after it.
 */
@Mixin(EntityRenderer.class)
public abstract class MobHealthMixin {

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$mobHealth(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
		if (!(entity instanceof LivingEntity le) || entity instanceof Player || !NametagHealthMod.on()) {
			return;
		}
		try {
			if (!NametagHealthMod.showOnMob(le, state.distanceToCameraSq)) {
				return;
			}
			Component hp = NametagHealthMod.line(le);
			if (state.nameTag == null) {
				state.nameTag = hp;
				state.nameTagAttachment = new Vec3(0, entity.getBbHeight(), 0);
			} else {
				state.nameTag = Component.empty().append(state.nameTag).append(Component.literal("  ")).append(hp);
			}
		} catch (RuntimeException ignored) {
		}
	}
}
