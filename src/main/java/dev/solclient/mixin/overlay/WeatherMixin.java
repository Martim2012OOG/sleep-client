package dev.solclient.mixin.overlay;

import dev.solclient.mods.WeatherMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Changer: what the client sees for rain / thunder. */
@Mixin(targets = "net.minecraft.world.World")
public abstract class WeatherMixin {

	@Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$rain(float delta, CallbackInfoReturnable<Float> cir) {
		float r = dev.solclient.mods.ChristmasWorldMod.snowing() ? dev.solclient.mods.ChristmasWorldMod.snowAmount() : WeatherMod.rain(); // Christmas World
		if (r >= 0F && ((Object) this) instanceof net.minecraft.client.world.ClientWorld) {
			cir.setReturnValue(r);
		}
	}

	@Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true, require = 0)
	private void plegumo$thunder(float delta, CallbackInfoReturnable<Float> cir) {
		float t = WeatherMod.thunder();
		if (t >= 0F && ((Object) this) instanceof net.minecraft.client.world.ClientWorld) {
			cir.setReturnValue(t);
		}
	}
}
