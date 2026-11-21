package dev.sleepclient.mixin.overlay;

import dev.sleepclient.fx.killfx.KillFxTrigger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** What servers tell the game around a kill, for the kill effects. Only reads, on the game thread. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class KillSignalsMixin {

	private static boolean sleep$ready() {
		return MinecraftClient.getInstance().isOnThread();
	}

	@Inject(method = "onEntityDamage", at = @At("HEAD"), require = 0)
	private void sleep$damage(EntityDamageS2CPacket p, CallbackInfo ci) {
		if (sleep$ready()) {
			KillFxTrigger.onDamage(p.entityId(), p.sourceCauseId());
		}
	}

	@Inject(method = "onGameMessage", at = @At("HEAD"), require = 0)
	private void sleep$message(GameMessageS2CPacket p, CallbackInfo ci) {
		if (sleep$ready()) {
			KillFxTrigger.onServerText(p.content().getString());
		}
	}

	@Inject(method = "onOverlayMessage", at = @At("HEAD"), require = 0)
	private void sleep$overlay(OverlayMessageS2CPacket p, CallbackInfo ci) {
		if (sleep$ready()) {
			KillFxTrigger.onServerText(p.text().getString());
		}
	}

	@Inject(method = "onTitle", at = @At("HEAD"), require = 0)
	private void sleep$title(TitleS2CPacket p, CallbackInfo ci) {
		if (sleep$ready()) {
			KillFxTrigger.onServerText(p.text().getString());
		}
	}

	@Inject(method = "onSubtitle", at = @At("HEAD"), require = 0)
	private void sleep$subtitle(SubtitleS2CPacket p, CallbackInfo ci) {
		if (sleep$ready()) {
			KillFxTrigger.onServerText(p.text().getString());
		}
	}

	@Inject(method = "onPlayerList", at = @At("HEAD"), require = 0)
	private void sleep$list(PlayerListS2CPacket p, CallbackInfo ci) {
		if (sleep$ready() && p.getActions().contains(PlayerListS2CPacket.Action.UPDATE_GAME_MODE)) {
			for (var e : p.getEntries()) {
				if (e.gameMode() == net.minecraft.world.GameMode.SPECTATOR) {
					KillFxTrigger.onSpectator(e.profileId());
				}
			}
		}
	}
}
