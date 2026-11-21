package dev.sleepclient.mixin;

import dev.sleepclient.mods.CustomHandMod;
import dev.sleepclient.mods.OldAnimationsMod;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Custom Hand and 1.7 Animations: move / resize the held item. */
@Mixin(targets = "net.minecraft.client.render.item.HeldItemRenderer")
public class KuboHandMixin {

	@Unique
	private static int kubo$depth;

	@ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
	private MatrixStack kubo$move(MatrixStack m) {
		boolean custom = CustomHandMod.on(), old = OldAnimationsMod.on();
		float tiny = dev.sleepclient.mods.CustomTexturesMod.toolScale();
		if (!custom && !old && tiny >= 1F) return m;
		m.push();
		kubo$depth++;
		if (tiny < 1F) {
			// Custom Textures > Tiny Tools: shrink around where the item sits in the hand, not around the screen centre
			boolean left = net.minecraft.client.MinecraftClient.getInstance().options.getMainArm().getValue() == net.minecraft.util.Arm.LEFT;
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

	@ModifyVariable(method = "renderFirstPersonItem", at = @At("RETURN"), ordinal = 0, argsOnly = true, require = 0)
	private MatrixStack kubo$back(MatrixStack m) {
		if (kubo$depth > 0) {
			m.pop();
			kubo$depth--;
		}
		return m;
	}
}
