package dev.solclient.mixin.extra;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The locator bar (the dots over the experience bar) shows the player's head instead of a coloured dot. Optional (require = 0). */
@Mixin(targets = "net.minecraft.client.gui.contextualbar.LocatorBarRenderer")
public abstract class LocatorHeadMixin {

	@Unique
	private static TrackedWaypoint kubo$wp;
	@Unique
	private static Entity kubo$viewer;

	@Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
	private void kubo$grab(Entity viewer, Level world, PartialTickSupplier tick, GuiGraphicsExtractor c, int y, TrackedWaypoint wp, CallbackInfo ci) {
		kubo$wp = wp;
		kubo$viewer = viewer;
	}

	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", ordinal = 0), require = 0)
	private void kubo$icon(GuiGraphicsExtractor c, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int colour) {
		try {
			TrackedWaypoint wp = kubo$wp;
			if (wp != null && Minecraft.getInstance().getConnection() != null) {
				var id = wp.id().left();
				if (id.isPresent()) {
					PlayerInfo e = Minecraft.getInstance().getConnection().getPlayerInfo(id.get());
					if (e != null) {
						// like the vanilla bar: the farther the player, the smaller the head (big when close, a small one far away)
						int size = 9;
						try {
							Entity v = kubo$viewer;
							if (v != null) {
								double d = Math.sqrt(wp.distanceSquared(v));
								float t = (float) Math.max(0.0, Math.min(1.0, (d - 24.0) / 276.0));
								size = Math.round(11F - 6F * t);
							}
						} catch (RuntimeException ignored) {
							size = 9;
						}
						int off = (9 - size) / 2;
						PlayerFaceExtractor.extractRenderState(c, e.getSkin(), x + off, y + off, size);
						return;
					}
				}
			}
		} catch (RuntimeException ignored) {
			// fall back to the coloured dot
		}
		c.blitSprite(pipeline, sprite, x, y, w, h, colour);
	}
}
