package dev.solclient.mixin.social;

import dev.solclient.mods.NametagHealthMod;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
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

	@Inject(method = "updateRenderState(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"), require = 0)
	private void kubo$mobHealth(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
		if (!(entity instanceof LivingEntity le) || entity instanceof PlayerEntity || !NametagHealthMod.on()) {
			return;
		}
		try {
			if (!NametagHealthMod.showOnMob(le, state.squaredDistanceToCamera)) {
				return;
			}
			Text hp = NametagHealthMod.line(le);
			if (state.displayName == null) {
				state.displayName = hp;
				state.nameLabelPos = new Vec3d(0, entity.getHeight(), 0);
			} else {
				state.displayName = Text.empty().append(state.displayName).append(Text.literal("  ")).append(hp);
			}
		} catch (RuntimeException ignored) {
		}
	}
}
