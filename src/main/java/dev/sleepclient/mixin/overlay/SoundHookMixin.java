package dev.sleepclient.mixin.overlay;

import dev.sleepclient.ext.KuboApi;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells extensions about sounds the server plays at a point (for example a mace smash). Only reads the packet. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class SoundHookMixin {

	@Inject(method = "onPlaySound", at = @At("HEAD"), require = 0)
	private void kubo$sound(PlaySoundS2CPacket packet, CallbackInfo ci) {
		try {
			// the packet is handled twice (network thread, then game thread): only the game thread counts
			if (!net.minecraft.client.MinecraftClient.getInstance().isOnThread()) {
				return;
			}
			String id = packet.getSound().getKey().map(k -> k.getValue().toString()).orElse(null);
			if (id != null) {
				KuboApi.worldSound(id, packet.getX(), packet.getY(), packet.getZ());
			}
		} catch (Throwable ignored) {
		}
	}
}
