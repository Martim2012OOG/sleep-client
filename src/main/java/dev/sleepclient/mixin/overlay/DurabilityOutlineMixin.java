package dev.sleepclient.mixin.overlay;

import dev.sleepclient.mods.DurabilityOverlayMod;
import dev.sleepclient.mods.HideArmorMod;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Durability Overlay: each armour piece is drawn with its own outline colour (green to red). */
@Mixin(ArmorFeatureRenderer.class)
public abstract class DurabilityOutlineMixin {

	@Unique
	private static PlayerEntityRenderState kubo$tinted;
	@Unique
	private static int kubo$prev;

	private static final String RENDER = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V";

	@Inject(method = RENDER, at = @At("HEAD"), require = 0)
	private void kubo$outlineOn(MatrixStack matrices, OrderedRenderCommandQueue queue, ItemStack stack, EquipmentSlot slot, int light,
			BipedEntityRenderState state, CallbackInfo ci) {
		kubo$tinted = null;
		if ((Object) state instanceof PlayerEntityRenderState ps && !HideArmorMod.hides(ps.id) && DurabilityOverlayMod.wantsId(ps.id)) {
			int c = DurabilityOverlayMod.colourFor(stack);
			if (c != 0) {
				kubo$prev = ps.outlineColor;
				ps.outlineColor = c;
				kubo$tinted = ps;
			}
		}
	}

	@Inject(method = RENDER, at = @At("RETURN"), require = 0)
	private void kubo$outlineOff(MatrixStack matrices, OrderedRenderCommandQueue queue, ItemStack stack, EquipmentSlot slot, int light,
			BipedEntityRenderState state, CallbackInfo ci) {
		if (kubo$tinted != null) {
			kubo$tinted.outlineColor = kubo$prev;
			kubo$tinted = null;
		}
	}
}
