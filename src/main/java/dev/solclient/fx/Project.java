package dev.solclient.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

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
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.gameRenderer == null) {
			return false;
		}
		var camera = mc.gameRenderer.getCamera();
		float td = mc.getRenderTickCounter().getTickProgress(true);
		Vec3d cam = mc.player.getCameraPosVec(td);
		cx = cam.x;
		cy = cam.y;
		cz = cam.z;
		double yaw = Math.toRadians(camera.getYaw()), pitch = Math.toRadians(camera.getPitch());
		fx = -Math.sin(yaw) * Math.cos(pitch);
		fy = -Math.sin(pitch);
		fz = Math.cos(yaw) * Math.cos(pitch);
		rx = -Math.cos(yaw);
		rz = -Math.sin(yaw);
		ux = -(rz * fy);
		uy = rz * fx - rx * fz;
		uz = rx * fy;
		sw = mc.getWindow().getScaledWidth();
		sh = mc.getWindow().getScaledHeight();
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
