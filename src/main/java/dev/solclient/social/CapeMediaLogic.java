package dev.solclient.social;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Shop capes with pictures / GIFs / videos ("capeMedia" of /players/lookup). This class has no Minecraft code at all,
 * so it can be unit tested: it decides HOW a cape is shown and builds the page that lays the cape atlas out.
 *
 * Cape atlas (vanilla layout, scaled): the outside of the cape (seen from behind the player = "back") is the box
 * (1,1)-(11,17) of a 64x32 grid, the inside ("front") is (12,1)-(22,17). Everything else (edges) stays plain.
 */
public final class CapeMediaLogic {

	private CapeMediaLogic() {
	}

	/** One side of a cape: an address and what the site says it is ("image" / "video"). */
	public record Side(String url, String type) {
	}

	/** What /players/lookup says about a cape. {@code front} is never null (falls back to {@code back}). */
	public record Desc(String layout, Side back, Side front) {

		public boolean panels() {
			return "panels".equals(layout);
		}

		/** A shop picture of unknown shape: a real cape atlas is used as it is, anything else is cropped onto the cape. */
		public boolean auto() {
			return "auto".equals(layout);
		}

		/** Cache key: same cape = same key, so one decoder / texture is shared by every player wearing it. */
		public String key() {
			return layout + "|" + back.url() + "|" + back.type() + "|" + front.url() + "|" + front.type();
		}

		public boolean sameMedia() {
			return back.url().equals(front.url());
		}
	}

	public enum Kind {
		PNG, JPG, GIF, WEBP, VIDEO, OTHER
	}

	/** Only https addresses are ever loaded (never file:, javascript:, data:...). */
	public static boolean https(String url) {
		return url != null && url.length() < 2048 && url.regionMatches(true, 0, "https://", 0, 8) && url.indexOf(' ') < 0;
	}

	/** Builds a side, or null when the address is missing / not https. */
	public static Side side(String url, String type) {
		if (!https(url)) {
			return null;
		}
		String t = type == null ? "" : type.toLowerCase(Locale.ROOT).trim();
		return new Side(url, t.equals("video") ? "video" : "image");
	}

	/** Builds a descriptor (null when there is no usable back side). A missing front copies the back. */
	public static Desc make(String layout, Side back, Side front) {
		if (back == null) {
			back = front;
		}
		if (back == null) {
			return null;
		}
		String l = "panels".equalsIgnoreCase(layout) ? "panels" : "auto".equalsIgnoreCase(layout) ? "auto" : "atlas";
		return new Desc(l, back, front == null ? back : front);
	}

	/** A cape known only by one picture (the shop picture): see {@link Desc#auto()}. Null when the address is not https. */
	public static Desc auto(String url) {
		Side s = side(url, "image");
		return s == null ? null : make("auto", s, s);
	}

	/**
	 * True when the picture is a real cape atlas (2:1, a multiple of 64 wide, empty bottom-right corner) and not a flat
	 * picture of the cape: atlases are used as they are, flat pictures are cropped onto the cape panels.
	 */
	public static boolean looksLikeAtlas(BufferedImage img) {
		if (img == null) {
			return false;
		}
		int w = img.getWidth(), h = img.getHeight();
		// every Minecraft cape file is 2:1 (64x32, 128x64, 22x17 crops are never 2:1); flat pictures of a cape are tall.
		// (the old test also wanted a transparent corner, so fully painted capes were cut up as if they were photos)
		return w >= 22 && Math.abs(w - 2 * h) <= Math.max(1, w / 64);
	}

