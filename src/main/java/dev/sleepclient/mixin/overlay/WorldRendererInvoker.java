package dev.sleepclient.mixin.overlay;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.render.WorldRenderer")
public interface WorldRendererInvoker {

	@Invoker("reload")
	void sol$reload();
}
