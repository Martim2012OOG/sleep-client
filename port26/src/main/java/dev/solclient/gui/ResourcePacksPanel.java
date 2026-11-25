package dev.solclient.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.social.KuboPackLogic;
import dev.solclient.social.KuboPacks;
import dev.solclient.social.Social;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Resource pack browser inside the client with two sources, chosen at the top: Modrinth (filters, install) or the Caos
 * site (GET /resource-packs: grid, detail with banner and social links, install with SHA-1 check, update, activate).
 * It is a panel drawn INSIDE the Caos menu (the middle area, like the shop), not a separate big screen: the menu is the
 * {@link Host} and gives it the mouse, the click regions and the text field focus. Opened from the "Resource Packs"
 * button in the top bar of the Right Shift menu.
 */
public class ResourcePacksPanel {

	/** What the menu gives the panel. */
	public interface Host {
		void region(int x, int y, int w, int h, Runnable click);

		boolean hovered(int x, int y, int w, int h);

		float anim(String key, boolean target);

		void button(GuiGraphicsExtractor c, int x, int y, int w, int h, String label, String id, Runnable run);

		/** The search box has the keyboard focus. */
		boolean searchFocused();

		void focusSearch();

		/** Leave the panel (back to the mods). */
		void close();
	}

	private static final String API = "https://api.modrinth.com/v2";
	private static final String UA = "Caos-Client/1.0 (Minecraft client mod)";
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(12))
			.build();

	/** id, English, Portuguese. */
	private static final String[][] CATS = {
			{"combat", "Combat", "Combate"}, {"cursed", "Cursed", "Amaldiçoado"}, {"decoration", "Decoration", "Decoração"},
			{"modded", "Modded", "Modificado"}, {"realistic", "Realistic", "Realista"}, {"simplistic", "Simplistic", "Simplista"},
			{"themed", "Themed", "Temático"}, {"tweaks", "Tweaks", "Ajustes"}, {"utility", "Utility", "Utilidade"},
			{"vanilla-like", "Vanilla-Like", "Estilo vanilla"}};
	private static final String[] RES = {"8x-", "16x", "32x", "48x", "64x", "128x", "256x", "512x+"};
	private static final String[] SORT_ID = {"relevance", "downloads", "follows", "newest", "updated"};
	private static final String[][] SORT_NAME = {{"Relevance", "Relevância"}, {"Downloads", "Downloads"}, {"Follows", "Seguidores"},
			{"Newest", "Mais recentes"}, {"Updated", "Atualizados"}};

	private static final int M = 10, SW = 150, GAP = 6, CARD_H = 66, PAGE = 20;

	private record Hit(String id, String slug, String title, String author, String desc, String icon, long downloads, long follows,
			List<String> cats) {
	}

	private static final class Inst {
		volatile float progress;
		volatile int state; // 0 = downloading, 1 = installed, 2 = failed
		volatile String msg = "";
	}

	/** Install state per project (kept while the game is open). */
	private static final Map<String, Inst> INSTALLS = new ConcurrentHashMap<>();

	private Host host;
	private final Minecraft mc = Minecraft.getInstance();
	private int mouseX, mouseY;
	private String tooltip;
	/** Area given by the menu this frame. */
	private int ox, oy, ow, oh;
	private final TextBox search = new TextBox(60);
	private final List<Hit> hits = new ArrayList<>();
	private final Set<String> cats = new HashSet<>();
	private final Set<String> res = new HashSet<>();
	private boolean thisVersionOnly = true;
	private int sort;
	private int total;
	private volatile boolean loading;
	private volatile int gen;
	private String error;
	private String status = "";
	private int scroll, sideScroll;
	private int contentH, sideH;
	private String typed = "", applied = null;
	private long typedAt;
	private Hit detail;

	// ---- source: Modrinth or the Caos site (the choice is kept while the game is open)
	private static boolean lastKubo = false;
	private boolean kubo = false; // the Caos source was removed: only Modrinth
	private final List<KuboPackLogic.Pack> kPacks = new ArrayList<>();
	private volatile boolean kLoading;
	private volatile int kGen;
	private String kError;
	private int kScroll, kDetailScroll, kDetailH;
	private KuboPackLogic.Pack kDetail;
	private String kNote = "";
	private long kNoteAt;

	public ResourcePacksPanel() {
	}

	// ------------------------------------------------------------------ small helpers (the menu does the real work)
	private static final int TILE = 0xFF14161B, TILE_HOVER = 0xFF1E2128, BORDER = 0x40FFFFFF;

	private void region(int x, int y, int w, int h, Runnable click) {
		host.region(x, y, w, h, click);
	}

	private boolean hovered(int x, int y, int w, int h) {
		return host.hovered(x, y, w, h);
	}

	private float anim(String key, boolean target) {
		return host.anim(key, target);
	}

	private void button(GuiGraphicsExtractor c, String id, int x, int y, int w, int h, String label, Runnable run) {
		host.button(c, x, y, w, h, label, id, run);
	}

	private void panel(GuiGraphicsExtractor c, int x, int y, int w, int h, String title) {
		Gfx.rrectBorder(c, x, y, w, h, 10, Theme.SUB, Theme.PANEL_BORDER);
	}

	/** The search box (the menu edits it: typing, caret, selection). */
	public TextBox searchBox() {
		return search;
	}

	/** Tooltip of this frame (null = none). */
	public String tooltip() {
		return tooltip;
	}

	// ------------------------------------------------------------------ data
	private static String gameVersion() {
		try {
			return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft")
					.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("1.21.11");
		} catch (Throwable t) {
			return "1.21.11";
		}
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String get(String url) throws IOException, InterruptedException {
		HttpRequest rq = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", UA)
				.header("Accept", "application/json").GET().build();
		HttpResponse<String> r = HTTP.send(rq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (r.statusCode() < 200 || r.statusCode() >= 300) {
			throw new IOException("HTTP " + r.statusCode());
		}
		return r.body();
	}

	private static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	private static long num(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? 0L : e.getAsLong();
	}

	private String searchUrl(int offset) {
		StringBuilder f = new StringBuilder("[[\"project_type:resourcepack\"]");
		if (thisVersionOnly) {
			f.append(",[\"versions:").append(gameVersion()).append("\"]");
		}
		for (String ct : cats) {
			f.append(",[\"categories:").append(ct).append("\"]");
		}
		if (!res.isEmpty()) {
			f.append(",[");
			boolean first = true;
			for (String r : res) {
				f.append(first ? "" : ",").append("\"categories:").append(r).append("\"");
				first = false;
			}
			f.append("]");
		}
		f.append("]");
		return API + "/search?query=" + enc(search.text().trim()) + "&facets=" + enc(f.toString()) + "&index=" + SORT_ID[sort]
				+ "&limit=" + PAGE + "&offset=" + offset;
	}

	/** New search (append = false) or the next page (append = true). */
	private void startSearch(boolean append) {
		if (kubo) {
			startKubo();
			return;
		}
		if (append && (loading || hits.size() >= total)) {
			return;
		}
		final int myGen = append ? gen : ++gen;
		loading = true;
		error = null;
		applied = search.text();
		final String url = searchUrl(append ? hits.size() : 0);
		Social.IO.execute(() -> {
			try {
				JsonObject o = JsonParser.parseString(get(url)).getAsJsonObject();
				List<Hit> got = new ArrayList<>();
				JsonArray arr = o.getAsJsonArray("hits");
				for (JsonElement e : arr) {
					JsonObject h = e.getAsJsonObject();
					List<String> cs = new ArrayList<>();
					if (h.has("categories") && h.get("categories").isJsonArray()) {
						for (JsonElement ce : h.getAsJsonArray("categories")) {
							cs.add(ce.getAsString());
						}
					}
					got.add(new Hit(str(h, "project_id"), str(h, "slug"), str(h, "title"), str(h, "author"), str(h, "description"),
							str(h, "icon_url"), num(h, "downloads"), num(h, "follows"), cs));
				}
				int tot = (int) num(o, "total_hits");
				mc.execute(() -> {
					if (myGen != gen) {
						return;
					}
					if (!append) {
						hits.clear();
						scroll = 0;
					}
					hits.addAll(got);
					total = tot;
					loading = false;
				});
			} catch (Throwable t) {
				mc.execute(() -> {
					if (myGen != gen) {
						return;
					}
					loading = false;
					error = Lang.tr("Could not reach Modrinth", "Não foi possível ligar ao Modrinth");
				});
			}
		});
	}

	private static String safeName(String n) {
		String s = n.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
		if (s.isEmpty()) {
			s = "pack.zip";
		}
		return s.toLowerCase().endsWith(".zip") ? s : s + ".zip";
	}

	private void install(Hit h) {
		Inst cur = INSTALLS.get(h.id());
		if (cur != null && cur.state == 0) {
			return;
		}
		final Inst in = new Inst();
		INSTALLS.put(h.id(), in);
		final String gv = gameVersion();
		final Path dir = mc.gameDirectory.toPath().resolve("resourcepacks");
		Social.IO.execute(() -> {
			Path tmp = null;
			try {
				JsonArray vs = JsonParser.parseString(get(API + "/project/" + h.id() + "/version?game_versions=" + enc("[\"" + gv + "\"]")))
						.getAsJsonArray();
				if (vs.size() == 0) { // nothing for this exact version: the newest one still usually works
					vs = JsonParser.parseString(get(API + "/project/" + h.id() + "/version")).getAsJsonArray();
				}
				if (vs.size() == 0 || vs.get(0).getAsJsonObject().getAsJsonArray("files").size() == 0) {
					throw new IOException("no files");
				}
				JsonArray files = vs.get(0).getAsJsonObject().getAsJsonArray("files");
				JsonObject f = files.get(0).getAsJsonObject();
				for (JsonElement e : files) {
					JsonObject fo = e.getAsJsonObject();
					if (fo.has("primary") && fo.get("primary").getAsBoolean()) {
						f = fo;
						break;
					}
				}
				Files.createDirectories(dir);
				Path target = dir.resolve(safeName(str(f, "filename")));
				if (Files.exists(target)) {
					in.progress = 1F;
					in.state = 1;
					return;
				}
				tmp = dir.resolve(target.getFileName() + ".part");
				HttpRequest rq = HttpRequest.newBuilder(URI.create(str(f, "url"))).timeout(Duration.ofMinutes(15))
						.header("User-Agent", UA).GET().build();
				HttpResponse<InputStream> r = HTTP.send(rq, HttpResponse.BodyHandlers.ofInputStream());
				if (r.statusCode() < 200 || r.statusCode() >= 300) {
					throw new IOException("HTTP " + r.statusCode());
				}
				long len = r.headers().firstValueAsLong("Content-Length").orElse(num(f, "size"));
				MessageDigest md = MessageDigest.getInstance("SHA-1");
				try (InputStream is = r.body(); OutputStream os = Files.newOutputStream(tmp)) {
					byte[] buf = new byte[65536];
					long read = 0;
					int n;
					while ((n = is.read(buf)) > 0) {
						os.write(buf, 0, n);
						md.update(buf, 0, n);
						read += n;
						if (len > 0) {
							in.progress = Math.min(0.99F, read / (float) len);
						}
					}
				}
				String want = f.has("hashes") && f.getAsJsonObject("hashes").has("sha1") ? str(f.getAsJsonObject("hashes"), "sha1") : "";
				if (!want.isEmpty()) {
					StringBuilder hex = new StringBuilder();
					for (byte b : md.digest()) {
						hex.append(String.format(java.util.Locale.ROOT, "%02x", b));
					}
					if (!hex.toString().equalsIgnoreCase(want)) {
						throw new IOException("checksum");
					}
				}
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				in.progress = 1F;
				in.state = 1;
				mc.execute(() -> {
					try {
						mc.getResourcePackRepository().reload(); // shows up in Options > Resource Packs without restarting
					} catch (Throwable ignored) {
						// not important: it appears the next time the list is opened
					}
				});
			} catch (Throwable t) {
				in.state = 2;
				in.msg = String.valueOf(t.getMessage());
				try {
					if (tmp != null) {
						Files.deleteIfExists(tmp);
					}
				} catch (IOException ignored) {
					// leftover .part file, harmless
				}
			}
		});
	}

	// ------------------------------------------------------------------ text helpers (Modrinth text is never auto-translated)
	private static String fitRaw(String s, int maxW) {
		if (s == null) {
			return "";
		}
		if (Gfx.twRaw(s) <= maxW) {
			return s;
		}
		while (s.length() > 1 && Gfx.twRaw(s + "..") > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}

	private static List<String> wrap(String s, int maxW, int maxLines) {
		List<String> lines = new ArrayList<>();
		String cur = "";
		for (String w : s.replace('\n', ' ').split(" +")) {
			if (w.isEmpty()) {
				continue;
			}
			if (cur.isEmpty()) {
				cur = w;
			} else if (Gfx.twRaw(cur + " " + w) <= maxW) {
				cur += " " + w;
			} else {
				lines.add(cur);
				cur = w;
			}
		}
		if (!cur.isEmpty()) {
			lines.add(cur);
		}
		if (lines.size() > maxLines) {
			String last = lines.get(maxLines - 1) + " " + lines.get(maxLines) + "...";
			lines = new ArrayList<>(lines.subList(0, maxLines));
			lines.set(maxLines - 1, fitRaw(last, maxW));
		}
		List<String> out = new ArrayList<>();
		for (String l : lines) {
			out.add(fitRaw(l, maxW));
		}
		return out;
	}

	private static String compact(long n) {
		if (n >= 1_000_000L) {
			return String.format(java.util.Locale.ROOT, "%.1fM", n / 1_000_000.0).replace(".0M", "M");
		}
		if (n >= 1_000L) {
			return String.format(java.util.Locale.ROOT, "%.1fK", n / 1_000.0).replace(".0K", "K");
		}
		return Long.toString(n);
	}

	// ------------------------------------------------------------------ drawing
	/** Draws the panel inside the area (x, y, w, hh) of the menu. */
	public void draw(GuiGraphicsExtractor c, Host h, int mx, int my, int x, int y, int w, int hh) {
		this.host = h;
		this.mouseX = mx;
		this.mouseY = my;
		this.tooltip = null;
		this.ox = x;
		this.oy = y;
		this.ow = w;
		this.oh = hh;
		int ph = oh - 2 * M;
		drawSidebar(c, ox + M, oy + M, SW, ph);
		int mx0 = ox + M + SW + GAP, mw = ox + ow - M - mx0;
		panel(c, mx0, oy + M, mw, ph, null);

		// debounce: search while typing
		String now = search.text();
		if (!now.equals(typed)) {
			typed = now;
			typedAt = System.currentTimeMillis();
		}
		if (applied == null) {
			startSearch(false);
		} else if (!typed.equals(applied) && System.currentTimeMillis() - typedAt > 350) {
			startSearch(false);
		}

		if (kubo) {
			if (kDetail != null) {
				drawKDetail(c, mx0, oy + M, mw, ph);
			} else {
				drawKBrowse(c, mx0, oy + M, mw, ph);
			}
		} else if (detail != null) {
			drawDetail(c, mx0, oy + M, mw, ph);
		} else {
			drawBrowse(c, mx0, oy + M, mw, ph);
		}
	}

	private void check(GuiGraphicsExtractor c, String id, int x, int y, int w, String label, boolean on, Runnable toggle) {
		boolean hov = hovered(x, y, w, 14) && mouseY >= oy + M + 34 && mouseY < oy + oh - M - 6;
		float a = anim("chk" + id, hov);
		Gfx.rrectBorder(c, x, y, 14, 14, 4, on ? Theme.accent() : Gfx.blend(0xFF0A0B0E, 0xFF1E2128, a), on ? Theme.accent() : Gfx.blend(BORDER, 0xFFFFFFFF, a * 0.5F));
		if (on) { // a real tick: two stepped strokes, 2px thick
			int[][] tick = {{3, 7}, {4, 8}, {5, 9}, {6, 8}, {7, 7}, {8, 6}, {9, 5}, {10, 4}};
			for (int[] t : tick) {
				Gfx.fill(c, x + t[0], y + t[1], x + t[0] + 2, y + t[1] + 2, 0xFFFFFFFF);
			}
		}
		Gfx.text(c, label, x + 21, y + 3, on ? 0xFFFFFFFF : Gfx.blend(Theme.DIM, Theme.TEXT, 0.5F + 0.5F * a));
		int ry0 = Math.max(y, oy + M + 34), ry1 = Math.min(y + 14, oy + oh - M - 6);
		if (ry1 > ry0) {
			region(x - 2, ry0, w + 4, ry1 - ry0, toggle);
		}
	}

	private void drawSidebar(GuiGraphicsExtractor c, int x, int y, int w, int h) {
		panel(c, x, y, w, h, null);
		button(c, "rpclose", x + 8, y + 8, w - 16, 20, Lang.tr("Close", "Fechar"), this::close);
		if (kubo) {
			drawKSidebar(c, x, y, w);
			return;
		}
		int y0 = y + 34, y1 = y + h - 6;
		sideScroll = Math.max(0, Math.min(sideScroll, Math.max(0, sideH - (y1 - y0))));
		c.enableScissor(x + 2, y0, x + w - 2, y1);
		int yy = y0 - sideScroll + 2;
		int ix = x + 12, iw = w - 24;

		Gfx.text(c, Lang.tr("Categories", "Categorias"), ix, yy, Theme.DIM);
		yy += 14;
		for (String[] ct : CATS) {
			final String id = ct[0];
			check(c, id, ix, yy, iw, Lang.tr(ct[1], ct[2]), cats.contains(id), () -> {
				if (!cats.remove(id)) {
					cats.add(id);
				}
				startSearch(false);
			});
			yy += 19;
		}
		yy += 6;
		Gfx.text(c, Lang.tr("Resolution", "Resolução"), ix, yy, Theme.DIM);
		yy += 14;
		for (String r : RES) {
			final String id = r;
			String label = r.equals("8x-") ? Lang.tr("8x or lower", "8x ou menos") : r.equals("512x+") ? Lang.tr("512x or higher", "512x ou mais") : r;
			check(c, "r" + id, ix, yy, iw, label, res.contains(id), () -> {
				if (!res.remove(id)) {
					res.add(id);
				}
				startSearch(false);
			});
			yy += 19;
		}
		yy += 6;
		Gfx.text(c, Lang.tr("Minecraft version", "Versão do Minecraft"), ix, yy, Theme.DIM);
		yy += 14;
		check(c, "ver", ix, yy, iw, gameVersion(), thisVersionOnly, () -> {
			thisVersionOnly = !thisVersionOnly;
			startSearch(false);
		});
		yy += 19;
		sideH = yy + sideScroll - y0 + 4;
		c.disableScissor();
	}

	private void drawBrowse(GuiGraphicsExtractor c, int x, int y, int w, int h) {
		// top row: search + sort
		int sbw = 120, tg = TOGGLE_W, sx0 = x + 8 + tg, sy0 = y + 8, sw = w - 16 - sbw - 6 - tg;
		drawToggle(c, x + 8, sy0);
		Gfx.rrectBorder(c, sx0, sy0, sw, 22, 7, 0xFF0A0B0E, Theme.accent());
		if (search.isEmpty()) {
			Gfx.text(c, Lang.tr("Search resource packs...", "Pesquisar pacotes de recursos..."), sx0 + 8, sy0 + 7, Theme.DIM);
		}
		search.draw(c, sx0, sy0, sw, 22, sx0 + 8, sw - 16, host.searchFocused(), Theme.accent(), Theme.TEXT);
		region(sx0, sy0, sw, 22, host::focusSearch);
		button(c, "rpsort", x + w - 8 - sbw, sy0, sbw, 22, Lang.tr("Sort: ", "Ordem: ") + Lang.tr(SORT_NAME[sort][0], SORT_NAME[sort][1]), () -> {
			sort = (sort + 1) % SORT_ID.length;
			startSearch(false);
		});

		// results grid
		int gy0 = y + 36, gy1 = y + h - 22;
		int gx = x + 8, gw = w - 16;
		int cols = Math.max(1, (gw + GAP) / 260);
		int cw = (gw - (cols - 1) * GAP) / cols;
		int rows = (hits.size() + cols - 1) / cols;
		contentH = rows * (CARD_H + GAP);
		int maxScroll = Math.max(0, contentH - (gy1 - gy0));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		if (scroll > maxScroll - 140 && !loading && hits.size() < total) {
			startSearch(true);
		}
		c.enableScissor(x + 2, gy0, x + w - 2, gy1);
		for (int i = 0; i < hits.size(); i++) {
			int cx = gx + (i % cols) * (cw + GAP), cy = gy0 - scroll + (i / cols) * (CARD_H + GAP);
			if (cy + CARD_H >= gy0 && cy <= gy1) {
				card(c, hits.get(i), cx, cy, cw, gy0, gy1);
			}
		}
		c.disableScissor();
		if (hits.isEmpty()) {
			String msg = error != null ? error : loading ? Lang.tr("Loading...", "A carregar...") : Lang.tr("No resource packs found", "Nenhum pacote de recursos encontrado");
			Gfx.textC(c, msg, x + w / 2, (gy0 + gy1) / 2 - 4, error != null ? 0xFFE5767A : Theme.DIM);
		}
		// bottom status line
		String st = !status.isEmpty() ? status
				: Lang.tr("Packs go to your resourcepacks folder. Turn them on in Options > Resource Packs.",
						"Os pacotes vão para a pasta resourcepacks. Ativa-os em Opções > Pacotes de recursos.");
		Gfx.textRaw(c, fitRaw(st, w - 130), x + 10, y + h - 15, Theme.DIM);
		if (total > 0) {
			String t = hits.size() + " / " + total;
			Gfx.textRaw(c, t, x + w - 10 - Gfx.twRaw(t), y + h - 15, Theme.DIM);
		}
	}

	/** The install button (state aware). Returns nothing; registers its own click. */
	private void installButton(GuiGraphicsExtractor c, Hit h, int bx, int by, int bw, int bh, int clip0, int clip1) {
		Inst in = INSTALLS.get(h.id());
		boolean hov = hovered(bx, by, bw, bh) && mouseY >= clip0 && mouseY < clip1;
		float a = anim("ib" + h.id(), hov);
		int accent = Theme.accent();
		int bg, fg = 0xFFFFFFFF;
		String word;
		boolean clickable = true;
		if (in == null) {
			word = Lang.tr("Install", "Instalar");
			bg = Gfx.blend(accent, 0xFF000000, 0.35F - 0.15F * a);
		} else if (in.state == 0) {
			word = Math.round(in.progress * 100) + "%";
			bg = Theme.OFF_BG;
			fg = Theme.TEXT;
			clickable = false;
		} else if (in.state == 1) {
			word = Lang.tr("Installed", "Instalado");
			bg = Theme.GREEN_BG;
			fg = Theme.GREEN;
			clickable = false;
		} else {
			word = Lang.tr("Retry", "Repetir");
			bg = Gfx.blend(0xFF3A1E22, 0xFF4A242A, a);
			fg = 0xFFE5767A;
		}
		Gfx.rrect(c, bx, by, bw, bh, 6, bg);
		if (in != null && in.state == 0) {
			Gfx.rrect(c, bx, by, Math.max(6, Math.round(bw * in.progress)), bh, 6, Gfx.withAlpha(accent, 0xB0));
		}
		Gfx.textFit(c, word, bx + bw / 2, bw - 6, by + (bh - 9) / 2, fg);
		if (in != null && in.state == 2 && hov) {
			tooltip = Lang.tr("Failed: ", "Falhou: ") + in.msg;
		}
		int ry0 = Math.max(by, clip0), ry1 = Math.min(by + bh, clip1);
		if (clickable && ry1 > ry0) {
			region(bx, ry0, bw, ry1 - ry0, () -> {
				status = Lang.tr("Installing ", "A instalar ") + h.title() + "...";
				install(h);
			});
		}
	}

	private void icon(GuiGraphicsExtractor c, Hit h, int x, int y, int s) {
		// same loader as the Caos packs: PNG / JPG / GIF, and WebP (most Modrinth icons) through a PNG conversion
		KuboPacks.Img t = h.icon().isEmpty() ? null : KuboPacks.image(h.icon(), 128);
		if (t != null) {
			Gfx.tex(c, t.id(), x, y, s, s, t.width(), t.height(), 0xFFFFFFFF);
		} else {
			Gfx.rrect(c, x, y, s, s, Math.max(3, s / 8), 0xFF2C2F38);
			String ini = h.title().isEmpty() ? "?" : h.title().substring(0, 1).toUpperCase();
			Gfx.textRaw(c, ini, x + s / 2 - Gfx.twRaw(ini) / 2, y + s / 2 - 4, Theme.DIM);
		}
	}

	private void card(GuiGraphicsExtractor c, Hit h, int x, int y, int w, int clip0, int clip1) {
		boolean hov = hovered(x, y, w, CARD_H) && mouseY >= clip0 && mouseY < clip1;
		float a = anim("rc" + h.id(), hov);
		Gfx.rrectBorder(c, x, y, w, CARD_H, 8, Gfx.blend(TILE, TILE_HOVER, a), Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, a));
		int ry0 = Math.max(y, clip0), ry1 = Math.min(y + CARD_H, clip1);
		if (ry1 > ry0) {
			region(x, ry0, w, ry1 - ry0, () -> detail = h);
		}
		icon(c, h, x + 8, y + 8, 50);
		int bw = 62, bh = 18, tx = x + 66;
		Gfx.textRaw(c, fitRaw(h.title(), w - 66 - bw - 16), tx, y + 8, 0xFFFFFFFF);
		Gfx.textRaw(c, fitRaw(Lang.tr("by ", "por ") + h.author(), w - 66 - 8), tx, y + 19, Theme.DIM);
		List<String> lines = wrap(h.desc(), w - 66 - 8, 2);
		for (int i = 0; i < lines.size(); i++) {
			Gfx.textRaw(c, lines.get(i), tx, y + 31 + i * 10, Theme.TEXT);
		}
		String stats = compact(h.downloads()) + " downloads  -  " + compact(h.follows()) + " " + Lang.tr("followers", "seguidores");
		Gfx.textRaw(c, fitRaw(stats, w - 66 - 8), tx, y + CARD_H - 12, 0xFF6B7080);
		installButton(c, h, x + w - 8 - bw, y + 8, bw, bh, clip0, clip1);
	}

	private void drawDetail(GuiGraphicsExtractor c, int x, int y, int w, int h) {
		Hit d = detail;
		button(c, "rpback", x + 8, y + 8, 80, 22, "< " + Lang.tr("Back", "Voltar"), () -> detail = null);
		int px = x + 18, py = y + 44;
		icon(c, d, px, py, 72);
		int tx = px + 84, tw = w - 36 - 84;
		c.pose().pushMatrix();
		c.pose().translate(tx, py);
		c.pose().scale(1.5F, 1.5F);
		Gfx.textRaw(c, fitRaw(d.title(), Math.round(tw / 1.5F)), 0, 0, 0xFFFFFFFF);
		c.pose().popMatrix();
		Gfx.textRaw(c, fitRaw(Lang.tr("by ", "por ") + d.author(), tw), tx, py + 20, Theme.DIM);
		Gfx.textRaw(c, compact(d.downloads()) + " downloads  -  " + compact(d.follows()) + " " + Lang.tr("followers", "seguidores"), tx, py + 32, 0xFF6B7080);
		installButton(c, d, tx, py + 48, 90, 22, y, y + h);
		int bx2 = tx + 98;
		String openLbl = Lang.tr("Open on Modrinth", "Abrir no Modrinth");
		int ow = Gfx.tw(openLbl) + 20;
		button(c, "rpopen", bx2, py + 48, ow, 22, openLbl, () -> net.minecraft.util.Util.getPlatform().openUri("https://modrinth.com/resourcepack/" + (d.slug().isEmpty() ? d.id() : d.slug())));

		int yy = py + 90;
		for (String l : wrap(d.desc(), w - 36, 6)) {
			Gfx.textRaw(c, l, px, yy, Theme.TEXT);
			yy += 11;
		}
		yy += 8;
		int cx = px;
		for (String ct : d.cats()) {
			int cwid = Gfx.twRaw(ct) + 14;
			if (cx + cwid > x + w - 18) {
				cx = px;
				yy += 20;
			}
			Gfx.rrectBorder(c, cx, yy, cwid, 16, 6, TILE, Theme.CARD_BORDER);
			Gfx.textRaw(c, ct, cx + 7, yy + 4, Theme.DIM);
			cx += cwid + 5;
		}
	}


	// ------------------------------------------------------------------ source toggle
	private static final int TOGGLE_W = 62 + 8;

	private void setSource(boolean k) {
		if (kubo == k) {
			return;
		}
		kubo = k;
		lastKubo = k;
		detail = null;
		kDetail = null;
		applied = null; // draw() starts the search of the new source
	}

	private void drawToggle(GuiGraphicsExtractor c, int x, int y) {
		sourceButton(c, "srcmod", x, y, 62, "Modrinth", !kubo, () -> setSource(false));
	}

	private void sourceButton(GuiGraphicsExtractor c, String id, int x, int y, int w, String label, boolean on, Runnable run) {
		float a = anim(id, hovered(x, y, w, 22));
		Gfx.rrectBorder(c, x, y, w, 22, 7, on ? Gfx.blend(Theme.accent(), 0xFF000000, 0.35F) : Gfx.blend(TILE, TILE_HOVER, a),
				on ? Theme.accent() : Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, a));
		Gfx.textFit(c, label, x + w / 2, w - 6, y + 6, on ? 0xFFFFFFFF : Theme.TEXT);
		region(x, y, w, 22, run);
	}

	// ------------------------------------------------------------------ Caos source
	private void startKubo() {
		final int my = ++kGen;
		kLoading = true;
		kError = null;
		applied = search.text();
		final String q = search.text();
		Social.IO.execute(() -> {
			try {
				List<KuboPackLogic.Pack> got = KuboPacks.list(q);
				KuboPacks.loadStates(got); // before showing, so a pack never flashes "Install" when it is installed
				mc.execute(() -> {
					if (my != kGen) {
						return;
					}
					kPacks.clear();
					kPacks.addAll(got);
					kScroll = 0;
					kLoading = false;
				});
			} catch (Throwable t) {
				mc.execute(() -> {
					if (my != kGen) {
						return;
					}
					kPacks.clear();
					kLoading = false;
					kError = Lang.tr("Could not reach the Caos site", "Não foi possível ligar ao site do Caos");
				});
			}
		});
	}

	private void note(String text) {
		kNote = text;
		kNoteAt = System.currentTimeMillis();
	}

	private void drawKSidebar(GuiGraphicsExtractor c, int x, int y, int w) {
		int ix = x + 12, iw = w - 24, yy = y + 40;
		Gfx.textRaw(c, Lang.tr("Caos packs", "Packs do Caos"), ix, yy, Theme.DIM);
		yy += 14;
		String info = Lang.tr("Packs published on the Caos site. Install downloads the file, checks its SHA-1 and turns the pack on.",
				"Packs publicados no site do Caos. Instalar descarrega o ficheiro, verifica o SHA-1 e ativa o pack.");
		for (String l : wrap(info, iw, 12)) {
			Gfx.textRaw(c, l, ix, yy, Theme.TEXT);
			yy += 11;
		}
		yy += 8;
		String tip = Lang.tr("Switch to Modrinth at the top for thousands more.", "Muda para Modrinth, em cima, para milhares de outros.");
		for (String l : wrap(tip, iw, 6)) {
			Gfx.textRaw(c, l, ix, yy, Theme.DIM);
			yy += 11;
		}
	}

	private void kIcon(GuiGraphicsExtractor c, KuboPackLogic.Pack p, int x, int y, int s) {
		KuboPacks.Img t = KuboPacks.image(p.icon(), 128);
		if (t != null) {
			Gfx.tex(c, t.id(), x, y, s, s, t.width(), t.height(), 0xFFFFFFFF);
		} else {
			Gfx.rrect(c, x, y, s, s, Math.max(3, s / 8), 0xFF2C2F38);
			String ini = p.name().isEmpty() ? "?" : p.name().substring(0, 1).toUpperCase();
			Gfx.textRaw(c, ini, x + s / 2 - Gfx.twRaw(ini) / 2, y + s / 2 - 4, Theme.DIM);
		}
	}

	private void drawKBrowse(GuiGraphicsExtractor c, int x, int y, int w, int h) {
		int sx0 = x + 8 + TOGGLE_W, sy0 = y + 8, sw = w - 16 - TOGGLE_W;
		drawToggle(c, x + 8, sy0);
		Gfx.rrectBorder(c, sx0, sy0, sw, 22, 7, 0xFF0A0B0E, Theme.accent());
		if (search.isEmpty()) {
			Gfx.textRaw(c, Lang.tr("Search Caos resource packs...", "Pesquisar packs do Caos..."), sx0 + 8, sy0 + 7, Theme.DIM);
		}
		search.draw(c, sx0, sy0, sw, 22, sx0 + 8, sw - 16, host.searchFocused(), Theme.accent(), Theme.TEXT);
		region(sx0, sy0, sw, 22, host::focusSearch);

		int gy0 = y + 36, gy1 = y + h - 22;
		int gx = x + 8, gw = w - 16;
		int cols = Math.max(1, (gw + GAP) / 260);
		int cw = (gw - (cols - 1) * GAP) / cols;
		int rows = (kPacks.size() + cols - 1) / cols;
		int contentH = rows * (CARD_H + GAP);
		int maxScroll = Math.max(0, contentH - (gy1 - gy0));
		kScroll = Math.max(0, Math.min(kScroll, maxScroll));
		c.enableScissor(x + 2, gy0, x + w - 2, gy1);
		for (int i = 0; i < kPacks.size(); i++) {
			int cx = gx + (i % cols) * (cw + GAP), cy = gy0 - kScroll + (i / cols) * (CARD_H + GAP);
			if (cy + CARD_H >= gy0 && cy <= gy1) {
				kCard(c, kPacks.get(i), cx, cy, cw, gy0, gy1);
			}
		}
		c.disableScissor();
		if (kPacks.isEmpty()) {
			String msg = kError != null ? kError : kLoading ? Lang.tr("Loading...", "A carregar...") : Lang.tr("No resource packs found", "Nenhum pacote de recursos encontrado");
			Gfx.textC(c, msg, x + w / 2, (gy0 + gy1) / 2 - 4, kError != null ? 0xFFE5767A : Theme.DIM);
		}
		String st = System.currentTimeMillis() - kNoteAt < 5000 && !kNote.isEmpty() ? kNote
				: Lang.tr("Packs go to your resourcepacks folder and are turned on after install.", "Os packs vão para a pasta resourcepacks e ficam ativos depois de instalar.");
		Gfx.textRaw(c, fitRaw(st, w - 100), x + 10, y + h - 15, Theme.DIM);
		if (!kPacks.isEmpty()) {
			String t = Integer.toString(kPacks.size());
			Gfx.textRaw(c, t, x + w - 10 - Gfx.twRaw(t), y + h - 15, Theme.DIM);
		}
	}

	private void kCard(GuiGraphicsExtractor c, KuboPackLogic.Pack p, int x, int y, int w, int clip0, int clip1) {
		boolean hov = hovered(x, y, w, CARD_H) && mouseY >= clip0 && mouseY < clip1;
		float a = anim("kc" + p.id(), hov);
		Gfx.rrectBorder(c, x, y, w, CARD_H, 8, Gfx.blend(TILE, TILE_HOVER, a), Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, a));
		int ry0 = Math.max(y, clip0), ry1 = Math.min(y + CARD_H, clip1);
		if (ry1 > ry0) {
			region(x, ry0, w, ry1 - ry0, () -> {
				kDetail = p;
				kDetailScroll = 0;
			});
		}
		kIcon(c, p, x + 8, y + 8, 50);
		int bw = 62, bh = 18, tx = x + 66;
		Gfx.textRaw(c, fitRaw(p.name(), w - 66 - bw - 16), tx, y + 8, 0xFFFFFFFF);
		Gfx.textRaw(c, fitRaw(Lang.tr("by ", "por ") + p.owner(), w - 66 - 8), tx, y + 19, Theme.DIM);
		int cx = tx, max = x + w - 8;
		for (String tag : p.tags()) {
			int cwid = Gfx.twRaw(tag) + 12;
			if (cx + cwid > max) {
				break;
			}
			Gfx.rrectBorder(c, cx, y + 31, cwid, 14, 5, TILE, Theme.CARD_BORDER);
			Gfx.textRaw(c, tag, cx + 6, y + 34, Theme.DIM);
			cx += cwid + 4;
		}
		String line = (p.version().isEmpty() ? "" : "v" + p.version() + "  -  ") + compact(p.downloads()) + " downloads";
		Gfx.textRaw(c, fitRaw(line, w - 66 - 8), tx, y + CARD_H - 12, 0xFF6B7080);
		kInstallButton(c, p, x + w - 8 - bw, y + 8, bw, bh, clip0, clip1);
	}

	/** Install / progress / Update / Activate / Active, from the real state of the file. */
	private void kInstallButton(GuiGraphicsExtractor c, KuboPackLogic.Pack p, int bx, int by, int bw, int bh, int clip0, int clip1) {
		KuboPacks.Inst in = KuboPacks.installing(p);
		KuboPackLogic.State st = KuboPacks.state(p);
		boolean hov = hovered(bx, by, bw, bh) && mouseY >= clip0 && mouseY < clip1;
		float a = anim("kib" + p.id(), hov);
		int accent = Theme.accent();
		int bg = Gfx.blend(accent, 0xFF000000, 0.35F - 0.15F * a), fg = 0xFFFFFFFF;
		String word;
		Runnable act = null;
		if (in != null && in.state == 0) {
			word = Math.round(in.progress * 100) + "%";
			bg = Theme.OFF_BG;
			fg = Theme.TEXT;
		} else if (in != null && in.state == 2) {
			word = Lang.tr("Retry", "Repetir");
			bg = Gfx.blend(0xFF3A1E22, 0xFF4A242A, a);
			fg = 0xFFE5767A;
			act = () -> KuboPacks.install(p);
			if (hov) {
				tooltip = Lang.tr("Failed: ", "Falhou: ") + in.msg;
			}
		} else if (st == KuboPackLogic.State.UPDATE) {
			word = Lang.tr("Update", "Atualizar");
			act = () -> KuboPacks.install(p);
		} else if (st == KuboPackLogic.State.INSTALLED) {
			if (KuboPacks.active(p)) {
				word = Lang.tr("Active", "Ativo");
				bg = Theme.GREEN_BG;
				fg = Theme.GREEN;
			} else {
				word = Lang.tr("Activate", "Ativar");
				act = () -> KuboPacks.activate(p);
			}
		} else {
			word = Lang.tr("Install", "Instalar");
			act = () -> KuboPacks.install(p);
		}
		Gfx.rrect(c, bx, by, bw, bh, 6, bg);
		if (in != null && in.state == 0) {
			Gfx.rrect(c, bx, by, Math.max(6, Math.round(bw * in.progress)), bh, 6, Gfx.withAlpha(accent, 0xB0));
		}
		Gfx.textFit(c, word, bx + bw / 2, bw - 6, by + (bh - 9) / 2, fg);
		int ry0 = Math.max(by, clip0), ry1 = Math.min(by + bh, clip1);
		if (act != null && ry1 > ry0) {
			final Runnable run = act;
			region(bx, ry0, bw, ry1 - ry0, run);
		}
	}

	private void clipButton(GuiGraphicsExtractor c, String id, int x, int y, int w, int h, String label, Runnable run, int clip0, int clip1) {
		boolean hov = hovered(x, y, w, h) && mouseY >= clip0 && mouseY < clip1;
		float a = anim(id, hov);
		Gfx.rrectBorder(c, x, y, w, h, 6, Gfx.blend(TILE, TILE_HOVER, a), Gfx.blend(Theme.CARD_BORDER, Theme.CARD_BORDER_HOVER, a));
		Gfx.textFit(c, label, x + w / 2, w - 6, y + (h - 9) / 2, 0xFFFFFFFF);
		int ry0 = Math.max(y, clip0), ry1 = Math.min(y + h, clip1);
		if (ry1 > ry0) {
			region(x, ry0, w, ry1 - ry0, run);
		}
	}

	/** A social button: opens the link in the system browser, or copies the text (Discord user names, handles). */
	private void socialButton(GuiGraphicsExtractor c, KuboPackLogic.Link l, String label, int x, int y, int clip0, int clip1) {
		int bw = Gfx.twRaw(label) + 20;
		clipButton(c, "soc" + l.platform(), x, y, bw, 20, label, () -> {
			if (l.opens()) {
				net.minecraft.util.Util.getPlatform().openUri(l.url());
			} else {
				mc.keyboardHandler.setClipboard(l.copy());
				note(Lang.tr("Copied: ", "Copiado: ") + l.copy());
			}
		}, clip0, clip1);
		if (hovered(x, y, bw, 20) && mouseY >= clip0 && mouseY < clip1) {
			tooltip = l.opens() ? l.url() : Lang.tr("Click to copy: ", "Clica para copiar: ") + l.copy();
		}
	}

	private void drawKDetail(GuiGraphicsExtractor c, int x, int y, int w, int h) {
		KuboPackLogic.Pack d = kDetail;
		button(c, "kback", x + 8, y + 8, 80, 22, "< " + Lang.tr("Back", "Voltar"), () -> kDetail = null);
		int top = y + 36, bot = y + h - 20;
		kDetailScroll = Math.max(0, Math.min(kDetailScroll, Math.max(0, kDetailH - (bot - top))));
		c.enableScissor(x + 2, top, x + w - 2, bot);
		int ox = x + 18, cw = w - 36;
		int yy = top - kDetailScroll + 2;

		// banner on top (cropped to fill, never stretched)
		int bh = 84, bx = x + 8, bw = w - 16;
		Gfx.rrect(c, bx, yy, bw, bh, 8, 0xFF14161B);
		KuboPacks.Img b = KuboPacks.image(d.banner(), 640);
		if (b != null) {
			float sc = Math.max(bw / (float) b.width(), bh / (float) b.height());
			int rw = Math.min(b.width(), Math.round(bw / sc)), rh = Math.min(b.height(), Math.round(bh / sc));
			Gfx.texUv(c, b.id(), bx, yy, bw, bh, (b.width() - rw) / 2F, (b.height() - rh) / 2F, rw, rh, b.width(), b.height(), 0xFFFFFFFF);
		}
		yy += bh + 12;

		kIcon(c, d, ox, yy, 64);
		int tx = ox + 76, tw = cw - 76;
		c.pose().pushMatrix();
		c.pose().translate(tx, yy);
		c.pose().scale(1.5F, 1.5F);
		Gfx.textRaw(c, fitRaw(d.name(), Math.round(tw / 1.5F)), 0, 0, 0xFFFFFFFF);
		c.pose().popMatrix();
		Gfx.textRaw(c, fitRaw(Lang.tr("by ", "por ") + d.owner(), tw), tx, yy + 20, Theme.DIM);
		StringBuilder info = new StringBuilder();
		if (!d.version().isEmpty()) {
			info.append("v").append(d.version());
		}
		if (!d.mcVersion().isEmpty()) {
			info.append(info.length() > 0 ? "  -  " : "").append("Minecraft ").append(d.mcVersion());
		}
		if (d.size() > 0) {
			info.append(info.length() > 0 ? "  -  " : "").append(String.format(java.util.Locale.ROOT, "%.1f MB", d.size() / 1048576.0));
		}
		info.append(info.length() > 0 ? "  -  " : "").append(compact(d.downloads())).append(" downloads");
		Gfx.textRaw(c, fitRaw(info.toString(), tw), tx, yy + 32, 0xFF6B7080);
		kInstallButton(c, d, tx, yy + 46, 110, 22, top, bot);
		yy += 80;

		for (String l : wrap(d.description(), cw, 14)) {
			Gfx.textRaw(c, l, ox, yy, Theme.TEXT);
			yy += 11;
		}
		if (!d.credits().isBlank()) {
			yy += 8;
			Gfx.textRaw(c, Lang.tr("Credits", "Créditos"), ox, yy, Theme.DIM);
			yy += 12;
			for (String l : wrap(d.credits(), cw, 6)) {
				Gfx.textRaw(c, l, ox, yy, Theme.TEXT);
				yy += 11;
			}
		}

		// social links (open in the system browser; a Discord user name is copied instead)
		String[][] soc = {{"discord", "Discord", d.discord()}, {"youtube", "YouTube", d.youtube()}, {"twitter", "Twitter", d.twitter()},
				{"website", "Website", d.website()}};
		int sx = ox, any = 0;
		yy += 10;
		for (String[] sp : soc) {
			KuboPackLogic.Link l = KuboPackLogic.link(sp[0], sp[2]);
			if (l == null) {
				continue;
			}
			int bw2 = Gfx.twRaw(sp[1]) + 20;
			if (sx + bw2 > ox + cw) {
				sx = ox;
				yy += 24;
			}
			socialButton(c, l, sp[1], sx, yy, top, bot);
			sx += bw2 + 6;
			any++;
		}
		if (any > 0) {
			yy += 28;
		}

		int cx = ox;
		for (String tag : d.tags()) {
			int cwid = Gfx.twRaw(tag) + 14;
			if (cx + cwid > ox + cw) {
				cx = ox;
				yy += 20;
			}
			Gfx.rrectBorder(c, cx, yy, cwid, 16, 6, TILE, Theme.CARD_BORDER);
			Gfx.textRaw(c, tag, cx + 7, yy + 4, Theme.DIM);
			cx += cwid + 5;
		}
		yy += 24;
		kDetailH = yy + kDetailScroll - top;
		c.disableScissor();
		if (System.currentTimeMillis() - kNoteAt < 5000 && !kNote.isEmpty()) {
			Gfx.textRaw(c, fitRaw(kNote, w - 24), x + 12, y + h - 15, Theme.DIM);
		}
	}

	// ------------------------------------------------------------------ input (called by the menu)
	/** Mouse wheel over the panel (gx = x in menu units). */
	public void scrolled(double gx, double v) {
		if (gx < ox + M + SW) {
			sideScroll = Math.max(0, sideScroll - (int) Math.round(v * 18));
		} else if (kubo) {
			if (kDetail != null) {
				kDetailScroll = Math.max(0, kDetailScroll - (int) Math.round(v * 26));
			} else {
				kScroll = Math.max(0, kScroll - (int) Math.round(v * 26));
			}
		} else {
			scroll = Math.max(0, scroll - (int) Math.round(v * 26));
		}
	}

	/** Esc / back: closes the open detail page. Returns false when there was nothing to close. */
	public boolean back() {
		if (kubo ? kDetail != null : detail != null) {
			detail = null;
			kDetail = null;
			return true;
		}
		return false;
	}

	/** Enter in the search box. */
	public void submit() {
		startSearch(false);
	}

	/** The menu leaves this panel: forget pending answers and free the pictures. */
	public void dispose() {
		gen++; // drop answers that are still on their way
		kGen++;
		KuboPacks.releaseImages(); // free the icon / banner textures of the Caos packs
	}

	private void close() {
		host.close();
	}
}