	/**
	 * A cape atlas whose elytra part is empty gets the cape picture copied there, so wearing an elytra shows your cape
	 * design instead of an invisible / black elytra. Atlases that already have an elytra design are left alone.
	 */
	public static BufferedImage withElytra(BufferedImage atlas) {
		if (atlas == null) {
			return null;
		}
		int w = atlas.getWidth(), h = atlas.getHeight();
		double k = w / 64.0;
		int ex = (int) Math.round(22 * k), ew = (int) Math.round(24 * k), eh = Math.min(h, (int) Math.round(22 * k));
		if (ex + ew > w || eh <= 0) {
			return atlas;
		}
		int opaque = 0, total = 0;
		for (int y = 0; y < eh; y += Math.max(1, eh / 22)) {
			for (int x = ex; x < ex + ew; x += Math.max(1, ew / 24)) {
				total++;
				if ((atlas.getRGB(x, y) >>> 24) > 32) {
					opaque++;
				}
			}
		}
		if (total == 0 || opaque > total / 4) {
			return atlas;
		}
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try {
			g.drawImage(atlas, 0, 0, null);
			int px = (int) Math.round(1 * k), py = (int) Math.round(1 * k), pw = (int) Math.round(10 * k), ph = (int) Math.round(16 * k);
			if (px + pw <= w && py + ph <= h) {
				BufferedImage panel = atlas.getSubimage(px, py, pw, ph);
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(panel, ex, 0, ew, eh, null);
			}
		} finally {
			g.dispose();
		}
		return out;
	}

