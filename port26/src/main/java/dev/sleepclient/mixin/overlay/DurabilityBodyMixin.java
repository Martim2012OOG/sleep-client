package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.DurabilityOverlayMod;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Durability Overlay: vanilla outlines the whole player once an outline is requested. This keeps the body itself out of
 * it (only the armour pieces are outlined, see DurabilityOutlineMixin) and puts the colour back afterwards.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class DurabilityBodyMixin {

	@Unique
	private static AvatarRenderState kubo$zeroed;
	@Unique
	private static int kubo$saved;

	@ModifyVariable(method = "submit", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
	private LivingEntityRenderState kubo$bodyNoOutline(LivingEntityRenderState state) {
		kubo$zeroed = null;
		if ((Object) state instanceof AvatarRenderState ps && ps.outlineColor != 0 && DurabilityOverlayMod.armourOnly(ps.id)) {
			kubo$saved = ps.outlineColor;
			ps.outlineColor = 0;
			kubo$zeroed = ps;
		}
		return state;
	}

	@ModifyVariable(method = "submit", at = @At("RETURN"), ordinal = 0, argsOnly = true, require = 0)
	private LivingEntityRenderState kubo$bodyRestore(LivingEntityRenderState state) {
		if (kubo$zeroed != null) {
			kubo$zeroed.outlineColor = kubo$saved;
			kubo$zeroed = null;
		}
		return state;
	}
}
