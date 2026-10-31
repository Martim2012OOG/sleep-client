package dev.solclient.mixin.overlay;

import dev.solclient.mods.HideArmorMod;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide Armor: skips drawing a player's armour pieces. */
@Mixin(ArmorFeatureRenderer.class)
public abstract class HideArmorMixin {

	@Inject(method = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$hideArmor(MatrixStack matrices, OrderedRenderCommandQueue queue, ItemStack stack, EquipmentSlot slot, int light,
			BipedEntityRenderState state, CallbackInfo ci) {
		if ((Object) state instanceof PlayerEntityRenderState ps && HideArmorMod.hides(ps.id)) {
			ci.cancel();
		}
	}
}