	/** The picture unchanged, as PNG bytes (an atlas that is already in the right layout). */
	public static byte[] toPng(BufferedImage img) throws java.io.IOException {
		BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = argb.createGraphics();
		try {
			g.drawImage(img, 0, 0, null);
		} finally {
			g.dispose();
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream(64 * 1024);
		ImageIO.write(argb, "png", bos);
		return bos.toByteArray();
	}

	public static Kind kind(Side s) {
		if ("video".equals(s.type())) {
			return Kind.VIDEO;
		}
		String u = s.url().toLowerCase(Locale.ROOT);
		int cut = u.length();
		for (char c : new char[]{'?', '#'}) {
			int i = u.indexOf(c);
			if (i >= 0 && i < cut) {
				cut = i;
			}
		}
		u = u.substring(0, cut);
		if (u.endsWith(".png")) {
			return Kind.PNG;
		}
		if (u.endsWith(".jpg") || u.endsWith(".jpeg")) {
			return Kind.JPG;
		}
		if (u.endsWith(".gif")) {
			return Kind.GIF;
		}
		if (u.endsWith(".webp")) {
			return Kind.WEBP;
		}
		if (u.endsWith(".mp4") || u.endsWith(".webm") || u.endsWith(".m4v") || u.endsWith(".mov")) {
			return Kind.VIDEO;
		}
		return Kind.OTHER;
	}

	/**
	 * True when only the embedded browser can play it. Since v102 GIFs are decoded in Java (GifFrames) and videos of an
	 * atlas cape go through ffmpeg when it is installed, so the browser is only the fallback (WebP, video panels, no ffmpeg).
	 */
	public static boolean needsBrowser(Desc d) {
		Kind b = kind(d.back()), f = kind(d.front());
		if (b == Kind.GIF && f == Kind.GIF) {
			return false;
		}
		return moves(b) || moves(f);
	}

	/** A GIF cape (both sides the same kind): decoded in Java, no browser needed. */
	public static boolean gif(Desc d) {
		return kind(d.back()) == Kind.GIF && kind(d.front()) == Kind.GIF;
	}

	/** A video cape in the cape layout (one file for the whole atlas): ffmpeg can play it without the browser. */
	public static boolean atlasVideo(Desc d) {
		return kind(d.back()) == Kind.VIDEO && d.sameMedia() && !d.panels();
	}

	private static boolean moves(Kind k) {
		return k == Kind.VIDEO || k == Kind.GIF || k == Kind.WEBP;
	}

	/** One frame of a GIF / picture as the cape atlas: a 2:1 picture is used as it is (any size), anything else is cropped onto the panels. */
	public static BufferedImage atlasFrame(Desc d, BufferedImage back, BufferedImage front, int maxW) {
		if (!d.panels() && looksLikeAtlas(back)) {
			BufferedImage a = withElytra(back);
			int w = Math.min(maxW, a.getWidth() - a.getWidth() % 2);
			if (a.getWidth() <= maxW) {
				return a;
			}
			BufferedImage s = shrink(a, w, w / 2);
			BufferedImage out = new BufferedImage(w, w / 2, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = out.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(s, 0, 0, w, w / 2, null);
			g.dispose();
			return out;
		}
		int k = Math.max(4, maxW / 64);
		int w = 64 * k, h = 32 * k;
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setColor(new java.awt.Color(0x15171C));
		g.fillRect(0, 0, w, h);
		cover(g, back, k, k, 10 * k, 16 * k);
		cover(g, front, 12 * k, k, 10 * k, 16 * k);
		cover(g, back, 22 * k, 0, 24 * k, 22 * k);
		g.dispose();
		return out;
	}

	/**
	 * True for the old contract: a plain PNG cape atlas that the normal cape renderer draws straight from
	 * {@code capeUrl}. Everything else (panels, JPG atlas, moving media) goes through {@link CapeMedia}.
	 */
	public static boolean legacyAtlas(Desc d) {
		return !d.panels() && !d.auto() && kind(d.back()) == Kind.PNG;
	}

	public static boolean custom(Desc d) {
		return d != null && !legacyAtlas(d);
	}

	/**
	 * Pixel box {x, y, w, h} of a cape face in an atlas of this width (any 2:1 size: 64, 128, 2048...). Faces are given in
	 * the vanilla 64x32 grid and scaled by width / 64. Names: top, bottom, left, back, right, front, elytra.
	 */
	public static int[] face(String name, int atlasWidth) {
		int[] g = switch (name) {
			case "top" -> new int[]{1, 0, 10, 1};
			case "bottom" -> new int[]{11, 0, 10, 1};
			case "left" -> new int[]{0, 1, 1, 16};
			case "back" -> new int[]{1, 1, 10, 16};
			case "right" -> new int[]{11, 1, 1, 16};
			case "front" -> new int[]{12, 1, 10, 16};
			case "elytra" -> new int[]{22, 0, 24, 22};
			default -> throw new IllegalArgumentException(name);
		};
		double k = atlasWidth / 64.0;
		return new int[]{(int) Math.round(g[0] * k), (int) Math.round(g[1] * k), (int) Math.round(g[2] * k), (int) Math.round(g[3] * k)};
	}

	private static String esc(String s) {
		return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String media(Side s) {
		String u = esc(s.url());
		if (kind(s) == Kind.VIDEO) {
			return "<video src=\"" + u + "\" muted loop autoplay playsinline preload=\"auto\"></video>";
		}
		return "<img src=\"" + u + "\">";
	}

	/** Page size in pixels for atlas scale k: 64k x 32k. */
	public static int pageW(int k) {
		return 64 * k;
	}

	public static int pageH(int k) {
		return 32 * k;
	}

	/**
	 * The page the embedded browser shows: the whole cape atlas (64k x 32k). Panels layout: back and front are placed
	 * in their atlas boxes, cropped to fill without stretching (object-fit: cover). Atlas layout: the media fills
	 * the whole atlas. When both sides are the same file, ONE video is decoded and the second panel copies it
	 * (a canvas), so the two sides always share the same playback clock.
	 */
	public static String html(Desc d, int k) {
		int w = pageW(k), h = pageH(k);
		StringBuilder b = new StringBuilder(1024);
		b.append("<!doctype html><meta charset=\"utf-8\"><style>")
				.append("html,body{margin:0;width:").append(w).append("px;height:").append(h).append("px;overflow:hidden;background:#15171c}")
				.append(".p{position:absolute;overflow:hidden;background:#15171c}")
				.append(".p>*{display:block;width:100%;height:100%;object-fit:cover}")
				.append(".f>*{object-fit:fill}")
				.append("</style>");
		boolean mirror = false;
		boolean panels = d.panels() || d.auto(); // moving media of unknown shape is cropped onto the panels
		if (panels) {
			b.append(panel("p", k, 1, 1, 10, 16, media(d.back())));
			mirror = d.sameMedia() && kind(d.back()) == Kind.VIDEO;
			if (mirror) {
				b.append(panel("p", k, 12, 1, 10, 16, "<canvas id=\"m\" width=\"" + (10 * k) + "\" height=\"" + (16 * k) + "\"></canvas>"));
			} else {
				b.append(panel("p", k, 12, 1, 10, 16, media(d.front())));
			}
		} else {
			b.append("<div class=\"p f\" style=\"left:0;top:0;width:").append(w).append("px;height:").append(h).append("px\">")
					.append(media(d.back())).append("</div>");
		}
		b.append("<script>(function(){")
				.append("var vs=document.querySelectorAll('video');")
				.append("function go(){vs.forEach(function(v){v.muted=true;v.defaultMuted=true;if(v.paused){var p=v.play();if(p&&p.catch)p.catch(function(){});}});}")
				.append("go();setInterval(go,1000);");
		if (mirror) {
			b.append("var cv=document.getElementById('m'),c=cv.getContext('2d'),src=vs[0];")
					.append("function draw(){if(src.readyState>=2&&src.videoWidth>0){var vw=src.videoWidth,vh=src.videoHeight,pw=cv.width,ph=cv.height;")
					.append("var s=Math.max(pw/vw,ph/vh),sw=pw/s,sh=ph/s;c.drawImage(src,(vw-sw)/2,(vh-sh)/2,sw,sh,0,0,pw,ph);}requestAnimationFrame(draw);}draw();");
		}
		if (panels && !d.sameMedia() && kind(d.back()) == Kind.VIDEO && kind(d.front()) == Kind.VIDEO) {
			// two different videos: keep the second one on the first one's clock
			b.append("setInterval(function(){var a=vs[0],f=vs[1],n=f.duration;if(n>0&&isFinite(n)){var t=a.currentTime%n;if(Math.abs(f.currentTime-t)>0.25)f.currentTime=t;}},500);");
		}
		b.append("})();</script>");
		return b.toString();
	}

	private static String panel(String cls, int k, int x, int y, int w, int h, String inner) {
		return "<div class=\"" + cls + "\" style=\"left:" + (x * k) + "px;top:" + (y * k) + "px;width:" + (w * k) + "px;height:" + (h * k) + "px\">" + inner + "</div>";
	}

	/** Builds the cape atlas as PNG bytes: panels cropped to fill (no stretching), or the picture over the whole atlas. */
	public static byte[] compose(Desc d, BufferedImage back, BufferedImage front, int k) throws java.io.IOException {
		int w = 64 * k, h = 32 * k;
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setColor(new java.awt.Color(0x15171C));
			g.fillRect(0, 0, w, h);
			if (d.panels() || d.auto()) {
				cover(g, back, 1 * k, 1 * k, 10 * k, 16 * k);
				cover(g, front, 12 * k, 1 * k, 10 * k, 16 * k);
				// the elytra takes its picture from this part of the cape texture: give it the cape picture too
				// (it used to stay the dark background, so elytras looked black / broken)
				cover(g, back, 22 * k, 0, 24 * k, 22 * k);
			} else {
				g.drawImage(shrink(back, w, h), 0, 0, w, h, null);
			}
		} finally {
			g.dispose();
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);
		ImageIO.write(out, "png", bos);
		return bos.toByteArray();
	}

	/** Draws 'img' so it fills the box completely, keeping its proportions (the overflow is cropped). */
	private static void cover(Graphics2D g, BufferedImage img, int x, int y, int w, int h) {
		double s = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
		int dw = (int) Math.ceil(img.getWidth() * s), dh = (int) Math.ceil(img.getHeight() * s);
		BufferedImage small = shrink(img, dw, dh);
		java.awt.Shape old = g.getClip();
		g.setClip(x, y, w, h);
		g.drawImage(small, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
		g.setClip(old);
	}

	/** Halves the picture step by step while it is much bigger than needed (a single big downscale looks jagged). */
	private static BufferedImage shrink(BufferedImage img, int tw, int th) {
		BufferedImage cur = img;
		while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
			BufferedImage half = new BufferedImage(cur.getWidth() / 2, cur.getHeight() / 2, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = half.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(cur, 0, 0, half.getWidth(), half.getHeight(), null);
			g.dispose();
			cur = half;
		}
		return cur;
	}
}
