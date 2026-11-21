package dev.sleepclient.mixin.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleepclient.mods.DurabilityOverlayMod;
import dev.sleepclient.mods.HideArmorMod;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Durability Overlay: each armour piece is drawn with its own outline colour (green to red). */
@Mixin(HumanoidArmorLayer.class)
public abstract class DurabilityOutlineMixin {

	@Unique
	private static AvatarRenderState kubo$tinted;
	@Unique
	private static int kubo$prev;

	private static final String RENDER = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V";

	@Inject(method = RENDER, at = @At("HEAD"), require = 0)
	private void kubo$outlineOn(PoseStack matrices, SubmitNodeCollector queue, ItemStack stack, EquipmentSlot slot, int light,
			HumanoidRenderState state, CallbackInfo ci) {
		kubo$tinted = null;
		if ((Object) state instanceof AvatarRenderState ps && !HideArmorMod.hides(ps.id) && DurabilityOverlayMod.wantsId(ps.id)) {
			int c = DurabilityOverlayMod.colourFor(stack);
			if (c != 0) {
				kubo$prev = ps.outlineColor;
				ps.outlineColor = c;
				kubo$tinted = ps;
			}
		}
	}

	@Inject(method = RENDER, at = @At("RETURN"), require = 0)
	private void kubo$outlineOff(PoseStack matrices, SubmitNodeCollector queue, ItemStack stack, EquipmentSlot slot, int light,
			HumanoidRenderState state, CallbackInfo ci) {
		if (kubo$tinted != null) {
			kubo$tinted.outlineColor = kubo$prev;
			kubo$tinted = null;
		}
	}
}
