package dev.solclient.mods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.solclient.Gfx;
import dev.solclient.Keys;
import dev.solclient.option.Option;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/** Lunar-style waypoints: markers on screen with name and distance, saved per server and dimension. */
public class WaypointsMod extends Mod {

	public static WaypointsMod instance;
	/** Real FOV used for the world projection (recorded by GameRendererMixin). */
	public static volatile float worldFov = 70F;

	private final Option.Key addKey = key("Add Waypoint Key", 78); // N
	private final Option.Key removeKey = key("Remove Nearest Key", -1);
	private final Option.Bool showName = bool("Show Name", true);
	private final Option.Bool showDistance = bool("Show Distance", true);
	private final Option.Num maxDistance = num("Max Distance", 5000, 50, 10000, 50, " blocks");
	private final Option.Num markerSize = num("Marker Size", 100, 50, 200, 10, "%");
	private final Option.Bool deathPoint = bool("Death Waypoint", true);
	private final Option.Bool deathMessage = bool("Death Coordinates In Chat", true);
	private final Option.Bool autoRemoveDeath = bool("Remove Death Point When Reached", true);
	private final Option.Action clear = action("Waypoints Here", "Clear", this::clearHere, () -> here().size() + " saved");

	public static final class Point {
		public String name;
		public double x, y, z;
		public String dim;
		public int color;
		public boolean death;
	}

	private static final int[] PALETTE = {0xFF4F8CFF, 0xFF3DDC84, 0xFFFFC23D, 0xFFB46CFF, 0xFFFF7AB6, 0xFF39D0E0, 0xFFFF8A3D};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private Map<String, List<Point>> data;
	private boolean addDown, removeDown, wasDead;

	public WaypointsMod() {
		super("Waypoints", "waypoints", "Mark places and see them on screen with their distance. Press N to add one.", Category.UTILITY);
		instance = this;
	}

