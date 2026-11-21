package dev.sleepclient.mixin.overlay;

import dev.sleepclient.ext.KuboApi;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells extensions about sounds the server plays at a point (for example a mace smash). Only reads the packet. */
@Mixin(ClientPacketListener.class)
public abstract class SoundHookMixin {

	@Inject(method = "handleSoundEvent", at = @At("HEAD"), require = 0)
	private void kubo$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
		try {
			// the packet is handled twice (network thread, then game thread): only the game thread counts
			if (!net.minecraft.client.Minecraft.getInstance().isSameThread()) {
				return;
			}
			String id = packet.getSound().unwrapKey().map(k -> k.identifier().toString()).orElse(null);
			if (id != null) {
				KuboApi.worldSound(id, packet.getX(), packet.getY(), packet.getZ());
			}
		} catch (Throwable ignored) {
		}
	}
}
