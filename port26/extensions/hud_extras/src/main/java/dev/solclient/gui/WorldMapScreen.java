package dev.solclient.gui;

import dev.solclient.map.MapStore;
import dev.solclient.map.MapView;
import dev.solclient.mods.MinimapMod;
import dev.solclient.mods.WaypointsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The big map. Drag to move, scroll to zoom, right-click to add a waypoint (right-click on a waypoint opens
 * Rename / Remove; Shift + right-click still removes it straight away), U waypoints, C caves, PgUp / PgDn change the cave slice, Space goes back to you.
 */
public class WorldMapScreen extends Screen {

	private static final float[] STEPS = { 0.25F, 0.4F, 0.6F, 0.8F, 1F, 1.5F, 2F, 3F, 4F, 6F, 8F, 12F, 16F };
	private final Minecraft mc = Minecraft.getInstance();
	private final MinimapMod mod;
	private final MapView view = new MapView("fullmap");
	private final MapView.P prm = new MapView.P();
	private double cx, cz;
	private float zoom, targetZoom;
	private int zi = 6;
	private boolean following = true, dragging;
	private long lastNs;
	private boolean caveOn, touched;
	private int sliceY;
	private final int[][] chips = new int[4][];
	/** The waypoint the right-click menu is open for (Rename / Remove), and where the menu is. */
	private WaypointsMod.Point menuPoint;
	private int menuX, menuY;
	private boolean renaming;
	private final TextBox renameBox = new TextBox(32);
	private static final int MENU_W = 104, MENU_ROW = 22;

