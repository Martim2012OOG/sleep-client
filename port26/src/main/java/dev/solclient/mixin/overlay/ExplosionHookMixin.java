package dev.solclient.mixin.overlay;

import dev.solclient.ext.KuboApi;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells extensions where an explosion happened (before Minecraft handles it). Only reads the packet. */
@Mixin(ClientPacketListener.class)
public abstract class ExplosionHookMixin {

	@Inject(method = "handleExplosion", at = @At("HEAD"), require = 0)
	private void kubo$explosion(ClientboundExplodePacket packet, CallbackInfo ci) {
		try {
			// the packet is handled twice (network thread, then game thread): only the game thread counts
			if (!net.minecraft.client.Minecraft.getInstance().isSameThread()) {
				return;
			}
			// found by type, so a renamed accessor in another mapping version still works
			for (var m : packet.getClass().getMethods()) {
				if (m.getParameterCount() == 0 && m.getReturnType() == Vec3.class) {
					Vec3 at = (Vec3) m.invoke(packet);
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
