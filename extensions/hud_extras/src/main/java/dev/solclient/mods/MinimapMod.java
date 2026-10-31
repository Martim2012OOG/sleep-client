package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.gui.WorldMapScreen;
import dev.solclient.map.MapIcons;
import dev.solclient.map.MapStore;
import dev.solclient.map.MapView;
import dev.solclient.option.Option;
import java.util.Locale;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.Registries;
import net.minecraft.world.LightType;

/**
 * Minimap: a sharp live map of the land around you (every block is drawn as real pixels, not as a few big squares).
 * Round / square / rounded, rotates with you, shows players and mobs as heads, your waypoints and a compass.
 * Drag it where you want in the HUD editor.
 */
public class MinimapMod extends HudMod {

	public static MinimapMod instance;

	/** Mobs that only fight back / in some situations (yellow on the map). Checked by id, so it does not depend on game class names. */
	private static final java.util.Set<String> NEUTRAL = java.util.Set.of("wolf", "bee", "iron_golem", "polar_bear", "llama", "trader_llama",
			"panda", "zombified_piglin", "enderman", "dolphin", "goat", "spider", "cave_spider");

	private final Option.Header hLook = header("Look");
	private final Option.Choice style = choice("Map Style", 0, "3D", "Flat (top view)").tip("3D: the land has height and you see the side of hills, trees and buildings.");
	private final Option.Num height3d = num("3D Height", 100, 40, 160, 10, "%");
	private final Option.Choice shape = choice("Shape", 0, "Circle", "Square", "Rounded");
	private final Option.Num size = num("Size", 128, 64, 256, 8, " px");
	private final Option.Num border = num("Border Width", 2, 0, 6, 1, " px");
	private final Option.Col borderColour = colour("Border Colour", 0xFF1B1E27);
	private final Option.Num opacity = num("Opacity", 100, 30, 100, 5, "%");
	private final Option.Num brightness = num("Brightness", 105, 60, 150, 5, "%");
	private final Option.Bool vignette = bool("Soft Edge Shadow", true);
	private final Option.Choice unexplored = choice("Unexplored Area", 0, "Light Fog", "Dark");

	private final Option.Header hMap = header("Map");
	private final Option.Bool rotate = bool("Rotate With Player", true);
	private final Option.Num zoom = num("Visible Blocks", 96, 24, 320, 8, " blocks");
	private final Option.Choice caves = choice("Cave View", 0, "Auto (when underground)", "Off", "Always");
	private final Option.Choice rate = choice("Update Rate", 2, "Every Frame", "60 per second", "30 per second");
	private final Option.Key zoomIn = key("Zoom In Key", 334);
	private final Option.Key zoomOut = key("Zoom Out Key", 333);
	private final Option.Key mapKey = key("Full Map Key", 77);
	private final Option.Action openMap = action("Full Map", "Open", this::openFull, () -> "Right-click adds a waypoint");

	private final Option.Header hYou = header("You");
	private final Option.Choice selfStyle = choice("Your Marker", 0, "Arrow", "Head", "Dot");
	private final Option.Num selfSize = num("Your Marker Size", 100, 50, 250, 10, "%");
	private final Option.Col selfColour = colour("Your Arrow Colour", 0xFFFFFFFF);
	private final Option.Col selfAccent = colour("Your Arrow Accent", 0xFF3D9BFF);

	private final Option.Header hPlayers = header("Other Players");
	private final Option.Bool players = bool("Show Players", true);
	private final Option.Choice playerStyle = choice("Player Marker", 1, "Arrow", "Head", "Dot");
	private final Option.Num playerSize = num("Player Marker Size", 100, 50, 250, 10, "%");
	private final Option.Col playerColour = colour("Player Frame Colour", 0xFFFFFFFF);
	private final Option.Bool playerNames = bool("Player Names", false);

	private final Option.Header hMobs = header("Mobs");
	private final Option.Bool mobs = bool("Show Mobs", true);
	private final Option.Bool mobPassive = bool("Passive Mobs (green)", true);
	private final Option.Bool mobNeutral = bool("Neutral Mobs (yellow)", true);
	private final Option.Bool mobHostile = bool("Hostile Mobs (red)", true);
	private final Option.Num mobSize = num("Mob Marker Size", 100, 50, 250, 10, "%");
	private final Option.Col passiveColour = colour("Passive Frame Colour", 0xFF3DDC84);
	private final Option.Col neutralColour = colour("Neutral Frame Colour", 0xFFFFC23D);
	private final Option.Col hostileColour = colour("Hostile Frame Colour", 0xFFE5484D);
	private final Option.Choice frame = choice("Icon Frame", 0, "Square", "Circle", "None");