	public WorldMapScreen(MinimapMod mod) {
		super(Component.literal("Map"));
		this.mod = mod;
		zoom = targetZoom = STEPS[zi];
		if (mc.player != null) {
			cx = mc.player.getX();
			cz = mc.player.getZ();
			sliceY = mc.player.getBlockY();
		}
		caveOn = mod.caveActive();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void removed() {
		mod.forceCave = null;
		view.close();
		MapStore.I.flush();
	}

	private boolean ceiling() {
		return mc.level != null && mc.level.dimensionType().hasCeiling();
	}

	private void applyCave() {
		touched = true;
		mod.forceCave = caveOn;
		mod.forceY = sliceY;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
		if (mc.player == null || mc.level == null) {
			onClose();
			return;
		}
		long now = System.nanoTime();
		float dt = lastNs == 0 ? 0.016F : Math.min(0.1F, (now - lastNs) / 1e9F);
		lastNs = now;
		zoom += (targetZoom - zoom) * Math.min(1F, dt * 14F);
		if (Math.abs(targetZoom - zoom) < 0.002F) {
			zoom = targetZoom;
		}
		float td = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		var pp = mc.player.getPosition(td);
		if (following) {
			cx = pp.x;
			cz = pp.z;
		}
		boolean cave = caveOn || ceiling();
		int gs = (int) Math.max(1, Math.round(mc.getWindow().getGuiScale()));
		int tw = Math.max(160, Math.min(1280, Math.round(width * gs / 2F))), th = Math.max(90, Math.min(720, Math.round(height * gs / 2F)));
		float ppb = zoom * tw / (float) width;
		prm.w = tw;
		prm.h = th;
		prm.cave = cave;
		prm.cx = cx;
		prm.cz = cz;
		prm.ppb = ppb;
		prm.yaw = (float) Math.PI;
		prm.shape = 1;
		prm.border = 0F;
		prm.bright = mod.bright();
		prm.vignette = false;
		prm.edge = true;
		prm.smooth = false;
		prm.fog = mod.fog();
		prm.threeD = mod.threeD(cave);
		prm.heightScale = mod.heightScale();
		prm.cy = pp.y;
		prm.minMs = 40;
		view.render(MapStore.I, prm);
		c.blit(RenderPipelines.GUI_TEXTURED, view.id(), 0, 0, 0F, 0F, width, height, tw, th, tw, th, 0xFFFFFFFF);

		mod.drawMarkers(c, cx, cz, zoom, (float) Math.PI, width / 2F, height / 2F, width / 2F, height / 2F, 1, true, td, prm.threeD, view.h0());

		// top bar
		c.fill(0, 0, width, 24, 0xC0101216);
		c.fill(0, 24, width, 25, 0x40FFFFFF);
		String title = ceiling() ? "Cave slice at y=" + sliceY : caveOn ? (touched ? "Cave slice at y=" + sliceY : "Under the roof") : "Surface";
		String info = title + "  ·  " + mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ() + "  ·  " + MapStore.I.chunks() + " chunks";
		c.text(mc.font, info, 8, 8, 0xFFFFFFFF, true);

		// toggles
		String[] names = { "Waypoints", "Mobs", "Players", "Caves" };
		boolean[] on = { mod.showWaypoints(), mod.showMobs(), mod.showPlayers(), caveOn || ceiling() };
		int x = width - 6;
		for (int i = names.length - 1; i >= 0; i--) {
			int w = mc.font.width(names[i]) + 12;
			x -= w;
			chips[i] = new int[] { x, 3, w, 18 };
			boolean hov = mouseX >= x && mouseX < x + w && mouseY >= 3 && mouseY < 21;
			c.fill(x, 3, x + w, 21, on[i] ? 0xFF3D6BDB : hov ? 0xFF3A4155 : 0xFF2A2E3B);
			c.fill(x, 20, x + w, 21, 0x55000000);
			c.text(mc.font, names[i], x + 6, 8, 0xFFFFFFFF, false);
			x -= 4;
		}

		// bottom hints
		c.fill(0, height - 16, width, height, 0xC0101216);
		String hint = "Drag pan · Scroll zoom · Right-click add waypoint (on one: rename / remove) · U waypoints · C caves · PgUp/PgDn slice · Space recenter";
		c.text(mc.font, hint, 8, height - 12, 0xFFD6D3D1, true);

		// cursor coordinates
		if (mouseY > 25 && mouseY < height - 16) {
			double[] wpos = worldAt(mouseX, mouseY);
			int bx = (int) Math.floor(wpos[0]), bz = (int) Math.floor(wpos[1]);
			String t = bx + ", " + bz;
			int w = mc.font.width(t);
			int tx = Math.min(width - w - 8, mouseX + 10), ty = Math.min(height - 32, mouseY + 10);
			c.fill(tx - 3, ty - 2, tx + w + 3, ty + 10, 0xB0000000);
			c.text(mc.font, t, tx, ty, 0xFFFFFFFF, false);
		}
		if (menuPoint != null) {
			drawMenu(c, mouseX, mouseY);
		}
	}

	/**
	 * The world x / z under a point of the screen. In the 3D view the land is tilted and lifted, so the exact block under
	 * the mouse is looked up in the picture itself; the flat view is a simple scale.
	 */
	private double[] worldAt(double mx, double my) {
		if (prm.threeD && prm.w > 0 && width > 0) {
			double[] hit = view.pick(MapStore.I, prm, (int) (mx * prm.w / width), (int) (my * prm.h / height));
			if (hit != null) {
				return hit;
			}
			double k = MapView.KY;
			return new double[] { cx + (mx - width / 2.0) / zoom, cz + (my - height / 2.0) / (zoom * k) };
		}
		return new double[] { cx + (mx - width / 2.0) / zoom, cz + (my - height / 2.0) / zoom };
	}

	private static String tr(String en, String pt) {
		return dev.solclient.Lang.tr(en, pt);
	}

	/** The right-click menu of a waypoint: its name, then Rename and Remove (or the name box while renaming). */
	private void drawMenu(GuiGraphicsExtractor c, int mouseX, int mouseY) {
		if (renaming) {
			int w = 190, h = 56;
			int x = Math.max(4, Math.min(width - w - 4, menuX)), y = Math.max(28, Math.min(height - h - 20, menuY));
			c.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF3A4155);
			c.fill(x, y, x + w, y + h, 0xF0161922);
			c.text(mc.font, tr("New name", "Novo nome"), x + 8, y + 7, 0xFFB8BCC8, false);
			int bx = x + 8, by = y + 20, bw = w - 16, bh = 18;
			c.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF3D6BDB);
			c.fill(bx, by, bx + bw, by + bh, 0xFF0E1013);
			renameBox.draw(c, bx, by, bw, bh, bx + 5, bw - 10, true, 0xFF3D6BDB, 0xFFFFFFFF);
			c.text(mc.font, tr("Enter = save  ·  Esc = cancel", "Enter = guardar  ·  Esc = cancelar"), x + 8, y + h - 12, 0xFF8A8F9C, false);
			return;
		}
		String[] labels = { tr("Rename", "Renomear"), tr("Remove", "Retirar") };
		int h = MENU_ROW * labels.length + 20;
		int x = Math.max(4, Math.min(width - MENU_W - 4, menuX)), y = Math.max(28, Math.min(height - h - 20, menuY));
		menuX = x;
		menuY = y;
		c.fill(x - 1, y - 1, x + MENU_W + 1, y + h + 1, 0xFF3A4155);
		c.fill(x, y, x + MENU_W, y + h, 0xF0161922);
		String name = menuPoint.name == null ? "" : menuPoint.name;
		if (mc.font.width(name) > MENU_W - 16) {
			name = mc.font.plainSubstrByWidth(name, MENU_W - 24) + "..";
		}
		c.text(mc.font, name, x + 8, y + 6, 0xFFB8BCC8, false);
		c.fill(x + 4, y + 17, x + MENU_W - 4, y + 18, 0x30FFFFFF);
		for (int i = 0; i < labels.length; i++) {
			int ry = y + 20 + i * MENU_ROW;
			boolean hov = mouseX >= x && mouseX < x + MENU_W && mouseY >= ry && mouseY < ry + MENU_ROW;
			if (hov) {
				c.fill(x + 2, ry, x + MENU_W - 2, ry + MENU_ROW, i == 1 ? 0xFF7A2B30 : 0xFF3D6BDB);
			}
			c.text(mc.font, labels[i], x + 10, ry + 7, i == 1 && !hov ? 0xFFFF7A80 : 0xFFFFFFFF, false);
		}
	}

	/** The waypoint under the mouse (within its badge), or null. */
	private WaypointsMod.Point pointAt(double mx, double my) {
		WaypointsMod wm = WaypointsMod.instance;
		if (wm == null || !mod.showWaypoints()) {
			return null;
		}
		WaypointsMod.Point best = null;
		double bd = 12.0 * 12.0;
		for (WaypointsMod.Point p : wm.pointsHere()) {
			double sx = width / 2.0 + (p.x - cx) * zoom, sy = height / 2.0 + (p.z - cz) * zoom;
			if (prm.threeD) {
				sy = height / 2.0 + (p.z - cz) * zoom * MapView.KY - Math.max(-48.0, Math.min(64.0, p.y - view.h0())) * MapView.KH * mod.heightScale() * zoom;
			}
			double d = (sx - mx) * (sx - mx) + (sy - my) * (sy - my);
			if (d <= bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	private void closeMenu() {
		menuPoint = null;
		renaming = false;
	}

	// ------------------------------------------------------------------ input
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x(), my = click.y();
		if (menuPoint != null) {
			if (renaming) {
				if (renameBox.inside(mx, my)) {
					renameBox.click(mx);
				} else if (click.button() == 0) {
					closeMenu();
				}
				return true;
			}
			int h = MENU_ROW * 2 + 20;
			if (mx >= menuX && mx < menuX + MENU_W && my >= menuY + 20 && my < menuY + h) {
				int i = (int) ((my - menuY - 20) / MENU_ROW);
				if (click.button() == 0) {
					if (i == 0) {
						renaming = true;
						renameBox.set(menuPoint.name == null ? "" : menuPoint.name);
					} else if (WaypointsMod.instance != null) {
						WaypointsMod.instance.remove(menuPoint);
						closeMenu();
					}
				}
				return true;
			}
			closeMenu();
			if (click.button() == 0) {
				return true;
			}
			// a right click elsewhere closes the menu and then works as usual (new menu / new waypoint)
		}
		if (click.button() == 0) {
			for (int i = 0; i < chips.length; i++) {
				int[] r = chips[i];
				if (r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
					switch (i) {
						case 0 -> mod.toggleWaypoints();
						case 1 -> mod.toggleMobs();
						case 2 -> mod.togglePlayers();
						default -> {
							if (!ceiling()) {
								caveOn = !caveOn;
								applyCave();
							}
						}
					}
					return true;
				}
			}
			dragging = my > 25 && my < height - 16;
			return true;
		}
		if (click.button() == 1 && my > 25 && my < height - 16) {
			double[] wp0 = worldAt(mx, my);
			double wx = wp0[0], wz = wp0[1];
			WaypointsMod wm = WaypointsMod.instance;
			WaypointsMod.Point under = pointAt(mx, my);
			if (under != null && dev.solclient.Keys.shiftHeld() && wm != null) {
				wm.remove(under);
				return true;
			}
			if (under != null) {
				menuPoint = under;
				menuX = (int) mx + 4;
				menuY = (int) my + 4;
				renaming = false;
				return true;
			}
			if (wm != null) {
				if (dev.solclient.Keys.shiftHeld()) {
					wm.removeNear(wx, wz, Math.max(3.0, 14.0 / zoom));
				} else {
					int bx = (int) Math.floor(wx), bz = (int) Math.floor(wz);
					int y = mc.player.getBlockY();
					try {
						if (mc.level.getChunk(bx >> 4, bz >> 4, ChunkStatus.FULL, false) != null) {
							y = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
						}
					} catch (RuntimeException ignored) {
					}
					wm.addPointAt(null, wx, y, wz);
				}
			}
			return true;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		if (dragging && click.button() == 0) {
			cx -= dx / zoom;
			cz -= dy / zoom;
			following = false;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		dragging = false;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
		if (vAmount == 0) {
			return true;
		}
		float before = targetZoom;
		zi = Math.max(0, Math.min(STEPS.length - 1, zi + (vAmount > 0 ? 1 : -1)));
		targetZoom = STEPS[zi];
		double ox = mouseX - width / 2.0, oy = mouseY - height / 2.0;
		if (Math.abs(ox) + Math.abs(oy) > 8) {
			following = false;
		}
		if (!following) {
			cx += ox / before - ox / targetZoom;
			cz += oy / before - oy / targetZoom;
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent input) {
		if (menuPoint != null && renaming) {
			int cp = input.codepoint();
			if (cp >= 32 && cp != 127) {
				renameBox.typed(cp);
			}
			return true;
		}
		return super.charTyped(input);
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		int k = input.key();
		if (menuPoint != null) {
			if (k == 256) {
				closeMenu();
				return true;
			}
			if (renaming) {
				if (k == 257 || k == 335) {
					if (WaypointsMod.instance != null) {
						WaypointsMod.instance.rename(menuPoint, renameBox.text());
					}
					closeMenu();
				} else {
					renameBox.key(k);
				}
				return true;
			}
		}
		if (k == 256 || k == mod.mapKeyCode()) {
			onClose();
			return true;
		}
		switch (k) {
			case 85 -> mod.toggleWaypoints();
			case 67 -> {
				if (!ceiling()) {
					caveOn = !caveOn;
					applyCave();
				}
			}
			case 266 -> {
				sliceY += dev.solclient.Keys.shiftHeld() ? 8 : 1;
				caveOn = true;
				applyCave();
			}
			case 267 -> {
				sliceY -= dev.solclient.Keys.shiftHeld() ? 8 : 1;
				caveOn = true;
				applyCave();
			}
			case 32 -> {
				following = true;
				if (mc.player != null) {
					sliceY = mc.player.getBlockY();
					if (touched) {
						applyCave();
					}
				}
			}
			default -> {
				return super.keyPressed(input);
			}
		}
		return true;
	}
}
