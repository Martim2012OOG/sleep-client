package dev.solclient.voice.persona;

import com.mojang.blaze3d.platform.NativeImage;
import dev.solclient.Gfx;
import dev.solclient.Lang;
import dev.solclient.gui.TextBox;
import dev.solclient.gui.Theme;
import dev.solclient.mods.PersonasMod;
import dev.solclient.option.Option;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * The voice changer, drawn INSIDE the mod's settings page (no extra window), like Texture Styles. A banner with the chosen
 * voice, the on / off switches, a search box and category chips, then a grid of round photos. Every photo can be swapped for
 * any picture from your computer (pencil on a voice, or drag a photo onto the window = the chosen voice); it is cut into a
 * rounded square (like the voice apps' cards), so real photos look right. Your pictures live in kubo/personas/pictures/&lt;voice&gt;.png|jpg.
 */
public final class PersonaPanel {

	public static final PersonaPanel INSTANCE = new PersonaPanel();

	/** Size of the round pictures on the GPU (drawn smaller, smooth filtering). */
	private static final int PIC = 160;
	private static final int CELL = 74, ROW_H = 88;

	/** Last time the panel was on screen (the "hear myself" playback stops when it is gone). */
	public static volatile long lastDrawn;

	private static final class Smooth extends DynamicTexture {
		Smooth(NativeImage img) {
			super(() -> "kubo_persona_pic", img);
			this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
		}
	}

	private static final class Pic {
		volatile int state; // 0 not started, 1 loading, 2 pixels ready, 3 on the GPU, 4 failed
		volatile int[] px;
		Identifier id;
	}

	private final Map<String, Pic> pics = new ConcurrentHashMap<>();
	private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
		Thread t = new Thread(r, "kubo-persona-pics");
		t.setDaemon(true);
		return t;
	});
	private final TextBox search = new TextBox(32);
	/** -1 = favourites, otherwise an index of Persona.CATS (0 = all). */
	private int cat;
	private long shownAt;
	private volatile String note = "";

	private PersonaPanel() {
	}

	public Option.Custom.Drawer drawer() {
		return this::draw;
	}

	// ------------------------------------------------------------------ pictures
	private static Path picturesDir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("personas").resolve("pictures");
	}

	/** Your own picture for a voice, if you set one. */
	private static Path override(String id) {
		for (String ext : new String[] {".png", ".jpg", ".jpeg"}) {
			Path f = picturesDir().resolve(id + ext);
			if (Files.isRegularFile(f)) {
				return f;
			}
		}
		return null;
	}

	/** Real pictures: the photo of each voice's Wikipedia article, downloaded once and kept in kubo/personas/cache. */
	private static final Map<String, String> WIKI = Map.ofEntries(Map.entry("ronaldo", "Cristiano_Ronaldo"), Map.entry("messi", "Lionel_Messi"),
			Map.entry("speed", "IShowSpeed"), Map.entry("mrbeast", "MrBeast"), Map.entry("patrickjane", "Patrick_Jane"), Map.entry("patrick", "Patrick_Star"),
			Map.entry("krabs", "Mr._Krabs"), Map.entry("plankton", "Plankton_and_Karen"),
			Map.entry("shrek", "Shrek_(character)"), Map.entry("donkey", "Donkey_(Shrek)"), Map.entry("minion", "Minions_(Despicable_Me)"),
			Map.entry("pikachu", "Pikachu"), Map.entry("sonic", "Sonic_the_Hedgehog_(character)"), Map.entry("homer", "Homer_Simpson"),
			Map.entry("peppa", "Peppa_Pig"), Map.entry("vader", "Darth_Vader"), Map.entry("yoda", "Yoda"), Map.entry("gollum", "Gollum"),
			Map.entry("groot", "Groot"), Map.entry("venom", "Venom_(character)"), Map.entry("joker", "Joker_(character)"), Map.entry("batman", "Batman"),
			Map.entry("optimus", "Optimus_Prime"), Map.entry("stitch", "Stitch_(Lilo_&_Stitch)"), Map.entry("goku", "Goku"),
			Map.entry("herobrine", "Herobrine"), Map.entry("creeper", "Creeper_(Minecraft)"),  
			Map.entry("enderman", "Enderman"), Map.entry("chipmunk", "Chipmunk"), Map.entry("helium", "Balloon"), Map.entry("clown", "Clown"),
			Map.entry("baby", "Infant"), Map.entry("kid", "Child"), Map.entry("grandpa", "Grandparent"), Map.entry("grandma", "Grandmother"),
			Map.entry("dizzy", "Dizziness"), Map.entry("fast", "Cheetah"), Map.entry("slow", "Sloth"), Map.entry("cowboy", "Cowboy"),
			Map.entry("pirate", "Piracy"), Map.entry("santa", "Santa_Claus"), Map.entry("snowman", "Snowman"), Map.entry("hacker", "Anonymous_(hacker_group)"),
			Map.entry("demon", "Demon"), Map.entry("villain", "Villain"), Map.entry("monster", "Monster"), Map.entry("goblin", "Goblin"),
			Map.entry("zombie", "Zombie"), Map.entry("vampire", "Vampire"), Map.entry("ghost", "Ghost"), Map.entry("skeleton", "Human_skeleton"),
			Map.entry("doll", "Doll"), Map.entry("giant", "Giant"), Map.entry("wizard", "Wizard_(fantasy)"), Map.entry("elf", "Elf"),
			Map.entry("fairy", "Fairy"), Map.entry("unicorn", "Unicorn"), Map.entry("dragon", "Dragon"), Map.entry("knight", "Knight"),
			Map.entry("cat", "Cat"), Map.entry("mouse", "House_mouse"), Map.entry("bear", "Brown_bear"), Map.entry("lion", "Lion"),
			Map.entry("frog", "Frog"), Map.entry("duck", "Duck"), Map.entry("pig", "Pig"), Map.entry("bee", "Western_honey_bee"), Map.entry("bat", "Bat"),
			Map.entry("fish", "Clownfish"), Map.entry("robot", "Robot"), Map.entry("cyborg", "Cyborg"), Map.entry("alien", "Grey_alien"),
			Map.entry("ufo", "Unidentified_flying_object"), Map.entry("astronaut", "Astronaut"), Map.entry("glitch", "Glitch"),
			Map.entry("8bit", "Pixel_art"), Map.entry("phone", "Rotary_dial"), Map.entry("radio", "Radio_receiver"), Map.entry("megaphone", "Megaphone"),
			Map.entry("tvhost", "Television_presenter"), Map.entry("trailer", "Trailer_(promotion)"), Map.entry("stadium", "Stadium"),
			Map.entry("cathedral", "Cathedral"), Map.entry("cave", "Cave"), Map.entry("bathroom", "Shower"), Map.entry("whisper", "Whispering"),
			Map.entry("crowd", "Crowd"), Map.entry("bass", "Subwoofer"));

	private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
			.connectTimeout(java.time.Duration.ofSeconds(6)).build();

	private static Path cacheDir() {
		return picturesDir().getParent().resolve("cache");
	}

	private static final String UA = "SleepClient/1.0 (Minecraft mod; voice changer pictures)";
	private static Map<String, String> thumbs;
	/** Wikipedia said "too many requests": no more downloads until the game restarts (the bundled pictures are used). */
	private static volatile boolean slowDown;
	private static final Object DOWNLOAD = new Object();

	private static String http(String url) throws Exception {
		var res = HTTP.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).header("User-Agent", UA).timeout(java.time.Duration.ofSeconds(10))
				.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() == 429) {
			slowDown = true;
		}
		return res.statusCode() == 200 ? res.body() : null;
	}

	/** voice id -> picture URL, for all voices at once (2 requests; kept on disk for a week). */
	private static synchronized Map<String, String> thumbs() {
		if (thumbs != null) {
			return thumbs;
		}
		Map<String, String> out = new java.util.HashMap<>();
		Path f = cacheDir().resolve("pictures.json");
		try {
			if (Files.isRegularFile(f) && System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis() < 7L * 86_400_000L) {
				var o = com.google.gson.JsonParser.parseString(Files.readString(f)).getAsJsonObject();
				for (String k : o.keySet()) {
					out.put(k, o.get(k).getAsString());
				}
				return thumbs = out;
			}
			List<String> ids = new ArrayList<>(WIKI.keySet());
			Map<String, String> byTitle = new java.util.HashMap<>();
			for (int i = 0; i < ids.size(); i += 45) {
				StringBuilder titles = new StringBuilder();
				for (String id : ids.subList(i, Math.min(ids.size(), i + 45))) {
					titles.append(titles.length() == 0 ? "" : "|").append(WIKI.get(id).replace('_', ' '));
				}
				String body = http("https://en.wikipedia.org/w/api.php?action=query&format=json&redirects=1&prop=pageimages&piprop=thumbnail&pithumbsize=400&titles="
						+ java.net.URLEncoder.encode(titles.toString(), java.nio.charset.StandardCharsets.UTF_8));
				if (body == null) {
					return out; // offline / busy: try again next time the game starts (not cached)
				}
				var q = com.google.gson.JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("query");
				Map<String, String> moved = new java.util.HashMap<>();
				for (String arr : new String[] {"normalized", "redirects"}) {
					if (q.has(arr)) {
						for (var e : q.getAsJsonArray(arr)) {
							moved.put(e.getAsJsonObject().get("from").getAsString(), e.getAsJsonObject().get("to").getAsString());
						}
					}
				}
				for (var e : q.getAsJsonObject("pages").entrySet()) {
					var pg = e.getValue().getAsJsonObject();
					if (pg.has("thumbnail")) {
						byTitle.put(pg.get("title").getAsString(), pg.getAsJsonObject("thumbnail").get("source").getAsString());
					}
				}
				for (String id : ids.subList(i, Math.min(ids.size(), i + 45))) {
					String t = WIKI.get(id).replace('_', ' ');
					for (int hop = 0; hop < 3 && moved.containsKey(t); hop++) {
						t = moved.get(t);
					}
					if (byTitle.containsKey(t)) {
						out.put(id, byTitle.get(t));
					}
				}
			}
			Files.createDirectories(cacheDir());
			com.google.gson.JsonObject o = new com.google.gson.JsonObject();
			out.forEach(o::addProperty);
			Files.writeString(f, o.toString());
		} catch (Throwable e) {
			System.err.println("[Sleep] voice pictures: " + e);
		}
		return thumbs = out;
	}

	/** The voice's real picture (its Wikipedia article's photo), downloaded once into kubo/personas/cache; null offline / when there is none. */
	private static BufferedImage wiki(Persona p) {
		if (!WIKI.containsKey(p.id)) {
			return null;
		}
		Path f = cacheDir().resolve(p.id + ".img");
		try {
			if (Files.isRegularFile(f)) {
				return readFile(f);
			}
			String url = thumbs().get(p.id);
			if (url == null || slowDown) {
				return null;
			}
			synchronized (DOWNLOAD) { // one download at a time, a little apart: Wikipedia limits how fast you may ask
				if (slowDown) {
					return null;
				}
				var img = HTTP.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).header("User-Agent", UA)
						.timeout(java.time.Duration.ofSeconds(10)).build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray());
				Thread.sleep(120);
				if (img.statusCode() == 429) {
					slowDown = true;
				}
				if (img.statusCode() != 200) {
					return null;
				}
				BufferedImage b = decode(img.body());
				if (b != null) {
					Files.createDirectories(cacheDir());
					Files.write(f, img.body());
				}
				return b;
			}
		} catch (Throwable e) {
			return null; // offline: the bundled picture is used
		}
	}

	private static String key(Persona p) {
		Path f = override(p.id);
		if (f == null) {
			f = p.imageFile;
		}
		try {
			return f == null ? p.id : p.id + "@" + Files.getLastModifiedTime(f).toMillis();
		} catch (java.io.IOException e) {
			return p.id;
		}
	}

	/** The round picture of a voice, or null while it is loading / when there is none. */
	private Identifier picture(Persona p) {
		Pic pic = pics.computeIfAbsent(key(p), k -> new Pic());
		if (pic.state == 0) {
			pic.state = 1;
			Path file = override(p.id);
			pool.submit(() -> {
				try {
					BufferedImage img = file != null ? readFile(file) : p.imageFile != null ? readFile(p.imageFile) : null;
					if (img == null && !p.custom) {
						img = wiki(p);
					}
					if (img == null && !p.custom) {
						img = readResource(p.image);
					}
					if (img == null) {
						pic.state = 4;
						return;
					}
					pic.px = round(img);
					pic.state = 2;
				} catch (Throwable e) {
					System.err.println("[Sleep] persona picture " + p.id + ": " + e);
					pic.state = 4;
				}
			});
		}
		if (pic.state == 2) {
			upload(p, pic);
		}
		return pic.state == 3 ? pic.id : null;
	}

	private static BufferedImage readFile(Path f) throws Exception {
		try (InputStream in = Files.newInputStream(f)) {
			return decode(in.readAllBytes());
		}
	}

	private static BufferedImage readResource(Identifier id) throws Exception {
		var r = Minecraft.getInstance().getResourceManager().getResource(id);
		if (r.isEmpty()) {
			return null;
		}
		try (InputStream in = r.get().open()) {
			return decode(in.readAllBytes());
		}
	}

	/** PNG / JPG / BMP / GIF through ImageIO; PNG through Minecraft's reader if ImageIO is not there. */
	private static BufferedImage decode(byte[] bytes) throws Exception {
		try {
			BufferedImage b = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
			if (b != null) {
				return b;
			}
		} catch (Throwable ignored) {
			// try below
		}
		try (NativeImage n = NativeImage.read(new java.io.ByteArrayInputStream(bytes))) {
			BufferedImage b = new BufferedImage(n.getWidth(), n.getHeight(), BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < n.getHeight(); y++) {
				for (int x = 0; x < n.getWidth(); x++) {
					b.setRGB(x, y, n.getPixel(x, y));
				}
			}
			return b;
		}
	}

	/** Corner radius of the cards, as a part of their size. */
	private static final float CORNER = 0.22F;

	/** Centre square, scaled smoothly to PIC x PIC, cut into a rounded square with soft (anti-aliased) corners. */
	private static int[] round(BufferedImage src) {
		int s = Math.min(src.getWidth(), src.getHeight());
		// tall photos (people): keep the upper part, where the face is
		int oy = src.getHeight() > src.getWidth() ? Math.round((src.getHeight() - s) * 0.15F) : (src.getHeight() - s) / 2;
		BufferedImage sq = src.getSubimage((src.getWidth() - s) / 2, oy, s, s);
		// halve while it is much bigger: smoother than one big bicubic step
		while (s / 2 >= PIC * 2) {
			s /= 2;
			sq = scale(sq, s);
		}
		BufferedImage out = scale(sq, PIC);
		int[] px = new int[PIC * PIC];
		float r = PIC * CORNER, c = (PIC - 1) / 2F, half = PIC / 2F;
		for (int y = 0; y < PIC; y++) {
			for (int x = 0; x < PIC; x++) {
				// distance outside the rounded square (negative inside)
				float qx = Math.max(Math.abs(x - c) - (half - r), 0), qy = Math.max(Math.abs(y - c) - (half - r), 0);
				float d = (float) Math.hypot(qx, qy) - r;
				float a = Math.max(0, Math.min(1, 0.5F - d));
				int argb = out.getRGB(x, y);
				int alpha = Math.round(((argb >>> 24) & 0xFF) * a);
				px[y * PIC + x] = (alpha << 24) | (argb & 0xFFFFFF);
			}
		}
		return px;
	}

	private static BufferedImage scale(BufferedImage in, int size) {
		BufferedImage o = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		var g = o.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawImage(in, 0, 0, size, size, null);
		g.dispose();
		return o;
	}

	private void upload(Persona p, Pic pic) {
		int[] px = pic.px;
		pic.px = null;
		try {
			NativeImage img = new NativeImage(PIC, PIC, false);
			for (int y = 0; y < PIC; y++) {
				for (int x = 0; x < PIC; x++) {
					img.setPixel(x, y, px[y * PIC + x]);
				}
			}
			Smooth tex = new Smooth(img);
			tex.upload();
			Identifier id = Gfx.id("generated/persona_round_" + p.id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_") + "_"
					+ Integer.toHexString(key(p).hashCode()));
			Minecraft.getInstance().getTextureManager().register(id, tex);
			pic.id = id;
			pic.state = 3;
		} catch (Throwable e) {
			pic.state = 4;
		}
	}

	/** Uses a picture from your computer for a voice (copied into kubo/personas/pictures). */
	public void setPicture(Persona p, Path file) {
		String fn = file.getFileName().toString().toLowerCase(Locale.ROOT);
		String ext = fn.endsWith(".png") ? ".png" : fn.endsWith(".jpg") || fn.endsWith(".jpeg") ? ".jpg" : null;
		try {
			Files.createDirectories(picturesDir());
			BufferedImage check = readFile(file);
			if (check == null) {
				note = Lang.tr("That file is not a picture (use PNG or JPG).", "Esse ficheiro não é uma imagem (usa PNG ou JPG).");
				return;
			}
			resetPicture(p);
			if (ext == null) { // GIF / BMP / ...: keep it as PNG
				javax.imageio.ImageIO.write(check, "png", picturesDir().resolve(p.id + ".png").toFile());
			} else {
				Files.copy(file, picturesDir().resolve(p.id + ext), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			note = Lang.tr("New picture for ", "Nova imagem para ") + Lang.t(p.name) + ".";
		} catch (Throwable e) {
			note = Lang.tr("Could not use that picture: ", "Não deu para usar essa imagem: ") + e.getMessage();
		}
	}

	private void resetPicture(Persona p) {
		for (String ext : new String[] {".png", ".jpg", ".jpeg"}) {
			try {
				Files.deleteIfExists(picturesDir().resolve(p.id + ext));
			} catch (java.io.IOException ignored) {
				// still there; the next one wins anyway
			}
		}
	}

	private void choosePicture(Persona p) {
		note = Lang.tr("Choose a picture...", "Escolhe uma imagem...");
		Thread t = new Thread(() -> {
			String path;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer f = stack.mallocPointer(5);
				f.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).put(stack.UTF8("*.bmp")).put(stack.UTF8("*.gif"));
				f.flip();
				path = TinyFileDialogs.tinyfd_openFileDialog(Lang.tr("Picture for ", "Imagem para ") + Lang.t(p.name), "", f, "Images", false);
			} catch (Throwable e) {
				note = Lang.tr("No file picker here: drag a photo onto the window.", "Sem janela de ficheiros: arrasta uma foto para a janela.");
				return;
			}
			if (path == null) {
				note = "";
				return;
			}
			Minecraft.getInstance().execute(() -> setPicture(p, Path.of(path)));
		}, "kubo-persona-pick");
		t.setDaemon(true);
		t.start();
	}

	// ------------------------------------------------------------------ typing (forwarded by the menu)
	public boolean typed(int codepoint) {
		if (System.currentTimeMillis() - shownAt < 250 || codepoint < 32 || codepoint == 127) {
			return true; // the key that opened the page
		}
		search.typed(codepoint);
		return true;
	}

	/** Backspace & co. go to the search; Escape clears it first. */
	public boolean key(int key) {
		if (search.isEmpty()) {
			return false;
		}
		if (key == 256) {
			search.clear();
			return true;
		}
		return search.key(key);
	}

	// ------------------------------------------------------------------ list
	private List<Persona> shown(PersonasMod m) {
		String q = search.text().trim().toLowerCase(Locale.ROOT);
		List<Persona> out = new ArrayList<>();
		for (Persona p : Persona.all()) {
			if (cat == -1 && !m.isFav(p.id)) {
				continue;
			}
			if (cat > 0 && !p.category.equals(Persona.CATS[cat])) {
				continue;
			}
			if (!q.isEmpty() && !p.name.toLowerCase(Locale.ROOT).contains(q) && !Lang.t(p.name).toLowerCase(Locale.ROOT).contains(q)
					&& !p.tagline.toLowerCase(Locale.ROOT).contains(q) && !Lang.t(p.tagline).toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			out.add(p);
		}
		return out;
	}

	private static int count(PersonasMod m, int c) {
		int n = 0;
		for (Persona p : Persona.all()) {
			if (c == -1 ? m.isFav(p.id) : c == 0 || p.category.equals(Persona.CATS[c])) {
				n++;
			}
		}
		return n;
	}

	/** Short tags for the banner: what the voice does. */
	private static List<String> tags(Persona p) {
		List<String> t = new ArrayList<>();
		if (p.pitch != 0) {
			t.add(p.pitch > 0 ? "Higher" : "Deeper");
		}
		if (p.ring > 0.2F) {
			t.add("Robot");
		}
		if (p.drive > 0.25F) {
			t.add("Growl");
		}
		if (p.echo > 0.15F || p.reverb > 0.3F) {
			t.add("Echo");
		}
		if (p.vibrato > 0.3F) {
			t.add("Wobble");
		}
		if (p.crush > 0.4F) {
			t.add("8-bit");
		}
		if (p.bandHigh > 0 && p.bandHigh < 4000) {
			t.add("Radio");
		}
		if (p.chorus > 0.4F) {
			t.add("Crowd");
		}
		if (p.whisper > 0.4F) {
			t.add("Whisper");
		}
		return t;
	}

	private static boolean in(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	// ------------------------------------------------------------------ drawing
	/** A rounded-square photo, with a coloured frame when ring != 0; a coloured card with initials when there is no picture yet. */
	private void portrait(GuiGraphicsExtractor c, Persona p, int cx, int cy, int size, int ring) {
		int x = cx - size / 2, y = cy - size / 2, r = Math.round(size * CORNER);
		Gfx.rrect(c, x, y + 2, size, size, r, 0x55000000); // soft shadow
		if (ring != 0) {
			Gfx.rrect(c, x - 2, y - 2, size + 4, size + 4, r + 2, ring);
		}
		Identifier id = picture(p);
		if (id != null) {
			Gfx.rrect(c, x, y, size, size, r, 0xFF22242C);
			Gfx.tex(c, id, x, y, size, size, size, size, 0xFFFFFFFF);
			return;
		}
		// no picture (yet): a colourful card made from the name
		float hue = (p.id.hashCode() & 0xFFFF) / 65536F;
		int c1 = 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.55F, 0.55F), c2 = 0xFF000000 | java.awt.Color.HSBtoRGB(hue + 0.08F, 0.6F, 0.32F);
		Gfx.rrect(c, x, y, size, size, r, Gfx.blend(c2, c1, 0.6F));
		String[] w = Lang.t(p.name).split(" ");
		String ini = (w[0].substring(0, 1) + (w.length > 1 && !w[1].isEmpty() ? w[1].substring(0, 1) : "")).toUpperCase(Locale.ROOT);
		var mat = c.pose();
		mat.pushMatrix();
		mat.translate(cx, cy);
		mat.scale(size / 28F, size / 28F);
		Gfx.textRaw(c, ini, -Gfx.tw(ini) / 2, -4, 0xF0FFFFFF);
		mat.popMatrix();
	}

	private int draw(GuiGraphicsExtractor c, int mx, int my, int x, int y, int w, Option.Custom.Reg reg) {
		PersonasMod m = PersonasMod.instance;
		if (m == null) {
			return 0;
		}
		long now = System.currentTimeMillis();
		if (now - lastDrawn > 500) {
			shownAt = now;
		}
		lastDrawn = now;
		int accent = Theme.accent() | 0xFF000000;
		int top = y;

		// ---------------------------------------------------------- banner: the chosen voice
		Persona cur = m.current();
		int bh = 92;
		Gfx.rrect(c, x, y, w, bh, 12, Gfx.blend(Theme.CARD, accent, 0.10F));
		Gfx.gradientH(c, x + w / 3, y, w - w / 3, bh, 0x00000000, Gfx.withAlpha(accent, 0x30));
		int pcx = x + 14 + 34, pcy = y + bh / 2;
		float pulse = m.enabled ? 0.5F + 0.5F * (float) Math.sin(now / 450.0) : 0;
		if (m.enabled) {
			int gr = Math.round(37 + 2 * pulse);
			Gfx.rrect(c, pcx - gr, pcy - gr, gr * 2, gr * 2, Math.round(gr * CORNER * 1.2F), Gfx.withAlpha(accent, Math.round(40 + 40 * pulse)));
		}
		portrait(c, cur, pcx, pcy, 66, m.enabled ? accent : 0xFF4A4D59);
		int tx = x + 14 + 68 + 14;
		var mat = c.pose();
		mat.pushMatrix();
		mat.translate(tx, y + 16);
		mat.scale(1.6F, 1.6F);
		Gfx.text(c, cur.name, 0, 0, Theme.TEXT);
		mat.popMatrix();
		Gfx.textClip(c, cur.tagline, tx, y + 34, w - (tx - x) - 12, Theme.DIM);
		int chx = tx;
		for (String t : tags(cur)) {
			int tw = Gfx.tw(Lang.t(t)) + 12;
			if (chx + tw > x + w - 10) {
				break;
			}
			Gfx.rrect(c, chx, y + 48, tw, 14, 7, 0x22FFFFFF);
			Gfx.text(c, t, chx + 6, y + 51, Theme.TEXT);
			chx += tw + 4;
		}
		// switches along the bottom of the banner
		int sy = y + bh - 20;
		int sx = toggle(c, mx, my, tx, sy, "Voice changer", m.enabled, accent, () -> m.setEnabled(!m.enabled), reg);
		toggle(c, mx, my, sx + 14, sy, "Hear myself", PersonaMonitor.on(), accent, PersonaMonitor::toggle, reg);
		// live state, top right
		boolean live = m.enabled && (PersonaHook.hookAlive() || SystemVoice.on());
		String state = !m.enabled ? "Voice changer is off" : SystemVoice.on() ? "Live on the whole PC" : PersonaHook.hookAlive() ? "Live in your voice chat" : "Ready";
		int stw = Gfx.tw(Lang.t(state)) + 20;
		int stx = x + w - stw - 10;
		Gfx.rrect(c, stx, y + 10, stw, 16, 8, !m.enabled ? 0x40E5484D : live ? 0x4022C55E : 0x22FFFFFF);
		Gfx.circle(c, stx + 8, y + 18, 3, !m.enabled ? 0xFFF87171 : live ? 0xFF4ADE80 : 0xFFB8BCCB);
		Gfx.text(c, state, stx + 14, y + 14, Theme.TEXT);
		y += bh + 8;

		// ---------------------------------------------------------- where: Minecraft / PC / both
		String[] wh = {"Only Minecraft", "Whole PC", "Both"};
		int segW = Math.min(w, 330), segX = x + w - segW, segH = 22;
		// search on the left of the same row
		int fw = w - segW - 8;
		if (fw < 120) { // narrow: search gets its own row
			fw = w;
		}
		boolean focused = !search.isEmpty();
		Gfx.rrectBorder(c, x, y, fw, segH, 11, Theme.FIELD, focused ? Gfx.withAlpha(accent, 0xC0) : Theme.CARD_BORDER);
		Gfx.tex(c, Theme.iconPublic("search"), x + 8, y + 6, 10, 10, 64, 64, Theme.DIM);
		if (search.isEmpty()) {
			Gfx.textClip(c, Lang.tr("Search voices (just type)", "Procurar vozes (escreve)"), x + 23, y + 7, fw - 30, 0xFF6C7182);
		}
		search.draw(c, x, y, fw, segH, x + 23, fw - 34, focused, accent, Theme.TEXT);
		if (fw == w) {
			y += segH + 6;
			segX = x;
			segW = w;
		}
		Gfx.rrect(c, segX, y, segW, segH, 11, Theme.FIELD);
		int sw3 = (segW - 4) / 3;
		for (int i = 0; i < 3; i++) {
			int bx = segX + 2 + i * sw3;
			boolean on = m.where.idx() == i, hov = in(mx, my, bx, y + 2, sw3, segH - 4);
			if (on || hov) {
				Gfx.rrect(c, bx, y + 2, sw3, segH - 4, 9, on ? accent : Theme.CARD_HOVER);
			}
			Gfx.textFit(c, wh[i], bx + sw3 / 2, sw3 - 8, y + 7, on ? 0xFFFFFFFF : Theme.DIM);
			final int wi = i;
			reg.region(bx, y + 2, sw3, segH - 4, () -> m.where.set(wi));
		}
		y += segH + 8;

		// ---------------------------------------------------------- category chips
		int cx = x;
		int[] order = new int[Persona.CATS.length + 1];
		order[0] = 0;
		order[1] = -1;
		for (int i = 1; i < Persona.CATS.length; i++) {
			order[i + 1] = i;
		}
		for (int id : order) {
			String name = id == -1 ? "Favourites" : id == 0 ? "All voices" : Persona.CAT_NAMES[id];
			int n = count(m, id);
			if (id == Persona.CATS.length - 1 && n == 0) {
				continue; // "Mine" only when you made one
			}
			String num = String.valueOf(n);
			int cw = Gfx.tw(Lang.t(name)) + Gfx.tw(num) + 26 + (id == -1 ? 12 : 0);
			if (cx + cw > x + w) {
				cx = x;
				y += 24;
			}
			boolean on = cat == id, hov = in(mx, my, cx, y, cw, 20);
			Gfx.rrect(c, cx, y, cw, 20, 10, on ? accent : hov ? Theme.CARD_HOVER : Theme.CARD);
			int lx = cx + 10;
			if (id == -1) {
				Gfx.tex(c, Theme.iconPublic("star_filled"), lx, y + 5, 10, 10, 64, 64, on ? 0xFFFFFFFF : 0xFFFFC94A);
				lx += 12;
			}
			Gfx.text(c, name, lx, y + 6, on ? 0xFFFFFFFF : Theme.TEXT);
			Gfx.text(c, num, cx + cw - 10 - Gfx.tw(num), y + 6, on ? 0xCCFFFFFF : 0xFF6C7182);
			final int ci = id;
			reg.region(cx, y, cw, 20, () -> cat = ci);
			cx += cw + 5;
		}
		y += 30;

		// ---------------------------------------------------------- grid of round photos
		List<Persona> list = shown(m);
		int cols = Math.max(3, w / CELL), cwid = w / cols;
		// (no off-screen skipping: the menu has its own coordinates and scissor, the window height means nothing here)
		for (int i = 0; i < list.size(); i++) {
			Persona p = list.get(i);
			int gx = x + (i % cols) * cwid, gy = y + (i / cols) * ROW_H;
			boolean sel = p.id.equals(m.character.get()), hov = in(mx, my, gx, gy, cwid, ROW_H);
			int pcxx = gx + cwid / 2, pcyy = gy + 31;
			int size = hov ? 58 : 54;
			if (hov && !sel) {
				Gfx.rrect(c, gx + 2, gy, cwid - 4, ROW_H - 4, 10, Theme.CARD_HOVER);
			}
			if (sel) {
				Gfx.rrect(c, gx + 2, gy, cwid - 4, ROW_H - 4, 10, Gfx.blend(Theme.CARD, accent, 0.18F));
				int gr = size / 2 + 5;
				Gfx.rrect(c, pcxx - gr, pcyy - gr, gr * 2, gr * 2, Math.round(gr * CORNER * 1.2F), Gfx.withAlpha(accent, 0x50));
			}
			portrait(c, p, pcxx, pcyy, size, sel ? accent : hov ? 0x66FFFFFF : 0);
			Gfx.textFit(c, p.name, pcxx, cwid - 8, gy + 64, sel ? 0xFFFFFFFF : Theme.TEXT);
			String cn = Persona.CAT_NAMES[Math.max(0, java.util.Arrays.asList(Persona.CATS).indexOf(p.category))];
			Gfx.textFit(c, cn, pcxx, cwid - 8, gy + 74, 0xFF6C7182);
			// the whole tile picks the voice; the small buttons are registered after it so they win
			reg.region(gx, gy, cwid, ROW_H - 4, () -> {
				m.pick(p);
				if (!m.enabled) {
					m.setEnabled(true);
				}
			});
			boolean fav = m.isFav(p.id);
			if (fav || hov) {
				int fx = pcxx + size / 2 - 3, fy = pcyy - size / 2 + 3;
				boolean hf = in(mx, my, fx - 7, fy - 7, 14, 14);
				Gfx.circle(c, fx, fy, 7, 0xE6101116);
				Gfx.tex(c, Theme.iconPublic(fav ? "star_filled" : "star_outline"), fx - 5, fy - 5, 10, 10, 64, 64, fav ? 0xFFFFC94A : hf ? 0xFFFFFFFF : 0xFFB8BCCB);
				reg.region(fx - 7, fy - 7, 14, 14, () -> m.toggleFav(p.id));
			}
			if (hov) {
				// change the picture (and put the original back when you already changed it)
				int ex = pcxx - size / 2 + 3, ey = pcyy - size / 2 + 3;
				boolean he = in(mx, my, ex - 7, ey - 7, 14, 14);
				Gfx.circle(c, ex, ey, 7, 0xE6101116);
				Gfx.tex(c, Theme.iconPublic("edit"), ex - 4, ey - 4, 8, 8, 64, 64, he ? 0xFFFFFFFF : 0xFFB8BCCB);
				reg.region(ex - 7, ey - 7, 14, 14, () -> choosePicture(p));
				if (override(p.id) != null) {
					int rx = pcxx - size / 2 + 3, ry = pcyy + size / 2 - 3;
					boolean hr = in(mx, my, rx - 7, ry - 7, 14, 14);
					Gfx.circle(c, rx, ry, 7, 0xE6101116);
					Gfx.tex(c, Theme.iconPublic("close"), rx - 4, ry - 4, 8, 8, 64, 64, hr ? 0xFFF87171 : 0xFFB8BCCB);
					reg.region(rx - 7, ry - 7, 14, 14, () -> {
						resetPicture(p);
						note = Lang.tr("Original picture back.", "Imagem original de volta.");
					});
				}
			}
		}
		int rows = (list.size() + cols - 1) / cols;
		if (list.isEmpty()) {
			Gfx.textC(c, cat == -1 ? "Click the star on a voice to add it here" : "No voice found", x + w / 2, y + 20, Theme.DIM);
			rows = 1;
		}
		y += rows * ROW_H + 4;

		// ---------------------------------------------------------- notes + pictures folder
		String status = m.where.idx() != 0 && !SystemVoice.status.isEmpty() ? SystemVoice.status : PersonaMonitor.status;
		if (!status.isEmpty()) {
			Gfx.textClip(c, status, x, y, w, SystemVoice.on() || PersonaMonitor.on() ? 0xFF4ADE80 : 0xFFF87171);
			y += 12;
		}
		if (!note.isEmpty()) {
			Gfx.textClip(c, note, x, y, w, Theme.GREEN);
			y += 12;
		}
		Gfx.textClip(c, Lang.tr("Pencil on a voice = your own photo. Or drag a photo onto the window to use it for the chosen voice.",
				"Lápis numa voz = a tua própria foto. Ou arrasta uma foto para a janela para a voz escolhida."), x, y, w - 120, Theme.DIM);
		String fl = Lang.tr("Pictures folder", "Pasta das imagens");
		int fbw = Gfx.tw(fl) + 30;
		boolean fh = in(mx, my, x + w - fbw, y - 5, fbw, 18);
		Gfx.rrect(c, x + w - fbw, y - 5, fbw, 18, 9, fh ? Theme.CARD_HOVER : Theme.CARD);
		Gfx.tex(c, Theme.iconPublic("folder"), x + w - fbw + 8, y - 1, 10, 10, 64, 64, Theme.DIM);
		Gfx.text(c, fl, x + w - fbw + 22, y, Theme.TEXT);
		reg.region(x + w - fbw, y - 5, fbw, 18, () -> {
			try {
				Files.createDirectories(picturesDir());
				net.minecraft.util.Util.getPlatform().openFile(picturesDir().getParent().toFile());
			} catch (java.io.IOException e) {
				System.err.println("[Sleep] personas folder: " + e);
			}
		});
		y += 18;
		return y - top;
	}

	/** A switch with its label. Returns the x after it. */
	private int toggle(GuiGraphicsExtractor c, int mx, int my, int x, int y, String label, boolean on, int accent, Runnable click, Option.Custom.Reg reg) {
		int w = 26, h = 14, cy = y + 7;
		Gfx.rrect(c, x, y, w, h, 7, on ? accent : 0xFF3A3D49);
		Gfx.circle(c, on ? x + w - 7 : x + 7, cy, 5, 0xFFFFFFFF);
		Gfx.text(c, label, x + w + 6, cy - 4, on ? Theme.TEXT : Theme.DIM);
		int tw = w + 6 + Gfx.tw(Lang.t(label));
		reg.region(x, y - 2, tw, h + 4, click);
		return x + tw;
	}
}