	private final Option.Header hExtra = header("Waypoints & Info");
	private final Option.Bool waypoints = bool("Show Waypoints", true);
	private final Option.Num waypointSize = num("Waypoint Size", 100, 50, 250, 10, "%");
	private final Option.Bool waypointEdge = bool("Waypoints At The Edge", true);
	private final Option.Bool compass = bool("Compass Letters", true);
	private final Option.Bool coords = bool("Coordinates", true);
	private final Option.Bool biome = bool("Biome Name", true);

	private final MapView view = new MapView("minimap");
	{
		height3d.showIf(() -> style.idx() == 0);
	}
	private final MapView.P prm = new MapView.P();
	private int sliceY = Integer.MIN_VALUE;
	private int zoomTick;
	private boolean mapDown;
	/** Set by the full map: forces cave view on / off (null = follow the settings). */
	public Boolean forceCave;
	public int forceY;

	public MinimapMod() {
		super("Minimap", "minimap", "A sharp, customizable map of the land around you. Round or square, with heads of players and mobs.", 0.86F, 0.02F);
		instance = this;
	}

	// ------------------------------------------------------------------ small API for the full map
	public int mapKeyCode() {
		return mapKey.code();
	}

	/** True when the surface is drawn as a 3D view (cave slices are always flat). */
	public boolean threeD(boolean cave) {
		return style.idx() == 0 && !cave;
	}

	public float heightScale() {
		return height3d.f() / 100F;
	}

	public boolean showWaypoints() {
		return waypoints.on();
	}

	public boolean showMobs() {
		return mobs.on();
	}

	public boolean showPlayers() {
		return players.on();
	}

	public void toggleWaypoints() {
		waypoints.set(!waypoints.on());
	}

	public void toggleMobs() {
		mobs.set(!mobs.on());
	}

	public void togglePlayers() {
		players.set(!players.on());
	}

	public int fog() {
		return unexplored.idx() == 1 ? 0x1B1F2A : 0xDDE3EC;
	}

	public float bright() {
		return brightness.f() / 100F;
	}

	public void openFull() {
		if (mc.world != null && mc.player != null) {
			mc.setScreen(new WorldMapScreen(this));
		}
	}

	@Override
	protected void onDisable() {
		view.close();
		MapStore.I.flush();
	}

	// ------------------------------------------------------------------ logic
	/** Under a roof or underground: the map then shows the layer you are standing in instead of the surface. */
	public boolean caveActive() {
		if (forceCave != null) {
			return forceCave;
		}
		if (mc.world == null || mc.player == null) {
			return false;
		}
		var dim = mc.world.getDimension();
		if (dim.hasCeiling()) {
			return true;
		}
		if (caves.idx() == 1) {
			return false;
		}
		if (caves.idx() == 2) {
			return true;
		}
		if (!dim.hasSkyLight()) {
			return false;
		}
		try {
			return mc.world.getLightLevel(LightType.SKY, mc.player.getBlockPos().up()) <= 4;
		} catch (RuntimeException e) {
			return false;
		}
	}

	@Override
	public void tick() {
		var w = mc.world;
		var p = mc.player;
		if (w == null || p == null) {
			return;
		}
		int py = forceCave != null ? forceY : p.getBlockY();
		if (sliceY == Integer.MIN_VALUE || forceCave != null || Math.abs(py - sliceY) >= 2) {
			sliceY = py;
		}
		int rd = 10;
		try {
			rd = Math.min(12, mc.options.getViewDistance().getValue());
		} catch (RuntimeException ignored) {
		}
		MapStore.I.update(w, p.getX(), p.getZ(), Math.max(4, rd), 4, caveActive(), sliceY);
		boolean down = Keys.down(mapKey.code());
		if (mc.currentScreen == null) {
			if (down && !mapDown) {
				openFull();
			}
			if (++zoomTick % 3 == 0) {
				if (Keys.down(zoomIn.code())) {
					zoom.set(zoom.get() - 8F);
				}
				if (Keys.down(zoomOut.code())) {
					zoom.set(zoom.get() + 8F);
				}
			}
		}
		mapDown = down;
	}

