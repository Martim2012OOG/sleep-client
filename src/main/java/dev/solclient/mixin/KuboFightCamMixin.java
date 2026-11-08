package dev.solclient.mixin;

import dev.solclient.mods.FightCamMod;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves the camera for FightCam after the game has placed it. */
@Mixin(Camera.class)
public abstract class KuboFightCamMixin {

	@Shadow
	protected abstract void setPos(Vec3d pos);

	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Inject(method = "update", at = @At("RETURN"), require = 0)
	private void kubo$fightCam(CallbackInfo ci) {
		double[] cut = dev.solclient.ext.KuboApi.camera();
		if (cut != null) {
			this.setPos(new Vec3d(cut[0], cut[1], cut[2]));
			this.setRotation((float) cut[3], (float) cut[4]);
			return;
		}
		if (dev.solclient.gui.PhotoModeScreen.active()) {
			double[] p = dev.solclient.gui.PhotoModeScreen.pose();
			this.setPos(new Vec3d(p[0], p[1], p[2]));
			this.setRotation((float) p[3], (float) p[4]);
			return;
		}
		if (dev.solclient.fightcam.Replay.active()) {
			double[] p = dev.solclient.fightcam.Replay.pose();
			this.setPos(new Vec3d(p[0], p[1], p[2]));
			this.setRotation((float) p[3], (float) p[4]);
			return;
		}
		if (FightCamMod.camActive()) {
			double[] p = FightCamMod.pose();
			if (p != null) {
				this.setPos(new Vec3d(p[0], p[1], p[2]));
				this.setRotation((float) p[3], (float) p[4]);
			}
			return;
		}
		if (dev.solclient.mods.CinematicAfkMod.camActive()) {
			double[] p = dev.solclient.mods.CinematicAfkMod.pose();
			if (p != null) {
				this.setPos(new Vec3d(p[0], p[1], p[2]));
				this.setRotation((float) p[3], (float) p[4]);
			}
		}
	}
}