	// ------------------------------------------------------------------ storage

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("plegumo-waypoints.json");
	}

	private Map<String, List<Point>> data() {
		if (data == null) {
			data = new HashMap<>();
			try {
				Path f = file();
				if (Files.exists(f)) {
					Map<String, List<Point>> m = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
							new TypeToken<Map<String, List<Point>>>() {}.getType());
					if (m != null) {
						data = m;
					}
				}
			} catch (Exception ignored) {
			}
		}
		return data;
	}

	private void save() {
		try {
			Files.writeString(file(), GSON.toJson(data()), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	private String worldKey() {
		try {
			if (mc.isInSingleplayer() && mc.getServer() != null) {
				return "sp:" + mc.getServer().getSaveProperties().getLevelName();
			}
			if (mc.getCurrentServerEntry() != null) {
				return "mp:" + mc.getCurrentServerEntry().address.toLowerCase(java.util.Locale.ROOT);
			}
		} catch (RuntimeException ignored) {
		}
		return "unknown";
	}

	private String dim() {
		return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
	}

	private List<Point> all() {
		return data().computeIfAbsent(worldKey(), k -> new ArrayList<>());
	}

	/** Waypoints of the current world and dimension. */
	private List<Point> here() {
		List<Point> out = new ArrayList<>();
		if (mc.world == null) {
			return out;
		}
		String d = dim();
		for (Point p : all()) {
			if (d.equals(p.dim)) {
				out.add(p);
			}
		}
		return out;
	}

	/** Waypoints of the current world and dimension (used by the minimap and the full map). */
	public List<Point> pointsHere() {
		return here();
	}

	/** Adds a waypoint at a place (from the full map). */
	public void addPointAt(String name, double x, double y, double z) {
		if (mc.world == null) {
			return;
		}
		int n = here().size() + 1;
		add(name == null || name.isEmpty() ? "Waypoint " + n : name, new Vec3d(x, y, z), PALETTE[(n - 1) % PALETTE.length], false);
	}

	/** Removes the waypoint closest to (x, z) within radius blocks. */
	public boolean removeNear(double x, double z, double radius) {
		Point best = null;
		double bd = radius * radius;
		for (Point p : here()) {
			double d = (p.x - x) * (p.x - x) + (p.z - z) * (p.z - z);
			if (d <= bd) {
				bd = d;
				best = p;
			}
		}
		if (best == null) {
			return false;
		}
		all().remove(best);
		save();
		return true;
	}

	/** The waypoint closest to (x, z) within radius blocks, or null (used by the right-click menu of the full map). */
	public Point pointNear(double x, double z, double radius) {
		Point best = null;
		double bd = radius * radius;
		for (Point p : here()) {
			double d = (p.x - x) * (p.x - x) + (p.z - z) * (p.z - z);
			if (d <= bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/** Gives a waypoint a new name. */
	public void rename(Point p, String name) {
		if (p == null || name == null || name.isBlank()) {
			return;
		}
		p.name = name.strip();
		save();
	}

	/** Removes this waypoint. */
	public void remove(Point p) {
		if (p == null) {
			return;
		}
		all().remove(p);
		save();
	}

	private void clearHere() {
		if (mc.world == null) {
			return;
		}
		String d = dim();
		all().removeIf(p -> d.equals(p.dim));
		save();
	}

	private void add(String name, Vec3d pos, int color, boolean death) {
		Point p = new Point();
		p.name = name;
		p.x = Math.floor(pos.x) + 0.5;
		p.y = Math.floor(pos.y);
		p.z = Math.floor(pos.z) + 0.5;
		p.dim = dim();
		p.color = color;
		p.death = death;
		List<Point> l = all();
		if (death) {
			l.removeIf(o -> o.death && p.dim.equals(o.dim));
		}
		l.add(p);
		save();
	}

	/** A waypoint a friend sent in the Sleep chat: [name, x, y, z, dimension, server]. False if you are not in that world. */
	public boolean addShared(String[] d) {
		if (mc.world == null || d.length < 5) {
			return false;
		}
		String server = d.length > 5 ? d[5].toLowerCase(java.util.Locale.ROOT) : "";
		String here = worldKey();
		if (!server.isEmpty() && !server.equals("singleplayer") && !here.equals("mp:" + server)) {
			return false;
		}
		try {
			Point p = new Point();
			p.name = d[0];
			p.x = Integer.parseInt(d[1].trim()) + 0.5;
			p.y = Integer.parseInt(d[2].trim());
			p.z = Integer.parseInt(d[3].trim()) + 0.5;
			p.dim = d[4];
			p.color = PALETTE[Math.floorMod(d[0].hashCode(), PALETTE.length)];
			all().add(p);
			save();
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private void chat(Text t) {
		if (mc.inGameHud != null) {
			mc.inGameHud.getChatHud().addMessage(Text.empty().append(Text.literal("[Waypoints] ").formatted(Formatting.AQUA)).append(t));
		}
	}

	// ------------------------------------------------------------------ logic

	@Override
	public void tick() {
		if (mc.player == null || mc.world == null) {
			return;
		}
		boolean add = mc.currentScreen == null && Keys.down(addKey.code());
		if (add && !addDown) {
			int n = here().size() + 1;
			add("Waypoint " + n, new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), PALETTE[(n - 1) % PALETTE.length], false);
			chat(Text.literal("Added Waypoint " + n + " at " + mc.player.getBlockPos().toShortString()));
		}
		addDown = add;

		boolean rem = mc.currentScreen == null && Keys.down(removeKey.code());
		if (rem && !removeDown) {
			Point best = null;
			double bd = Double.MAX_VALUE;
			for (Point p : here()) {
				double d = mc.player.squaredDistanceTo(p.x, p.y, p.z);
				if (d < bd) {
					bd = d;
					best = p;
				}
			}
			if (best != null) {
				all().remove(best);
				save();
				chat(Text.literal("Removed " + best.name));
			}
		}
		removeDown = rem;

		boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0;
		if (dead && !wasDead) {
			Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
			if (deathPoint.on()) {
				add("Death", pos, 0xFFE5484D, true);
			}
			if (deathMessage.on()) {
				chat(Text.literal("You died at " + (int) Math.floor(pos.x) + " " + (int) Math.floor(pos.y) + " " + (int) Math.floor(pos.z)));
			}
		}
		wasDead = dead;

		if (autoRemoveDeath.on() && !dead) {
			String d = dim();
			boolean changed = all().removeIf(p -> p.death && d.equals(p.dim) && mc.player.squaredDistanceTo(p.x, p.y, p.z) < 9);
			if (changed) {
				save();
			}
		}
	}

	public void draw(DrawContext c) {
		if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
			return;
		}
		List<Point> pts = here();
		if (pts.isEmpty()) {
			return;
		}
		float td = mc.getRenderTickCounter().getTickProgress(true);
		Vec3d cam = mc.player.getCameraPosVec(td);
		var camera = mc.gameRenderer.getCamera();
		double yaw = Math.toRadians(camera.getYaw()), pitch = Math.toRadians(camera.getPitch());
		// forward, right and up vectors of the camera
		double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
		double ux = -(rz * fy), uy = rz * fx - rx * fz, uz = rx * fy;
		int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
		double focal = (sh / 2.0) / Math.tan(Math.toRadians(Math.max(5F, worldFov)) / 2.0);
		double max = maxDistance.f();
		float ms = markerSize.f() / 100F;

		for (Point p : pts) {
			double dx = p.x - cam.x, dy = p.y + 1.0 - cam.y, dz = p.z - cam.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist > max) {
				continue;
			}
			double zc = dx * fx + dy * fy + dz * fz;
			double xc = dx * rx + dz * rz;
			double yc = dx * ux + dy * uy + dz * uz;
			if (zc < 0.1) {
				continue;
			}
			int sx = (int) Math.round(sw / 2.0 + xc / zc * focal);
			int sy = (int) Math.round(sh / 2.0 - yc / zc * focal);
			if (sx < -50 || sx > sw + 50 || sy < -50 || sy > sh + 50) {
				continue;
			}
			drawMarker(c, p, sx, sy, dist, ms);
		}
	}

	private void drawMarker(DrawContext c, Point p, int sx, int sy, double dist, float ms) {
		c.getMatrices().pushMatrix();
		c.getMatrices().translate(sx, sy);
		c.getMatrices().scale(ms, ms);
		// diamond
		c.getMatrices().pushMatrix();
		c.getMatrices().rotate((float) Math.toRadians(45));
		c.fill(-5, -5, 5, 5, 0xFF0A0B10);
		c.fill(-4, -4, 4, 4, p.color | 0xFF000000);
		c.fill(-2, -2, 2, 2, Gfx.blend(p.color | 0xFF000000, 0xFFFFFFFF, 0.45F));
		c.getMatrices().popMatrix();
		String label = "";
		if (showName.on()) {
			label = p.name;
		}
		if (showDistance.on()) {
			String d = dist >= 1000 ? String.format("%.1fkm", dist / 1000) : Math.round(dist) + "m";
			label = label.isEmpty() ? d : label + " · " + d;
		}
		if (!label.isEmpty()) {
			int w = mc.textRenderer.getWidth(label);
			int y = -19;
			c.fill(-w / 2 - 4, y - 2, w / 2 + 4, y + 10, 0xA0000000);
			c.fill(-w / 2 - 4, y + 10, w / 2 + 4, y + 11, p.color | 0xFF000000);
			c.drawText(mc.textRenderer, label, -w / 2, y, 0xFFFFFFFF, true);
		}
		c.getMatrices().popMatrix();
	}
}