	// ------------------------------------------------------------------ HUD
	@Override
	public int width() {
		return size.i();
	}

	@Override
	public int height() {
		return size.i() + (coords.on() ? 11 : 0) + (biome.on() ? 10 : 0);
	}

	@Override
	public boolean visible() {
		return mc.world != null && mc.player != null;
	}

	@Override
	protected void renderHud(DrawContext c, boolean edit) {
		if (mc.world == null || mc.player == null) {
			return;
		}
		int s = size.i();
		int gs = (int) Math.max(1, Math.round(mc.getWindow().getScaleFactor()));
		int tw = Math.max(96, Math.min(512, Math.round(s * s() * gs)));
		float ppb = tw / zoom.f();
		if (ppb >= 1.5F) {
			ppb = Math.round(ppb);
		}
		float td = mc.getRenderTickCounter().getTickProgress(true);
		var pos = mc.player.getLerpedPos(td);
		float yaw = mc.player.getYaw(td);
		float mapYaw = rotate.on() ? (float) Math.toRadians(yaw) : (float) Math.PI;

		prm.w = tw;
		prm.h = tw;
		prm.cave = caveActive();
		prm.cx = pos.x;
		prm.cz = pos.z;
		prm.ppb = ppb;
		prm.yaw = mapYaw;
		prm.shape = shape.idx();
		prm.border = border.f();
		prm.borderColour = borderColour.c() & 0xFFFFFF;
		prm.bright = bright();
		prm.vignette = vignette.on();
		prm.edge = true;
		prm.smooth = rotate.on() && ppb < 4F;
		prm.fog = fog();
		prm.threeD = threeD(prm.cave);
		prm.heightScale = heightScale();
		prm.cy = pos.y;
		prm.minMs = switch (rate.idx()) {
			case 1 -> 14;
			case 2 -> 30;
			default -> 0;
		};
		if (prm.threeD && prm.minMs < 33) {
			prm.minMs = 33; // the 3D map is redrawn at most 30 times a second: no FPS cost
		}
		view.render(MapStore.I, prm);
		int alpha = Math.round(opacity.f() * 2.55F);
		c.drawTexture(RenderPipelines.GUI_TEXTURED, view.id(), 0, 0, 0F, 0F, s, s, tw, tw, tw, tw, alpha << 24 | 0xFFFFFF);

		float ppbG = ppb * s / (float) tw;
		drawMarkers(c, pos.x, pos.z, ppbG, mapYaw, s / 2F, s / 2F, s / 2F, s / 2F, shape.idx(), false, td, prm.threeD, view.h0());
		if (compass.on()) {
			drawCompass(c, mapYaw, s);
		}
		int y = s + 2;
		if (coords.on()) {
			String t = mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
			c.drawText(mc.textRenderer, t, s - mc.textRenderer.getWidth(t), y, 0xFFFFFFFF, true);
			y += 11;
		}
		if (biome.on()) {
			String b = biomeName();
			c.drawText(mc.textRenderer, b, s - mc.textRenderer.getWidth(b), y, 0xFFE6E6E6, true);
		}
	}

	private String biomeName() {
		try {
			return mc.world.getBiome(mc.player.getBlockPos()).getKey().map(k -> pretty(k.getValue().getPath())).orElse("");
		} catch (RuntimeException e) {
			return "";
		}
	}

	private static String pretty(String path) {
		StringBuilder sb = new StringBuilder();
		boolean up = true;
		for (char ch : path.toCharArray()) {
			if (ch == '_') {
				sb.append(' ');
				up = true;
			} else {
				sb.append(up ? Character.toUpperCase(ch) : ch);
				up = false;
			}
		}
		return sb.toString();
	}

