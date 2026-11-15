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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

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
	private final Option.Key editKey = key("Waypoint Editor Key", 74); // J
	private final Option.Bool edgeArrows = bool("Arrows At The Screen Edge", true);
	private final Option.Action editor = action("Waypoint Editor", "Open", () -> dev.solclient.compat.Mc.setScreen(mc, new dev.solclient.gui.WaypointEditorScreen(dev.solclient.compat.Mc.screen(mc))), () -> here().size() + " here");
	private final Option.Action clear = action("Waypoints Here", "Clear", this::clearHere, () -> here().size() + " saved");

	public static final class Point {
		public String name;
		public double x, y, z;
		public String dim;
		public int color;
		public boolean death;
		/** Look of the marker (edited in the Waypoint Editor): 0 diamond, 1 triangle, 2 circle, 3 square, 4 pin. */
		public int shape;
		public int size = 100, opacity = 100;
		public boolean outline = true, showName = true, showDist = true, visible = true, onlyFacing, hideNear;
	}

	private static final int[] PALETTE = {0xFF4F8CFF, 0xFF3DDC84, 0xFFFFC23D, 0xFFB46CFF, 0xFFFF7AB6, 0xFF39D0E0, 0xFFFF8A3D};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private Map<String, List<Point>> data;
	private boolean addDown, removeDown, editDown, wasDead;

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
			if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
				return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
			}
			if (mc.getCurrentServer() != null) {
				return "mp:" + mc.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
			}
		} catch (RuntimeException ignored) {
		}
		return "unknown";
	}

	private String dim() {
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	private List<Point> all() {
		return data().computeIfAbsent(worldKey(), k -> new ArrayList<>());
	}

	/** Waypoints of the current world and dimension. */
	private List<Point> here() {
		List<Point> out = new ArrayList<>();
		if (mc.level == null) {
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

	public static final int[] COLOURS = PALETTE;

	/** Adds a waypoint where you stand (the editor's + button). */
	public Point addHere() {
		if (mc.player == null) {
			return null;
		}
		int n = here().size() + 1;
		add("Waypoint " + n, new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()), PALETTE[(n - 1) % PALETTE.length], false);
		List<Point> h = here();
		return h.isEmpty() ? null : h.get(h.size() - 1);
	}

	/** Saves after the editor changed a waypoint. */
	public void saveNow() {
		save();
	}

	/** Waypoints of the current world and dimension (used by the minimap and the full map). */
	public List<Point> pointsHere() {
		return here();
	}

	/** Adds a waypoint at a place (from the full map). */
	public void addPointAt(String name, double x, double y, double z) {
		if (mc.level == null) {
			return;
		}
		int n = here().size() + 1;
		add(name == null || name.isEmpty() ? "Waypoint " + n : name, new Vec3(x, y, z), PALETTE[(n - 1) % PALETTE.length], false);
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
		if (mc.level == null) {
			return;
		}
		String d = dim();
		all().removeIf(p -> d.equals(p.dim));
		save();
	}

	private void add(String name, Vec3 pos, int color, boolean death) {
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
		if (mc.level == null || d.length < 5) {
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

	private void chat(Component t) {
		if (mc.gui != null) {
			dev.solclient.compat.Mc.chat(mc).addClientSystemMessage(Component.empty().append(Component.literal("[Waypoints] ").withStyle(ChatFormatting.AQUA)).append(t));
		}
	}

	// ------------------------------------------------------------------ logic

	@Override
	public void tick() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		boolean add = dev.solclient.compat.Mc.screen(mc) == null && Keys.down(addKey.code());
		if (add && !addDown) {
			int n = here().size() + 1;
			add("Waypoint " + n, new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()), PALETTE[(n - 1) % PALETTE.length], false);
			chat(Component.literal("Added Waypoint " + n + " at " + mc.player.blockPosition().toShortString()));
		}
		addDown = add;

		boolean ed = dev.solclient.compat.Mc.screen(mc) == null && Keys.down(editKey.code());
		if (ed && !editDown) {
			dev.solclient.compat.Mc.setScreen(mc, new dev.solclient.gui.WaypointEditorScreen(null));
		}
		editDown = ed;

		boolean rem = dev.solclient.compat.Mc.screen(mc) == null && Keys.down(removeKey.code());
		if (rem && !removeDown) {
			Point best = null;
			double bd = Double.MAX_VALUE;
			for (Point p : here()) {
				double d = mc.player.distanceToSqr(p.x, p.y, p.z);
				if (d < bd) {
					bd = d;
					best = p;
				}
			}
			if (best != null) {
				all().remove(best);
				save();
				chat(Component.literal("Removed " + best.name));
			}
		}
		removeDown = rem;

		boolean dead = mc.player.isDeadOrDying() || mc.player.getHealth() <= 0;
		if (dead && !wasDead) {
			Vec3 pos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
			if (deathPoint.on()) {
				add("Death", pos, 0xFFE5484D, true);
			}
			if (deathMessage.on()) {
				chat(Component.literal("You died at " + (int) Math.floor(pos.x) + " " + (int) Math.floor(pos.y) + " " + (int) Math.floor(pos.z)));
			}
		}
		wasDead = dead;

		if (autoRemoveDeath.on() && !dead) {
			String d = dim();
			boolean changed = all().removeIf(p -> p.death && d.equals(p.dim) && mc.player.distanceToSqr(p.x, p.y, p.z) < 9);
			if (changed) {
				save();
			}
		}
	}

	public void draw(GuiGraphicsExtractor c) {
		if (mc.player == null || mc.level == null || mc.gameRenderer == null) {
			return;
		}
		List<Point> pts = here();
		if (pts.isEmpty()) {
			return;
		}
		float td = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		var camera = dev.solclient.compat.Mc.camera(mc);
		Vec3 cam = camera.position();
		double yaw = Math.toRadians(camera.yRot()), pitch = Math.toRadians(camera.xRot());
		double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
		double ux = -(rz * fy), uy = rz * fx - rx * fz, uz = rx * fy;
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		double focal = (sh / 2.0) / Math.tan(Math.toRadians(Math.max(5F, worldFov)) / 2.0);
		double max = maxDistance.f();
		float base = markerSize.f() / 100F;
		for (Point p : pts) {
			if (!p.visible) {
				continue;
			}
			double dx = p.x - cam.x, dy = p.y + 1.0 - cam.y, dz = p.z - cam.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist > max || p.hideNear && dist < 8) {
				continue;
			}
			double zc = dx * fx + dy * fy + dz * fz;
			double xc = dx * rx + dz * rz;
			double yc = dx * ux + dy * uy + dz * uz;
			if (p.onlyFacing && (zc <= 0 || zc / Math.max(0.001, dist) < 0.985)) {
				continue;
			}
			float sc = base * p.size / 100F;
			boolean on = zc > 0.1;
			double px = on ? sw / 2.0 + xc / zc * focal : 0, py = on ? sh / 2.0 - yc / zc * focal : 0;
			int m = 16;
			if (on && px >= m && px <= sw - m && py >= m + 12 && py <= sh - m) {
				drawMarker(c, p, (int) Math.round(px), (int) Math.round(py), dist, sc, true);
			} else if (edgeArrows.on()) {
				// off screen: a little arrow at the edge that points to it
				double ax = on ? px - sw / 2.0 : -xc, ay = on ? py - sh / 2.0 : yc;
				double len = Math.max(0.001, Math.hypot(ax, ay));
				ax /= len;
				ay /= len;
				double hw = sw / 2.0 - 22, hh = sh / 2.0 - 28;
				double t = Math.min(Math.abs(ax) < 1e-6 ? 1e9 : hw / Math.abs(ax), Math.abs(ay) < 1e-6 ? 1e9 : hh / Math.abs(ay));
				drawEdge(c, p, (int) Math.round(sw / 2.0 + ax * t), (int) Math.round(sh / 2.0 + ay * t), Math.atan2(ay, ax), dist, sc);
			}
		}
	}

	private static int alpha(int col, int pct) {
		return (Math.round(255 * Math.max(0, Math.min(100, pct)) / 100F) << 24) | (col & 0xFFFFFF);
	}

	/** One marker shape centred on (0,0), about 10 px. Used by the world and by the editor's preview. */
	public static void shape(GuiGraphicsExtractor c, int shape, int col, boolean outline, int pct) {
		int fill = alpha(col, pct), edge = alpha(0x0A0B10, pct), hi = alpha(Gfx.blend(col | 0xFF000000, 0xFFFFFFFF, 0.45F), pct);
		switch (shape) {
			case 1 -> { // triangle pointing down
				for (int i = 0; i < 10; i++) {
					int hw = 5 - i / 2;
					if (outline) {
						c.fill(-hw - 1, -6 + i, hw + 2, -5 + i, edge);
					}
					c.fill(-hw, -6 + i, hw + 1, -5 + i, i < 3 ? hi : fill);
				}
			}
			case 2 -> {
				if (outline) {
					Gfx.circle(c, 0, 0, 6, edge);
				}
				Gfx.circle(c, 0, 0, 5, fill);
				Gfx.circle(c, 0, 0, 2, hi);
			}
			case 3 -> {
				if (outline) {
					c.fill(-6, -6, 6, 6, edge);
				}
				c.fill(-5, -5, 5, 5, fill);
				c.fill(-2, -2, 2, 2, hi);
			}
			case 4 -> { // pin: a round head with a point below
				for (int i = 0; i < 6; i++) {
					int hw = 3 - i / 2;
					if (outline) {
						c.fill(-hw - 1, 1 + i, hw + 2, 2 + i, edge);
					}
					c.fill(-hw, 1 + i, hw + 1, 2 + i, fill);
				}
				if (outline) {
					Gfx.circle(c, 0, -2, 6, edge);
				}
				Gfx.circle(c, 0, -2, 5, fill);
				Gfx.circle(c, 0, -2, 2, hi);
			}
			default -> {
				c.pose().pushMatrix();
				c.pose().rotate((float) Math.toRadians(45));
				if (outline) {
					c.fill(-5, -5, 5, 5, edge);
				}
				c.fill(-4, -4, 4, 4, fill);
				c.fill(-2, -2, 2, 2, hi);
				c.pose().popMatrix();
			}
		}
	}

	private static String distText(double dist) {
		return dist >= 1000 ? String.format("%.1fkm", dist / 1000) : Math.round(dist) + "m";
	}

	/** Text under a marker: name and/or distance on a small dark pill with a line in the waypoint's colour. */
	public static void label(GuiGraphicsExtractor c, net.minecraft.client.gui.Font tr, Point p, double dist, int y) {
		String label = "";
		if (p.showName) {
			label = p.name;
		}
		if (p.showDist) {
			String d = distText(dist);
			label = label.isEmpty() ? d : label + " · " + d;
		}
		if (label.isEmpty()) {
			return;
		}
		int w = tr.width(label);
		c.fill(-w / 2 - 4, y - 2, w / 2 + 4, y + 10, alpha(0x000000, Math.round(p.opacity * 0.63F)));
		c.fill(-w / 2 - 4, y + 10, w / 2 + 4, y + 11, alpha(p.color, p.opacity));
		c.text(tr, label, -w / 2, y, alpha(0xFFFFFF, p.opacity), true);
	}

	private void drawMarker(GuiGraphicsExtractor c, Point p, int sx, int sy, double dist, float ms, boolean unused) {
		c.pose().pushMatrix();
		c.pose().translate(sx, sy);
		c.pose().scale(ms, ms);
		shape(c, p.shape, p.color, p.outline, p.opacity);
		label(c, mc.font, p, dist, -19);
		c.pose().popMatrix();
	}

	private void drawEdge(GuiGraphicsExtractor c, Point p, int sx, int sy, double ang, double dist, float ms) {
		c.pose().pushMatrix();
		c.pose().translate(sx, sy);
		c.pose().scale(Math.min(1F, ms), Math.min(1F, ms));
		c.pose().pushMatrix();
		c.pose().rotate((float) ang);
		int col = alpha(p.color, p.opacity);
		for (int i = 0; i < 6; i++) { // arrow head pointing to +x
			c.fill(6 - i - 1, -(5 - i), 6 - i + 1, (5 - i) + 1, col);
		}
		c.pose().popMatrix();
		String d = distText(dist);
		int w = mc.font.width(d);
		c.text(mc.font, d, -w / 2, sy > mc.getWindow().getGuiScaledHeight() / 2 ? -18 : 9, alpha(0xFFFFFF, p.opacity), true);
		c.pose().popMatrix();
	}
}
