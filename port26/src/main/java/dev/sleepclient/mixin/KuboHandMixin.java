package dev.sleepclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sleepclient.mods.CustomHandMod;
import dev.sleepclient.mods.OldAnimationsMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Custom Hand and 1.7 Animations: move / resize the held item. */
@Mixin(targets = "net.minecraft.client.renderer.ItemInHandRenderer")
public class KuboHandMixin {

	@Unique
	private static int kubo$depth;

	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
	private PoseStack kubo$move(PoseStack m) {
		boolean custom = CustomHandMod.on(), old = OldAnimationsMod.on();
		float tiny = dev.sleepclient.mods.CustomTexturesMod.toolScale();
		if (!custom && !old && tiny >= 1F) return m;
		m.pushPose();
		kubo$depth++;
		if (tiny < 1F) {
			// Custom Textures > Tiny Tools: shrink around where the item sits in the hand, not around the screen centre
			boolean left = net.minecraft.client.Minecraft.getInstance().options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT;
			float k = 1F - tiny;
			m.translate((left ? -0.56F : 0.56F) * k, -0.52F * k, -0.72F * k);
			m.scale(tiny, tiny, tiny);
		}
		if (old) {
			m.translate(0.0F, -0.08F, 0.08F);
			m.scale(1.15F, 1.15F, 1.15F);
		}
		if (custom) {
			CustomHandMod h = CustomHandMod.instance;
			m.translate(h.x.f() * 0.5F, h.y.f() * 0.5F, h.z.f() * 0.5F);
			float s = h.size.f();
			m.scale(s, s, s);
		}
		return m;
	}

	@ModifyVariable(method = "renderArmWithItem", at = @At("RETURN"), ordinal = 0, argsOnly = true, require = 0)
	private PoseStack kubo$back(PoseStack m) {
		if (kubo$depth > 0) {
			m.popPose();
			kubo$depth--;
		}
		return m;
	}
}
