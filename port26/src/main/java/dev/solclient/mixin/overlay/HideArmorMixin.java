package dev.solclient.mixin.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.solclient.mods.HideArmorMod;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide Armor: skips drawing a player's armour pieces. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HideArmorMixin {

	@Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$hideArmor(PoseStack matrices, SubmitNodeCollector queue, ItemStack stack, EquipmentSlot slot, int light,
			HumanoidRenderState state, CallbackInfo ci) {
		if ((Object) state instanceof AvatarRenderState ps && HideArmorMod.hides(ps.id)) {
			ci.cancel();
		}
	}
}
