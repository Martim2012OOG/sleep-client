package dev.solclient.mixin.overlay;

import dev.solclient.ext.KuboApi;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells extensions where an explosion happened (before Minecraft handles it). Only reads the packet. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ExplosionHookMixin {

	@Inject(method = "onExplosion", at = @At("HEAD"), require = 0)
	private void kubo$explosion(ExplosionS2CPacket packet, CallbackInfo ci) {
		try {
			// the packet is handled twice (network thread, then game thread): only the game thread counts
			if (!net.minecraft.client.MinecraftClient.getInstance().isOnThread()) {
				return;
			}
			// found by type, so a renamed accessor in another mapping version still works
			for (var m : packet.getClass().getMethods()) {
				if (m.getParameterCount() == 0 && m.getReturnType() == Vec3d.class) {
					Vec3d at = (Vec3d) m.invoke(packet);
					if (at != null) {
						KuboApi.explosion(at);
					}
					return;
				}
			}
		} catch (Throwable ignored) {
		}
	}
}