	private void drawCompass(DrawContext c, float mapYaw, int s) {
		float rx = (float) -Math.cos(mapYaw), rz = (float) -Math.sin(mapYaw), dx = (float) Math.sin(mapYaw), dz = (float) -Math.cos(mapYaw);
		String[] names = { "N", "E", "S", "W" };
		float[][] dirs = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };
		float half = s / 2F, reach = half - 6F;
		for (int i = 0; i < 4; i++) {
			float sx = dirs[i][0] * rx + dirs[i][1] * rz, sy = dirs[i][0] * dx + dirs[i][1] * dz;
			if (shape.idx() != 0) {
				float m = Math.max(Math.abs(sx), Math.abs(sy));
				sx /= m;
				sy /= m;
			}
			float x = half + sx * reach, y = half + sy * reach;
			int w = mc.textRenderer.getWidth(names[i]);
			int tx = Math.round(x - w / 2F), ty = Math.round(y - 4F);
			c.fill(tx - 2, ty - 1, tx + w + 2, ty + 9, 0x90000000);
			c.drawText(mc.textRenderer, names[i], tx, ty, i == 0 ? 0xFFFF5555 : 0xFFFFFFFF, false);
		}
	}

	// ------------------------------------------------------------------ markers (shared with the full map)
	private static boolean inside(float sx, float sy, float hw, float hh, int shp, float margin) {
		if (shp == 0) {
			float r = Math.min(hw, hh) - margin;
			return sx * sx + sy * sy <= r * r;
		}
		return Math.abs(sx) <= hw - margin && Math.abs(sy) <= hh - margin;
	}

	/**
	 * Draws waypoints, mobs, players and you. (wcx, wcz) = world point in the middle of the map, ppbG = GUI pixels
	 * per block, mapYaw = the map rotation (PI = north up), (ccx, ccy) = middle of the map on screen, hw / hh = half
	 * its size, shp = 0 circle / 1 square / 2 rounded.
	 */
	public void drawMarkers(DrawContext c, double wcx, double wcz, float ppbG, float mapYaw, float ccx, float ccy, float hw, float hh, int shp, boolean full, float td, boolean three, float h0) {
		float rx = (float) -Math.cos(mapYaw), rz = (float) -Math.sin(mapYaw), dx = (float) Math.sin(mapYaw), dz = (float) -Math.cos(mapYaw);
		// 3D view: things further "down" the picture are squeezed, and higher things are lifted (same numbers as MapView)
		final float ky = three ? MapView.KY : 1F, kh = three ? MapView.KH * heightScale() : 0F;
		float big = full ? 1.35F : 1F;

		if (waypoints.on() && WaypointsMod.instance != null) {
			float r = 5F * waypointSize.f() / 100F * big;
			for (WaypointsMod.Point wp : WaypointsMod.instance.pointsHere()) {
				float sx = (float) ((wp.x - wcx) * rx + (wp.z - wcz) * rz) * ppbG, sy = (float) ((wp.x - wcx) * dx + (wp.z - wcz) * dz) * ppbG * ky - (three ? lift(wp.y, h0, kh, ppbG) : 0F);
				if (!inside(sx, sy, hw, hh, shp, r + 1F)) {
					if (full || !waypointEdge.on()) {
						continue;
					}
					float k;
					if (shp == 0) {
						k = (Math.min(hw, hh) - r - 1F) / (float) Math.sqrt(sx * sx + sy * sy);
					} else {
						k = Math.min((hw - r - 1F) / Math.max(0.001F, Math.abs(sx)), (hh - r - 1F) / Math.max(0.001F, Math.abs(sy)));
					}
					sx *= k;
					sy *= k;
				}
				MapIcons.badge(c, ccx + sx, ccy + sy, r, wp.death ? 0xE5484D : wp.color, wp.death ? "X" : initials(wp.name));
			}
		}

		int frameStyle = frame.idx();
		int drawn = 0;
		java.util.List<int[]> labels = new java.util.ArrayList<>();
		if (mobs.on()) {
			float ms = 8F * mobSize.f() / 100F * big;
			for (Entity e : mc.world.getEntities()) {
				if (drawn >= 60) {
					break;
				}
				if (!(e instanceof MobEntity me) || !me.isAlive() || me.isInvisible()) {
					continue;
				}
				int kind = NEUTRAL.contains(Registries.ENTITY_TYPE.getId(me.getType()).getPath()) ? 1 : me instanceof Monster ? 2 : 0;
				if (kind == 0 && !mobPassive.on() || kind == 1 && !mobNeutral.on() || kind == 2 && !mobHostile.on()) {
					continue;
				}
				var ep = e.getLerpedPos(td);
				float sx = (float) ((ep.x - wcx) * rx + (ep.z - wcz) * rz) * ppbG, sy = (float) ((ep.x - wcx) * dx + (ep.z - wcz) * dz) * ppbG * ky - (three ? lift(ep.y - 1, h0, kh, ppbG) : 0F);
				if (!inside(sx, sy, hw, hh, shp, ms * 0.7F + 2F)) {
					continue;
				}
				int ring = kind == 2 ? hostileColour.c() : kind == 1 ? neutralColour.c() : passiveColour.c();
				MapIcons.icon(c, frameStyle, ccx + sx, ccy + sy, Math.round(ms), ring, (ix, iy, isz) -> MapIcons.mobHead(c, me, ix, iy, isz));
				drawn++;
			}
		}

		if (players.on()) {
			float ps = 10F * playerSize.f() / 100F * big;
			for (Entity e : mc.world.getEntities()) {
				if (e == mc.player || !(e instanceof AbstractClientPlayerEntity ap) || !ap.isAlive() || ap.isInvisible()) {
					continue;
				}
				var ep = ap.getLerpedPos(td);
				float sx = (float) ((ep.x - wcx) * rx + (ep.z - wcz) * rz) * ppbG, sy = (float) ((ep.x - wcx) * dx + (ep.z - wcz) * dz) * ppbG * ky - (three ? lift(ep.y - 1, h0, kh, ppbG) : 0F);
				if (!inside(sx, sy, hw, hh, shp, ps * 0.7F + 2F)) {
					continue;
				}
				float cx = ccx + sx, cy = ccy + sy;
				int style = playerStyle.idx();
				if (style == 1) {
					MapIcons.icon(c, frameStyle, cx, cy, Math.round(ps), playerColour.c(), (ix, iy, isz) -> net.minecraft.client.gui.PlayerSkinDrawer.draw(c, ap.getSkin(), ix, iy, isz));
				} else if (style == 0) {
					MapIcons.arrow(c, cx, cy, (float) Math.toRadians(ap.getYaw(td)) - mapYaw, ps * 1.2F, playerColour.c(), 0xFFFF5555);
				} else {
					MapIcons.dot(c, cx, cy, ps * 0.4F, playerColour.c());
				}
				if (full || playerNames.on()) {
					String n = ap.getName().getString();
					int w = mc.textRenderer.getWidth(n);
					int lx = Math.round(cx - w / 2F), ly = Math.round(cy + ps / 2F + 3F);
					// names never pile up on each other (a lobby full of players): a name that would overlap another one is skipped
					boolean free = true;
					for (int[] r : labels) {
						if (lx < r[2] && lx + w > r[0] && ly < r[3] && ly + 9 > r[1]) {
							free = false;
							break;
						}
					}
					if (free) {
						labels.add(new int[] {lx - 1, ly - 1, lx + w + 1, ly + 9});
						c.drawText(mc.textRenderer, n, lx, ly, 0xFFFFFFFF, true);
					}
				}
			}
		}

		// you, always on top
		var pp = mc.player.getLerpedPos(td);
		float px = ccx + (float) ((pp.x - wcx) * rx + (pp.z - wcz) * rz) * ppbG, py = ccy + (float) ((pp.x - wcx) * dx + (pp.z - wcz) * dz) * ppbG * ky - (three ? lift(pp.y - 1, h0, kh, ppbG) : 0F);
		float ss = 12F * selfSize.f() / 100F * big;
		int sstyle = selfStyle.idx();
		if (sstyle == 1) {
			MapIcons.icon(c, frameStyle, px, py, Math.round(ss * 0.9F), selfAccent.c(), (ix, iy, isz) -> net.minecraft.client.gui.PlayerSkinDrawer.draw(c, mc.player.getSkin(), ix, iy, isz));
		} else if (sstyle == 0) {
			MapIcons.arrow(c, px, py, (float) Math.toRadians(mc.player.getYaw(td)) - mapYaw, ss, selfColour.c(), selfAccent.c());
		} else {
			MapIcons.dot(c, px, py, ss * 0.32F, selfColour.c());
		}
	}

	/** How far up the picture a thing at height y is drawn in the 3D view (same clamp as MapView). */
	private static float lift(double y, float h0, float kh, float ppbG) {
		return (float) Math.max(-48.0, Math.min(64.0, y - h0)) * kh * ppbG;
	}

	private static String initials(String n) {
		if (n == null || n.isBlank()) {
			return "";
		}
		String[] parts = n.trim().split("\\s+");
		String a = parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
		if (parts.length > 1 && !parts[1].isEmpty()) {
			a += parts[1].substring(0, 1).toUpperCase(Locale.ROOT);
		}
		return a;
	}
}
