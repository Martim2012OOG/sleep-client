package dev.solclient.mixin.overlay;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's "draw this item stack (1 to 5 copies)" so Item Physics only changes how the item is turned. */
@Mixin(ItemEntityRenderer.class)
public interface ItemEntityRendererInvoker {

	@Invoker("render")
	static void kubo$renderStack(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, ItemStackEntityRenderState state, Random random,
			Box box) {
		throw new AssertionError();
	}
}
