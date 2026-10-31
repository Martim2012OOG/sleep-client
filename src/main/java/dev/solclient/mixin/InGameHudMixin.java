package dev.solclient.mixin;

import dev.solclient.mods.CrosshairMod;
import dev.solclient.mods.OverlayMod;
import dev.solclient.mods.ScoreboardMod;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(InGameHud.class)
public class InGameHudMixin {

	/**
	 * While resource packs reload, the game's own HUD (chat, hotbar, boss bar) is not drawn: its letters and pictures are
	 * being swapped and showed as boxes over the loading screen.
	 */
	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$noHudWhileLoading(CallbackInfo ci) {
		var mc = net.minecraft.client.MinecraftClient.getInstance();
		if (mc.getOverlay() != null && dev.solclient.SolClient.settings != null && dev.solclient.SolClient.settings.hideHudWhileLoading.on()) {
			ci.cancel();
		}
	}

	/** Hides the vanilla crosshair; CrosshairMod draws its own one. */
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$hideCrosshair(CallbackInfo ci) {
		if (CrosshairMod.replacesVanilla()) {
			ci.cancel();
		}
	}

	/** Hides the vanilla sidebar; ScoreboardMod draws its own one. */
	@Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$hideScoreboard(CallbackInfo ci) {
		if (ScoreboardMod.replacesVanilla()) {
			ci.cancel();
		}
	}

	/** Pumpkin / spyglass / powder snow overlay opacity: renderOverlay(DrawContext, Identifier, float). */
	@ModifyArgs(method = {"renderMiscOverlays", "render"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V"), require = 0)
	private void sol$overlayOpacity(Args args) {
		args.set(2, OverlayMod.overlayOpacity(String.valueOf(args.get(1)), (float) args.get(2)));
	}
}
