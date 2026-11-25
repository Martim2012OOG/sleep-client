package dev.solclient.mixin.overlay;

import dev.solclient.fx.killfx.KillFxTrigger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** What servers tell the game around a kill, for the kill effects. Only reads, on the game thread. */
@Mixin(ClientPacketListener.class)
public abstract class KillSignalsMixin {

	private static boolean caos$ready() {
		return Minecraft.getInstance().isSameThread();
	}

	@Inject(method = "handleDamageEvent", at = @At("HEAD"), require = 0)
	private void caos$damage(ClientboundDamageEventPacket p, CallbackInfo ci) {
		if (caos$ready()) {
			KillFxTrigger.onDamage(p.entityId(), p.sourceCauseId());
		}
	}

	@Inject(method = "handleSystemChat", at = @At("HEAD"), require = 0)
	private void caos$message(ClientboundSystemChatPacket p, CallbackInfo ci) {
		if (caos$ready()) {
			KillFxTrigger.onServerText(p.content().getString());
		}
	}

	@Inject(method = "setActionBarText", at = @At("HEAD"), require = 0)
	private void caos$overlay(ClientboundSetActionBarTextPacket p, CallbackInfo ci) {
		if (caos$ready()) {
			KillFxTrigger.onServerText(p.text().getString());
		}
	}

	@Inject(method = "setTitleText", at = @At("HEAD"), require = 0)
	private void caos$title(ClientboundSetTitleTextPacket p, CallbackInfo ci) {
		if (caos$ready()) {
			KillFxTrigger.onServerText(p.text().getString());
		}
	}

	@Inject(method = "setSubtitleText", at = @At("HEAD"), require = 0)
	private void caos$subtitle(ClientboundSetSubtitleTextPacket p, CallbackInfo ci) {
		if (caos$ready()) {
			KillFxTrigger.onServerText(p.text().getString());
		}
	}

	@Inject(method = "handlePlayerInfoUpdate", at = @At("HEAD"), require = 0)
	private void caos$list(ClientboundPlayerInfoUpdatePacket p, CallbackInfo ci) {
		if (caos$ready() && p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE)) {
			for (var e : p.entries()) {
				if (e.gameMode() == net.minecraft.world.level.GameType.SPECTATOR) {
					KillFxTrigger.onSpectator(e.profileId());
				}
			}
		}
	}
}
