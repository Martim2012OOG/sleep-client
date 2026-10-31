package dev.solclient.mixin.extra;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.hud.bar.LocatorBar;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.waypoint.EntityTickProgress;
import net.minecraft.world.waypoint.TrackedWaypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The locator bar (the dots over the experience bar) shows the player's head instead of a coloured dot. Optional (require = 0). */
@Mixin(LocatorBar.class)
public abstract class LocatorHeadMixin {

	@Unique
	private static TrackedWaypoint kubo$wp;

	@Inject(method = "method_70870", at = @At("HEAD"), require = 0)
	private void kubo$grab(Entity viewer, World world, EntityTickProgress tick, DrawContext c, int y, TrackedWaypoint wp, CallbackInfo ci) {
		kubo$wp = wp;
	}

	@Redirect(method = "method_70870", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V", ordinal = 0), require = 0)
	private void kubo$icon(DrawContext c, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int colour) {
		try {
			TrackedWaypoint wp = kubo$wp;
			if (wp != null && MinecraftClient.getInstance().getNetworkHandler() != null) {
				var id = wp.getSource().left();
				if (id.isPresent()) {
					PlayerListEntry e = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(id.get());
					if (e != null) {
						PlayerSkinDrawer.draw(c, e.getSkinTextures(), x, y, 9);
						return;
					}
				}
			}
		} catch (RuntimeException ignored) {
			// fall back to the coloured dot
		}
		c.drawGuiTexture(pipeline, sprite, x, y, w, h, colour);
	}
}
