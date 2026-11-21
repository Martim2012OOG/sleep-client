package dev.sleepclient.mixin;

import dev.sleepclient.mods.CrosshairMod;
import dev.sleepclient.mods.OverlayMod;
import dev.sleepclient.mods.ScoreboardMod;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Gui.class)
public class InGameHudMixin {

	/**
	 * While resource packs reload, the game's own HUD (chat, hotbar, boss bar) is not drawn: its letters and pictures are
	 * being swapped and showed as boxes over the loading screen.
	 */
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
	private void kubo$noHudWhileLoading(CallbackInfo ci) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (dev.sleepclient.SleepClient.loadingScreenUp(mc) && dev.sleepclient.SleepClient.settings != null && dev.sleepclient.SleepClient.settings.hideHudWhileLoading.on()) {
			ci.cancel();
		}
	}

	/** Safety net: if the Fabric HUD callback does not run on this computer, Sleep's HUD is drawn from here instead. */
	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
	private void kubo$hudBackup(net.minecraft.client.gui.GuiGraphicsExtractor context, net.minecraft.client.DeltaTracker tickCounter, CallbackInfo ci) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (!dev.sleepclient.SleepClient.loadingScreenUp(mc)) {
			dev.sleepclient.SleepClient.hudBackup(context);
		}
	}

	/** Hides the vanilla crosshair; CrosshairMod draws its own one. */
	@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$hideCrosshair(CallbackInfo ci) {
		if (CrosshairMod.replacesVanilla()) {
			ci.cancel();
		}
	}

	/** Hides the vanilla sidebar; ScoreboardMod draws its own one. */
	@Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
	private void sol$hideScoreboard(CallbackInfo ci) {
		if (ScoreboardMod.replacesVanilla()) {
			ci.cancel();
		}
	}

	/** Pumpkin / spyglass / powder snow overlay opacity: renderOverlay(DrawContext, Identifier, float). */
	@ModifyArgs(method = {"extractCameraOverlays", "extractRenderState"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V"), require = 0)
	private void sol$overlayOpacity(Args args) {
		Object id = args.get(1); float op = args.<Float>get(2); args.set(2, OverlayMod.overlayOpacity(String.valueOf(id), op));
	}
}
