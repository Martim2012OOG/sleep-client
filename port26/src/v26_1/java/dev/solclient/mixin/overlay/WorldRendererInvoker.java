package dev.solclient.mixin.overlay;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer")
public interface WorldRendererInvoker {

	@Invoker("allChanged")
	void sol$reload();
}
