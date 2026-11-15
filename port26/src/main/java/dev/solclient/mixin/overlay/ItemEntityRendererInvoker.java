package dev.solclient.mixin.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's "draw this item stack (1 to 5 copies)" so Item Physics only changes how the item is turned. */
@Mixin(ItemEntityRenderer.class)
public interface ItemEntityRendererInvoker {

	@Invoker("submitMultipleFromCount")
	static void kubo$renderStack(PoseStack matrices, SubmitNodeCollector queue, int light, ItemClusterRenderState state, RandomSource random,
			AABB box) {
		throw new AssertionError();
	}
}
