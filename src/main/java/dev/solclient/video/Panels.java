package dev.solclient.video;

import dev.solclient.mods.BrowserMod;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * The floating browser panels: creates / closes them, keeps their Chromium pages alive and draws them in the world
 * (frame, title bar with buttons and address, page). Aiming, clicking, moving and typing live in {@link PanelInput}.
 */
public final class Panels {

	public static final List<PanelBox> LIST = new ArrayList<>();

	private static final Identifier WHITE = Identifier.of("minecraft", "textures/misc/white.png");
	private static final int LIGHT = 0xF000F0;
	private static final double TEXT_SCALE = 0.0155;
	private static boolean logged;

	private record Quad(float[] v, int color, float nx, float ny, float nz) {
	}

	private Panels() {
	}

	public static boolean active() {
		return BrowserMod.on();
	}

	/** Hooks the world rendering and clears the panels when you leave a server / world. */
	public static void register() {
		try {
			WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
			long kuboPerfT0 = System.nanoTime();
			try {
				long t0 = System.nanoTime();
				try {
					frame(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
					if (BrowserMod.instance != null && !LIST.isEmpty()) {
						BrowserMod.instance.addCost(System.nanoTime() - t0);
					}
				} catch (RuntimeException e) {
					if (!logged) {
						logged = true;
						System.err.println("[Sleep] floating panels: " + e);
						e.printStackTrace();
					}
				}
						} finally {
				dev.solclient.Perf.add("Video screens", System.nanoTime() - kuboPerfT0);
			}
		});
		} catch (Throwable t) {
			System.err.println("[Sleep] floating panels rendering unavailable: " + t);
		}
		try {
			ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(Panels::clear));
		} catch (Throwable t) {
			System.err.println("[Sleep] floating panels: could not hook disconnect: " + t);
		}
	}

	// ------------------------------------------------------------------ create / close

	/** A new panel in front of the player (shifted sideways if another panel is already there). */
	public static PanelBox create() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return null;
		}
		Vec3d eye = mc.player.getEyePos();
		Vec3d look = mc.player.getRotationVec(1F);
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		if (flat.lengthSquared() < 1.0E-4) {
			flat = new Vec3d(0, 0, 1);
		}
		flat = flat.normalize();
		double w = BrowserMod.spawnWidth();
		double h = w * 9.0 / 16.0 + PanelBox.TITLE_H;
		Vec3d center = eye.add(flat.multiply(BrowserMod.spawnDistance()));
		PanelBox p = new PanelBox(center, flat, w, h, BrowserMod.home());
		for (int i = 0; i < 8; i++) {
			boolean taken = false;
			for (PanelBox o : LIST) {
				if (o.center.distanceTo(p.center) < Math.min(o.width, w) * 0.5) {
					taken = true;
					break;
				}
			}
			if (!taken) {
				break;
			}
			p.center = p.center.add(p.right().multiply(w + 0.25));
		}
		LIST.add(p);
		return p;
	}

	/** A new panel showing an app of this computer (Open Apps): live, and you can use it. */
	public static PanelBox createApp(AppMirror.Source s) {
		PanelBox p = create();
		if (p != null) {
			p.mirrorSrc = s;
			p.shownUrl = "app: " + s.label();
			fitToApp(p, s);
			if (s.installed()) {
				// not open yet: open it, then show its window
				p.launching = true;
				Thread t = new Thread(() -> {
					AppMirror.Source w = AppMirror.launch(s);
					MinecraftClient.getInstance().execute(() -> {
						p.launching = false;
						if (w != null && !p.closed) {
							p.mirrorSrc = w;
							p.shownUrl = "app: " + w.label();
							fitToApp(p, w);
						} else if (!p.closed) {
							p.shownUrl = "app: " + s.label() + " (no window)";
						}
					});
				}, "kubo-open-app");
				t.setDaemon(true);
				t.start();
			}
		}
		return p;
	}

	private static final String[] REAL_BROWSERS = {"Safari", "Google Chrome", "Arc", "Brave Browser", "Firefox", "Microsoft Edge", "Opera", "Chromium"};

	/**
	 * Opens the panel's page in the computer's own browser and turns the panel into that browser's window (Open Apps):
	 * videos the built-in browser can't play (Instagram, TikTok: H.264 / AAC) then work.
	 */
	public static void openInRealBrowser(PanelBox p) {
		String url = p.shownUrl == null || p.shownUrl.startsWith("app:") ? BrowserMod.home() : p.shownUrl;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			mc.player.sendMessage(net.minecraft.text.Text.literal("Opening it in your browser..."), true);
		}
		Thread t = new Thread(() -> {
			try {
				new ProcessBuilder("open", "-g", url).start();
				for (int i = 0; i < 30; i++) {
					Thread.sleep(300);
					for (AppMirror.Source w : AppMirror.macWindows()) {
						for (String b : REAL_BROWSERS) {
							if (b.equalsIgnoreCase(w.app())) {
								mc.execute(() -> {
									if (p.closed) {
										return;
									}
									if (p.browser != null) {
										p.browser.close();
										p.browser = null;
									}
									p.mirrorSrc = w;
									p.shownUrl = "app: " + w.label();
									fitToApp(p, w);
								});
								return;
							}
						}
					}
				}
			} catch (Exception e) {
				System.err.println("[Sleep] open in browser: " + e);
			}
		}, "kubo-real-browser");
		t.setDaemon(true);
		t.start();
	}

	/** Same shape as the app's window (so clicks land where you aim). */
	private static void fitToApp(PanelBox p, AppMirror.Source s) {
		if (s.w() > 0 && s.h() > 0) {
			p.height = p.width * s.h() / s.w() + PanelBox.TITLE_H;
			p.height = Math.max(PanelBox.MIN_H, Math.min(PanelBox.MAX_H, p.height));
		}
	}

	public static void remove(PanelBox p) {
		LIST.remove(p);
		close(p);
	}

	public static void clear() {
		for (PanelBox p : LIST) {
			close(p);
		}
		LIST.clear();
		PanelInput.reset();
	}

	private static void close(PanelBox p) {
		if (p.closed) {
			return;
		}
		p.closed = true;
		if (p.browser != null) {
			p.browser.close();
			p.browser = null;
		}
		if (p.mirror != null) {
			p.mirror.close();
			p.mirror = null;
		}
		if (p.texRegistered) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(p.texId);
			p.texRegistered = false;
		}
		if (p.chromeTex != null && p.chromeId != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(p.chromeId);
			p.chromeTex = null;
		}
	}

	// ------------------------------------------------------------------ per frame

	private static void frame(MatrixStack m, OrderedRenderCommandQueue q, Vec3d cam) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) {
			return;
		}
		if (!active()) {
			if (!LIST.isEmpty()) {
				clear();
			}
			return;
		}
		float td = mc.getRenderTickCounter().getTickProgress(true);
		Vec3d eye = mc.player.getCameraPosVec(td);
		Vec3d look = mc.player.getRotationVec(td);
		PanelInput.update(mc, eye, look);
		if (LIST.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (PanelBox p : new ArrayList<>(LIST)) {
			ensureBrowser(p, now);
			refresh(p, cam, look, now);
			draw(p, cam, m, q, mc);
		}
	}

	private static void sizeFor(PanelBox p, int[] out) {
		// ~1.3 million pixels: big pages (Instagram...) were slow to type in at 2 million, and look the same at this distance
		double ppb = Math.min(240.0, Math.sqrt(1_300_000.0 / Math.max(0.1, p.width * p.contentH())));
		out[0] = (int) Math.max(96, Math.min(2048, p.width * ppb));
		out[1] = (int) Math.max(64, Math.min(2048, p.contentH() * ppb));
	}

	/** Starts the engine / the page when needed and follows the panel's size. Cheap when nothing changes. */
	private static void ensureBrowser(PanelBox p, long now) {
		if (p.closed) {
			return;
		}
		if (p.mirrorSrc != null) {
			if (p.launching || p.mirrorSrc.installed()) {
				return;
			}
			if (p.mirror != null && p.mirror.failed() && now >= p.retryAt) {
				p.mirror.close();
				p.mirror = null;
				p.retryAt = now + 3000;
				return;
			}
			if (p.mirror == null && now >= p.retryAt && AppMirror.MAC && p.mirrorSrc.window() > 0) {
				int[] sz = new int[2];
				sizeFor(p, sz);
				p.pxW = Math.min(1600, sz[0]) & ~1;
				p.pxH = Math.max(64, (int) Math.round(p.pxW * p.contentH() / p.width)) & ~1;
				p.mirror = new MacWindowView(p.mirrorSrc.window(), p.pxW, p.pxH, Identifier.of("sol_client", "browser/app_" + p.id));
				return;
			}
			if (p.mirror == null && now >= p.retryAt) {
				String ff = dev.solclient.fightcam.Ffmpeg.path;
				if (ff == null) {
					p.retryAt = now + 3000;
					Thread t = new Thread(dev.solclient.fightcam.Ffmpeg::findPath, "kubo-ffmpeg-find");
					t.setDaemon(true);
					t.start();
					return;
				}
				int[] sz = new int[2];
				sizeFor(p, sz);
				p.pxW = sz[0] & ~1;
				p.pxH = sz[1] & ~1;
				FfVideo fv = new FfVideo(p.mirrorSrc.input(), p.mirrorSrc.filter(), p.pxW, p.pxH, Identifier.of("sol_client", "browser/app_" + p.id));
				if (fv.start(ff)) {
					p.mirror = fv;
				} else {
					p.retryAt = now + 5000;
				}
			}
			return;
		}
		if (!Mcef.installed()) {
			return;
		}
		Mcef.start();
		if (!Mcef.ready()) {
			return;
		}
		int[] sz = new int[2];
		sizeFor(p, sz);
		if (p.browser == null) {
			if (now < p.retryAt) {
				return;
			}
			p.browser = Mcef.create(p.startUrl, sz[0], sz[1]);
			if (p.browser == null) {
				p.retryAt = now + 3000;
				return;
			}
			p.pxW = sz[0];
			p.pxH = sz[1];
			return;
		}
		boolean scaling = PanelInput.resizing() == p;
		if (!scaling && (Math.abs(sz[0] - p.pxW) > 8 || Math.abs(sz[1] - p.pxH) > 8) && now - p.resizeAt > 250) {
			p.resizeAt = now;
			p.pxW = sz[0];
			p.pxH = sz[1];
			p.browser.resize(sz[0], sz[1]);
		}
	}

	/** Address, back/forward state, frame rate by distance and the "open links here" script. */
	private static void refresh(PanelBox p, Vec3d cam, Vec3d look, long now) {
		if (p.browser == null) {
			return;
		}
		if (now - p.urlAt > 500) {
			p.urlAt = now;
			String u = p.browser.url();
			p.shownUrl = u == null ? "" : u;
			String low = p.shownUrl.toLowerCase(java.util.Locale.ROOT);
			if (!p.videoHint && (low.contains("instagram.com") || low.contains("tiktok.com") || low.contains("facebook.com") || low.contains("twitter.com") || low.contains("://x.com"))) {
				p.videoHint = true;
				dev.solclient.gui.Notify.show(dev.solclient.gui.Notify.Kind.INFO,
						dev.solclient.Lang.tr("Videos here don't play in the built-in browser", "Os vídeos daqui não dão no browser do Sleep"),
						AppMirror.MAC ? dev.solclient.Lang.tr("Press the share button on the bar: it opens in your browser", "Carrega no botão de partilhar da barra: abre no teu browser")
								: dev.solclient.Lang.tr("Use Open Apps (O) with your browser", "Usa o Open Apps (O) com o teu browser"));
			}
			p.canBack = p.browser.canBack();
			p.canFwd = p.browser.canForward();
		}
		if (now - p.fpsAt > 500) {
			p.fpsAt = now;
			Vec3d d = p.center.subtract(cam);
			double dist = d.length();
			boolean front = d.dotProduct(look) > 0;
			int want = p.hovered || PanelInput.typingIn(p) ? 30 : front && dist < 24 ? 20 : front && dist < 64 ? 8 : 3;
			if (want != p.fps) {
				p.fps = want;
				p.browser.frameRate(want);
			}
		}
		if (now - p.jsAt > 1500) {
			p.jsAt = now;
			// links that want a new window open in the same panel instead (there is no second window in the world)
			p.browser.js("(function(){if(window.__kb)return;window.__kb=1;var o=window.open;"
					+ "window.open=function(u){if(u){location.href=u;}return null;};"
					+ "document.addEventListener('click',function(e){var a=e.target&&e.target.closest?e.target.closest('a[target]'):null;"
					+ "if(a&&a.target&&a.target!=='_self'){a.target='_self';}},true);})();");
		}
	}

	private static boolean texture(PanelBox p, MinecraftClient mc) {
		if (p.mirror != null) {
			if (!p.mirror.upload()) {
				return false;
			}
			p.texId = p.mirror.id();
			return p.mirror.hasPicture();
		}
		if (p.browser == null) {
			return false;
		}
		var v = p.browser.texture();
		if (v == null) {
			return false;
		}
		if (p.tex == null) {
			p.tex = new BrowserTexture();
			p.texId = Identifier.of("sol_client", "browser/panel_" + p.id);
		}
		if (!p.tex.set(v)) {
			return false;
		}
		if (!p.texRegistered) {
			mc.getTextureManager().registerTexture(p.texId, p.tex);
			p.texRegistered = true;
		}
		return true;
	}

	// ------------------------------------------------------------------ drawing

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	private static void rect(List<Quad> out, PanelBox p, Vec3d cam, double x0, double x1, double y0, double y1, double off, double k,
			int color) {
		off *= k; // further away: bigger gaps between the layers, so the depth buffer still tells them apart
		Vec3d a = p.point(x0, y0, off).subtract(cam);
		Vec3d b = p.point(x1, y0, off).subtract(cam);
		Vec3d c = p.point(x1, y1, off).subtract(cam);
		Vec3d d = p.point(x0, y1, off).subtract(cam);
		Vec3d n = p.normal();
		out.add(new Quad(new float[] {(float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, (float) c.x, (float) c.y,
				(float) c.z, (float) d.x, (float) d.y, (float) d.z}, color, (float) n.x, (float) n.y, (float) n.z));
	}

	private static void emit(MatrixStack.Entry e, VertexConsumer vc, Quad q, boolean uv) {
		float[] v = q.v();
		float[] us = uv ? new float[] {0, 1, 1, 0} : new float[] {0.5F, 0.5F, 0.5F, 0.5F};
		float[] vs = uv ? new float[] {1, 1, 0, 0} : new float[] {0.5F, 0.5F, 0.5F, 0.5F};
		for (int i = 0; i < 4; i++) {
			vc.vertex(e, v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).color(q.color()).texture(us[i], vs[i]).overlay(OverlayTexture.DEFAULT_UV)
					.light(LIGHT).normal(e, q.nx(), q.ny(), q.nz());
		}
	}

	/** Text on the panel. align: -1 starts at lx, 0 centred on lx, 1 ends at lx. Shrinks to fit maxW (blocks). */
	private static void text(PanelBox p, Vec3d cam, MatrixStack m, OrderedRenderCommandQueue q, MinecraftClient mc, String s, double lx,
			double ly, double sc, int color, int align, double maxW, double off) {
		if (s == null || s.isEmpty()) {
			return;
		}
		TextRenderer tr = mc.textRenderer;
		int tw = tr.getWidth(s);
		if (tw <= 0) {
			return;
		}
		if (tw * sc > maxW) {
			sc = maxW / tw;
		}
		float x = align == 0 ? -tw / 2F : align < 0 ? 0F : -tw;
		Vec3d r = p.right(), u = p.up(), n = p.normal();
		Vec3d o = p.point(lx, ly, off).subtract(cam);
		m.push();
		m.translate(o.x, o.y, o.z);
		// the panel's own axes (right, up, towards you), so text follows the tilt too
		m.multiply(new Quaternionf().setFromNormalized(new org.joml.Matrix3f((float) r.x, (float) r.y, (float) r.z, (float) u.x, (float) u.y, (float) u.z,
				(float) n.x, (float) n.y, (float) n.z)));
		m.scale((float) sc, (float) -sc, (float) sc);
		q.submitText(m, x, -4.5F, Text.literal(s).asOrderedText(), false, TextRenderer.TextLayerType.POLYGON_OFFSET, LIGHT, color, 0, 0);
		m.pop();
	}

	/** Keeps the end of a long address (the end matters more than the start). */
	private static String fit(MinecraftClient mc, String s, double maxBlocks, double sc) {
		TextRenderer tr = mc.textRenderer;
		int max = (int) (maxBlocks / sc);
		if (tr.getWidth(s) <= max) {
			return s;
		}
		while (s.length() > 1 && tr.getWidth("…" + s) > max) {
			s = s.substring(1);
		}
		return "…" + s;
	}

	private static String pretty(String u) {
		String s = u == null ? "" : u;
		s = s.replaceFirst("^https?://", "");
		return s.replaceFirst("^www\\.", "");
	}

	/** Repaints the Safari-style bar picture when something on it changed (width, hover, back / forward, editing). */
	private static boolean chrome(PanelBox p, MinecraftClient mc, int hover, boolean barHover, boolean editing, int accent) {
		SafariChrome.Layout l = p.layout();
		double scale = Math.max(0.5, Math.min(2.5, 2048.0 / l.w));
		String key = Math.round(l.w) + "|" + hover + "|" + barHover + "|" + p.canBack + "|" + p.canFwd + "|" + editing + "|" + accent;
		if (key.equals(p.chromeKey) && p.chromeTex != null) {
			return true;
		}
		try {
			java.awt.image.BufferedImage img = SafariChrome.paint(l, scale, hover, barHover, p.canBack, p.canFwd, editing, accent);
			int w = img.getWidth(), h = img.getHeight();
			net.minecraft.client.texture.NativeImage ni;
			if (p.chromeTex != null && p.chromeTex.getImage() != null && p.chromeTex.getImage().getWidth() == w && p.chromeTex.getImage().getHeight() == h) {
				ni = p.chromeTex.getImage();
			} else {
				if (p.chromeId == null) {
					p.chromeId = Identifier.of("sol_client", "browser/bar_" + p.id);
				}
				p.chromeTex = new net.minecraft.client.texture.NativeImageBackedTexture(() -> "kubo_browser_bar_" + p.id, w, h, false);
				mc.getTextureManager().registerTexture(p.chromeId, p.chromeTex); // replaces (and frees) the old picture
				ni = p.chromeTex.getImage();
			}
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					ni.setColorArgb(x, y, img.getRGB(x, y));
				}
			}
			p.chromeTex.upload();
			p.chromeKey = key;
			return true;
		} catch (Throwable t) {
			if (!logged) {
				logged = true;
				System.err.println("[Sleep] browser bar: " + t);
			}
			return false;
		}
	}

	private static void draw(PanelBox p, Vec3d cam, MatrixStack m, OrderedRenderCommandQueue q, MinecraftClient mc) {
		double dist = p.center.distanceTo(cam);
		double k = Math.max(1.0, Math.min(6.0, dist / 6.0));
		double hw = p.width / 2, hh = p.height / 2, t = PanelBox.TITLE_H, unit = p.unit();
		int accent = BrowserMod.accent();
		boolean typingHere = PanelInput.typingIn(p), urlHere = PanelInput.urlEditing(p);
		boolean aimed = p.hovered && PanelInput.hover == p;
		boolean barHover = aimed && p.inTitle(p.hitX, p.hitY);
		int hoverPart = aimed ? p.barPart(p.hitX, p.hitY) : SafariChrome.NONE;

		// window: a thin #404040 edge (the theme colour while you type in the page) and a dark body
		List<Quad> chrome = new ArrayList<>();
		double e = Math.max(unit * 1.2, 0.006 * k);
		int edge = typingHere ? accent : 0xFF404040;
		rect(chrome, p, cam, -hw - e, hw + e, -hh - e, hh - t + e, 0.000, k, edge);
		rect(chrome, p, cam, -hw, hw, -hh, hh - t, 0.004, k, 0xFF1A1A1A);

		boolean hasTex = texture(p, mc);
		if (!hasTex) {
			engineStatus(chrome, p, cam, k, accent);
		}

		if (p.hovered) {
			double cz = p.cornerSize();
			// resize handles: the two bottom corners (the top corners belong to the title bar)
			for (int sx = -1; sx <= 1; sx += 2) {
				double xa = sx * hw, xb = sx * (hw - cz), ya = -hh, yb = -hh + cz;
				boolean active = p.cornerX == sx && p.cornerY == -1;
				rect(chrome, p, cam, Math.min(xa, xb), Math.max(xa, xb), ya, yb, 0.020, k, active ? 0xFFFFFFFF : 0xFF5A5A5A);
			}
			if (aimed) {
				double c = 0.02;
				rect(chrome, p, cam, p.hitX - c, p.hitX + c, p.hitY - c, p.hitY + c, 0.024, k, 0xFFFFFFFF);
			}
		}

		RenderLayer white = RenderLayers.entityCutoutNoCull(WHITE);
		q.submitCustom(m, white, (en, vc) -> {
			for (Quad qd : chrome) {
				emit(en, vc, qd, false);
			}
		});

		if (hasTex) {
			List<Quad> web = new ArrayList<>();
			rect(web, p, cam, -hw, hw, -hh, hh - t, 0.008, k, 0xFFFFFFFF);
			RenderLayer layer = RenderLayers.entityCutoutNoCull(p.texId);
			q.submitCustom(m, layer, (en, vc) -> {
				for (Quad qd : web) {
					emit(en, vc, qd, true);
				}
			});
		} else {
			engineText(p, cam, m, q, mc, k);
		}

		// the Safari-style bar (one picture), then the live address on top of it
		if (chrome(p, mc, hoverPart, barHover, urlHere, accent)) {
			List<Quad> bar = new ArrayList<>();
			rect(bar, p, cam, -hw, hw, hh - t, hh, 0.012, k, 0xFFFFFFFF);
			RenderLayer layer = RenderLayers.entityTranslucent(p.chromeId);
			q.submitCustom(m, layer, (en, vc) -> {
				for (Quad qd : bar) {
					emit(en, vc, qd, true);
				}
			});
		}
		SafariChrome.Layout l = p.layout();
		double off = 0.030 * k, sc = unit * 1.45;
		double tx0 = p.barX(l.textX0()), tx1 = p.barX(l.textX1()), ty = hh - t / 2;
		String shown;
		int col;
		if (urlHere) {
			shown = PanelInput.urlText + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : " ");
			col = 0xFFF2F2F2;
		} else {
			shown = pretty(p.shownUrl);
			col = 0xFFA3A3A3;
		}
		double w = tx1 - tx0;
		text(p, cam, m, q, mc, fit(mc, shown, w, sc), tx0, ty, sc, col, -1, w, off);
	}


	// ------------------------------------------------------------------ the page area while there is no page

	private static void engineStatus(List<Quad> out, PanelBox p, Vec3d cam, double k, int accent) {
		if (p.mirrorSrc != null) {
			return;
		}
		if (!Mcef.installed() || Mcef.failed() || Mcef.ready()) {
			return;
		}
		double cy = -PanelBox.TITLE_H / 2;
		double barW = Math.max(0.6, Math.min(p.width - 0.5, 1.8)), barH = 0.07, y0 = cy - 0.12;
		rect(out, p, cam, -barW / 2 - 0.01, barW / 2 + 0.01, y0 - 0.01, y0 + barH + 0.01, 0.010, k, 0xFF0B0C10);
		rect(out, p, cam, -barW / 2, barW / 2, y0, y0 + barH, 0.012, k, 0xFF2A2F3D);
		float pr = Mcef.progress();
		if (pr >= 0) {
			rect(out, p, cam, -barW / 2, -barW / 2 + barW * pr / 100.0, y0, y0 + barH, 0.014, k, accent);
		} else {
			double seg = barW / 4;
			double ph = (System.currentTimeMillis() % 1400) / 1400.0;
			double sx = -barW / 2 + (barW - seg) * (0.5 - 0.5 * Math.cos(ph * 2 * Math.PI));
			rect(out, p, cam, sx, sx + seg, y0, y0 + barH, 0.014, k, accent);
		}
	}

	private static void engineText(PanelBox p, Vec3d cam, MatrixStack m, OrderedRenderCommandQueue q, MinecraftClient mc, double k) {
		double cy = -PanelBox.TITLE_H / 2, mw = p.width - 0.3, off = 0.030 * k, sc = 0.018;
		if (p.mirrorSrc != null) {
			text(p, cam, m, q, mc, "Showing " + p.mirrorSrc.label() + "...", 0, cy + 0.06, sc, 0xFFB9BDC9, 0, mw, off);
			String hint = p.launching ? "Opening the app..."
					: AppMirror.MAC ? "Allow the launcher in Privacy > Screen Recording (picture) and Accessibility (clicks)"
					: dev.solclient.fightcam.Ffmpeg.path == null ? "Looking for ffmpeg..." : "Click on it to use it";
			text(p, cam, m, q, mc, hint, 0, cy - 0.1, 0.012, 0xFF8A8FA0, 0, mw, off);
			return;
		}
		if (!Mcef.installed()) {
			text(p, cam, m, q, mc, "Browser engine missing", 0, cy + 0.08, sc, 0xFFFF8A8A, 0, mw, off);
			text(p, cam, m, q, mc, "Rebuild Sleep with MCEF included", 0, cy - 0.08, 0.014, 0xFF8A8FA0, 0, mw, off);
			return;
		}
		if (Mcef.failed()) {
			String err = Mcef.error();
			text(p, cam, m, q, mc, "The browser engine could not start", 0, cy + 0.16, sc, 0xFFFF8A8A, 0, mw, off);
			text(p, cam, m, q, mc, err.length() > 70 ? err.substring(0, 70) + "..." : err, 0, cy + 0.02, 0.013, 0xFF8A8FA0, 0, mw, off);
			text(p, cam, m, q, mc, "Click the page to try again", 0, cy - 0.14, 0.014, 0xFFFFFFFF, 0, mw, off);
			return;
		}
		if (!Mcef.ready()) {
			float pr = Mcef.progress();
			text(p, cam, m, q, mc, Mcef.stageLabel(), 0, cy + 0.20, sc, 0xFFFFFFFF, 0, mw, off);
			text(p, cam, m, q, mc, pr >= 0 ? Math.round(pr) + "%" : "Working...", 0, cy - 0.28, 0.014, 0xFFB9BDC9, 0, mw, off);
			text(p, cam, m, q, mc, "About 100 MB, only the first time", 0, cy - 0.42, 0.012, 0xFF8A8FA0, 0, mw, off);
			if (Mcef.stalled()) {
				text(p, cam, m, q, mc, "Nothing moved for a while: check your connection", 0, cy - 0.56, 0.012, 0xFFFFD27A, 0, mw, off);
			}
			return;
		}
		text(p, cam, m, q, mc, "Opening the page...", 0, cy, sc, 0xFFB9BDC9, 0, mw, off);
	}
}
