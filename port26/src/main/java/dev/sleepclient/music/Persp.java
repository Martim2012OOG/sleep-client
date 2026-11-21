package dev.sleepclient.music;

/**
 * Tiny perspective helper for the lyrics: a flat sheet of text floats in front of the camera and is tilted in 3D
 * (yaw / pitch / roll). Each word gets the exact 2D position and the local 2x2 stretch of the projection, so far parts
 * look smaller and the near parts bigger, like text lying in the sky. No Minecraft classes here, so it is easy to test.
 */
public final class Persp {

	private Persp() {
	}

	/**
	 * Projects a point (u, v) of the sheet (screen pixels, v down, 0/0 = middle of the sheet).
	 * out = {x, y, dxdu, dydu, dxdv, dydv}: where the point lands (relative to the screen anchor) and how the sheet is
	 * stretched there.
	 */
	public static void project(float u, float v, float yaw, float pitch, float roll, float f, float[] out) {
		point(u, v, yaw, pitch, roll, f, out, 0);
		float[] a = new float[2], b = new float[2];
		point(u + 1F, v, yaw, pitch, roll, f, a, 0);
		point(u, v + 1F, yaw, pitch, roll, f, b, 0);
		out[2] = a[0] - out[0];
		out[3] = a[1] - out[1];
		out[4] = b[0] - out[0];
		out[5] = b[1] - out[1];
	}

	/** Scale of the sheet at (u, v): 1 = same size as flat, below 1 = further away. */
	public static float scale(float[] p) {
		return (float) Math.sqrt(Math.abs(p[2] * p[5] - p[3] * p[4]));
	}

	private static void point(float u, float v, float yaw, float pitch, float roll, float f, float[] o, int at) {
		double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch), cr = Math.cos(roll), sr = Math.sin(roll);
		// yaw: turns the sheet around its vertical axis (positive = right side goes away)
		double x1 = u * cy, z1 = u * sy;
		// pitch: leans it back (positive = top goes away)
		double y2 = v * cp + z1 * sp, z2 = -v * sp + z1 * cp;
		// roll: turns it in its own plane
		double x3 = x1 * cr - y2 * sr, y3 = x1 * sr + y2 * cr;
		double d = Math.max(0.35 * f, f + z2);
		o[at] = (float) (f * x3 / d);
		o[at + 1] = (float) (f * y3 / d);
	}
}
