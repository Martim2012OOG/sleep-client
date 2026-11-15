package dev.solclient.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * World position -> HUD position (the same maths the waypoints use). Call {@link #begin()} once per frame, then
 * {@link #to(double, double, double)} for each point: it fills {@link #sx}, {@link #sy}, {@link #dist} and returns
 * false when the point is behind the camera.
 */
public final class Project {

	private static double cx, cy, cz, fx, fy, fz, rx, rz, ux, uy, uz, focal;
	private static int sw, sh;
	public static int sx, sy;
	public static double dist;

	private Project() {
	}

	public static boolean begin() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameRenderer == null) {
			return false;
		}
		var camera = dev.solclient.compat.Mc.camera(mc);
		float td = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 cam = mc.player.getEyePosition(td);
		cx = cam.x;
		cy = cam.y;
		cz = cam.z;
		double yaw = Math.toRadians(camera.yRot()), pitch = Math.toRadians(camera.xRot());
		fx = -Math.sin(yaw) * Math.cos(pitch);
		fy = -Math.sin(pitch);
		fz = Math.cos(yaw) * Math.cos(pitch);
		rx = -Math.cos(yaw);
		rz = -Math.sin(yaw);
		ux = -(rz * fy);
		uy = rz * fx - rx * fz;
		uz = rx * fy;
		sw = mc.getWindow().getGuiScaledWidth();
		sh = mc.getWindow().getGuiScaledHeight();
		focal = (sh / 2.0) / Math.tan(Math.toRadians(Math.max(5F, dev.solclient.mods.WaypointsMod.worldFov)) / 2.0);
		return true;
	}

	public static boolean to(double x, double y, double z) {
		double dx = x - cx, dy = y - cy, dz = z - cz;
		dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double zc = dx * fx + dy * fy + dz * fz;
		if (zc < 0.1) {
			return false;
		}
		double xc = dx * rx + dz * rz;
		double yc = dx * ux + dy * uy + dz * uz;
		sx = (int) Math.round(sw / 2.0 + xc / zc * focal);
		sy = (int) Math.round(sh / 2.0 - yc / zc * focal);
		return sx > -80 && sx < sw + 80 && sy > -80 && sy < sh + 80;
	}

	/** Like {@link #to} but for points off screen gives the screen-edge direction instead (for pings). */
	public static boolean edge(double x, double y, double z, int margin) {
		boolean on = to(x, y, z);
		double dx = x - cx, dy = y - cy, dz = z - cz;
		double zc = dx * fx + dy * fy + dz * fz;
		if (on && sx >= margin && sx <= sw - margin && sy >= margin && sy <= sh - margin) {
			return true;
		}
		double xc = dx * rx + dz * rz, yc = dx * ux + dy * uy + dz * uz;
		if (zc < 0) {
			xc = -xc;
			yc = -yc;
		}
		double ang = Math.atan2(-yc, xc);
		double hw = sw / 2.0 - margin, hh = sh / 2.0 - margin;
		double c = Math.cos(ang), s = Math.sin(ang);
		double k = Math.min(hw / Math.max(1e-6, Math.abs(c)), hh / Math.max(1e-6, Math.abs(s)));
		sx = (int) Math.round(sw / 2.0 + c * k);
		sy = (int) Math.round(sh / 2.0 + s * k);
		return false;
	}
}
