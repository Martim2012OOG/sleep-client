package dev.sleepclient.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.sleepclient.account.Accounts;
import dev.sleepclient.account.MicrosoftAuth;
import dev.sleepclient.social.RemoteTextures;
import dev.sleepclient.social.Social;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * In-game skin changer (Locker > Inventory > Skins).
 * - Your skins are saved in config/plegumo-skins (add them by nick, by URL or from a PNG file).
 * - Equip: you see it on yourself at once (also in third person and the menus).
 * - Microsoft account: the skin is also sent to Mojang, so everybody sees it the next time you join a server.
 * - Offline account: the skin is sent to Solar, so other Plegumo players see it.
 */
public final class SkinChanger {

	public static final class Skin {
		public final String id;
		public String name;
		public boolean slim;
		final String file;
		volatile RemoteTextures.Tex tex;

		Skin(String id, String name, boolean slim, String file) {
			this.id = id;
			this.name = name;
			this.slim = slim;
			this.file = file;
		}

		/** Texture for previews (null until loaded). */
		public RemoteTextures.Tex tex() {
			if (tex == null) {
				loadTex(this);
			}
			return tex;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final List<Skin> SKINS = new ArrayList<>();
	/** account uuid -> skin id equipped locally. */
	private static final Map<String, String> ACTIVE = new HashMap<>();
	private static boolean loaded;

	public static volatile String message;
	public static volatile boolean messageError, busy;

	private SkinChanger() {
	}

	private static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("plegumo-skins");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path idx = dir().resolve("index.json");
			if (Files.exists(idx)) {
				JsonObject o = JsonParser.parseString(Files.readString(idx)).getAsJsonObject();
				for (JsonElement e : o.getAsJsonArray("skins")) {
					JsonObject s = e.getAsJsonObject();
					String file = s.get("file").getAsString();
					if (Files.exists(dir().resolve(file))) {
						SKINS.add(new Skin(s.get("id").getAsString(), s.get("name").getAsString(), s.has("slim") && s.get("slim").getAsBoolean(), file));
					}
				}
				if (o.has("active")) {
					JsonObject a = o.getAsJsonObject("active");
					for (String k : a.keySet()) {
						ACTIVE.put(k, a.get(k).getAsString());
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Sleep] could not read skins: " + e);
		}
	}

	private static synchronized void save() {
		try {
			Files.createDirectories(dir());
			JsonObject o = new JsonObject();
			JsonArray a = new JsonArray();
			for (Skin s : SKINS) {
				JsonObject j = new JsonObject();
				j.addProperty("id", s.id);
				j.addProperty("name", s.name);
				j.addProperty("slim", s.slim);
				j.addProperty("file", s.file);
				a.add(j);
			}
			o.add("skins", a);
			JsonObject act = new JsonObject();
			ACTIVE.forEach(act::addProperty);
			o.add("active", act);
			Files.writeString(dir().resolve("index.json"), GSON.toJson(o));
		} catch (Exception e) {
			System.err.println("[Sleep] could not save skins: " + e);
		}
	}

	public static List<Skin> all() {
		load();
		return Collections.unmodifiableList(new ArrayList<>(SKINS));
	}

	private static String me() {
		UUID id = Minecraft.getInstance().getUser().getProfileId();
		return id == null ? Minecraft.getInstance().getUser().getName() : id.toString();
	}

	/** The skin equipped on the current account, or null (= the account's own skin). */
	public static Skin active() {
		load();
		String id = ACTIVE.get(me());
		if (id == null) {
			return null;
		}
		for (Skin s : SKINS) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return null;
	}

	/** Your own skin for the 3D previews: the equipped one, else Solar's, else your nick's. */
	public static RemoteTextures.Tex myPreview() {
		Skin a = active();
		if (a != null && a.tex() != null) {
			return a.tex;
		}
		Minecraft mc = Minecraft.getInstance();
		// FIX: primeiro a skin REAL da tua conta (a do jogo). Antes ia buscar ao site / espelhos pelo nick, que davam Steve ou nada
		RemoteTextures.Tex real = ownGameSkin();
		if (real != null) {
			return real;
		}
		UUID id = mc.getUser().getProfileId();
		String url = id == null ? null : Social.skinUrl(id);
		if (url != null) {
			RemoteTextures.Tex site = RemoteTextures.get(url);
			if (site != null) {
				return site;
			}
		}
		String nick = mc.getUser().getName();
		return RemoteTextures.getFirst("name:" + nick.toLowerCase(java.util.Locale.ROOT), java.util.List.of(
				"https://mc-heads.net/skin/" + nick, "https://minotar.net/skin/" + nick));
	}


	private static volatile RemoteTextures.Tex ownSkinTex;
	/** Whose skin the cached address / picture below belong to. */
	private static volatile UUID ownSkinFor;
	/** The address of your account's skin (from Mojang's session server) and whether it has slim arms. Kept on disk too. */
	private static volatile String ownSkinUrl;
	private static volatile boolean ownSkinSlim;
	private static volatile long ownSkinTriedAt;
	private static boolean ownSkinLoaded;

	private static Path ownSkinFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("own-skin.txt");
	}

	private static synchronized void loadOwnSkinFile() {
		if (ownSkinLoaded) {
			return;
		}
		ownSkinLoaded = true;
		try {
			Path f = ownSkinFile();
			if (Files.exists(f)) {
				List<String> l = Files.readAllLines(f);
				UUID me = Minecraft.getInstance().getUser().getProfileId();
				// first line = whose skin it is, so a switched account never shows the old one
				if (l.size() >= 3 && me != null && l.get(0).equals(me.toString()) && l.get(1).startsWith("http")) {
					ownSkinUrl = l.get(1);
					ownSkinSlim = "slim".equals(l.get(2));
				}
			}
		} catch (Exception ignored) {
			// asked again below
		}
	}

	/** Asks Mojang's session server for the skin address of this account (background; at most once a minute). */
	private static void fetchOwnSkinUrl(UUID uuid) {
		long now = System.currentTimeMillis();
		if (now - ownSkinTriedAt < 60_000L) {
			return;
		}
		ownSkinTriedAt = now;
		Social.IO.execute(() -> {
			try {
				byte[] body = fetch("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
				JsonObject o = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
				for (JsonElement e : o.getAsJsonArray("properties")) {
					JsonObject pr = e.getAsJsonObject();
					if (!"textures".equals(pr.get("name").getAsString())) {
						continue;
					}
					JsonObject tx = JsonParser.parseString(new String(Base64.getDecoder().decode(pr.get("value").getAsString()), StandardCharsets.UTF_8))
							.getAsJsonObject().getAsJsonObject("textures");
					if (tx == null || !tx.has("SKIN")) {
						return; // this account has no own skin: Steve / Alex is right
					}
					JsonObject skin = tx.getAsJsonObject("SKIN");
					boolean slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
					ownSkinUrl = skin.get("url").getAsString();
					ownSkinSlim = slim;
					Path f = ownSkinFile();
					Files.createDirectories(f.getParent());
					Files.write(f, List.of(uuid.toString(), ownSkinUrl, slim ? "slim" : "wide"));
					return;
				}
			} catch (Throwable e) {
				System.err.println("[Sleep] could not load your account skin: " + e);
			}
		});
	}

	/**
	 * A skin da tua conta tal como o jogo a tem: em jogo vem do jogador; no menu principal (sem mundo) vai-se buscar o endereco da
	 * skin ao servidor de sessoes da Mojang e descarrega-se de la (guardado no disco para a proxima vez). Devolve null enquanto ainda
	 * nao se sabe (o menu usa entao a skin do site / espelhos, nunca uma skin por defeito).
	 */
	private static RemoteTextures.Tex ownGameSkin() {
		Minecraft mc = Minecraft.getInstance();
		try {
			if (mc.player != null) {
				Identifier path = mc.player.getSkin().body().texturePath();
				RemoteTextures.Tex cur = ownSkinTex;
				if (cur == null || !cur.id().equals(path)) {
					String p = path.getPath();
					Identifier asset = Identifier.fromNamespaceAndPath(path.getNamespace(), p.startsWith("textures/") ? p.substring(9).replaceAll("\\.png$", "") : p);
					cur = new RemoteTextures.Tex(path, asset, 64, 64);
					ownSkinTex = cur;
				}
				return cur;
			}
			UUID uuid = mc.getUser().getProfileId();
			if (uuid != null && !uuid.equals(ownSkinFor)) {
				// another account was chosen (an offline one, say): forget the skin address / picture of the previous one
				ownSkinFor = uuid;
				ownSkinUrl = null;
				ownSkinSlim = false;
				ownSkinTex = null;
				ownSkinTriedAt = 0L;
				ownSkinLoaded = false;
			}
			loadOwnSkinFile();
			if (ownSkinUrl == null && uuid != null) {
				fetchOwnSkinUrl(uuid);
			}
			return ownSkinUrl == null ? null : RemoteTextures.get(ownSkinUrl);
		} catch (Throwable t) {
			return null; // qualquer falha: segue o caminho antigo (site / espelhos)
		}
	}

	// ------------------------------------------------------------------ adding
	/** Adds a skin from a nick or an http(s) URL (in the background). */
	public static void add(String input) {
		String in = input == null ? "" : input.trim();
		if (in.isEmpty() || busy) {
			return;
		}
		busy = true;
		say("Adding " + in + "...", false);
		Social.IO.execute(() -> {
			try {
				if (in.startsWith("http://") || in.startsWith("https://")) {
					byte[] png = fetch(in);
					String name = in.replaceAll("[?#].*$", "").replaceAll("^.*/", "").replaceAll("(?i)\\.png$", "");
					store(name.isEmpty() ? "Skin" : name, png, null);
				} else if (Accounts.valid(in)) {
					fromNick(in);
				} else {
					say("Type a nick (3-16 letters) or a link to a PNG", true);
				}
			} catch (Exception e) {
				say(e.getMessage() == null ? "Could not add the skin" : e.getMessage(), true);
			} finally {
				busy = false;
			}
		});
	}

	public static void addFile(Path file) {
		addFile(file, false);
	}

	/**
	 * Adds a PNG from disk; with equip = true it is also put on at once ("Upload skin"): Microsoft account -> sent to
	 * Mojang only (nothing is kept on Sleep's servers); any other account -> sent to the Sleep site, which keeps only a
	 * link to the picture (imgbb) so every Sleep player sees it.
	 */
	public static void addFile(Path file, boolean equip) {
		Social.IO.execute(() -> {
			try {
				Skin s = store(file.getFileName().toString().replaceAll("(?i)\\.png$", ""), Files.readAllBytes(file), null);
				if (equip) {
					equip(s);
				}
			} catch (Exception e) {
				say(e.getMessage() == null ? "Could not read the file" : e.getMessage(), true);
			}
		});
	}

	private static void fromNick(String nick) throws Exception {
		String id = null;
		try {
			JsonObject p = JsonParser.parseString(text("https://api.mojang.com/users/profiles/minecraft/" + nick)).getAsJsonObject();
			id = p.get("id").getAsString();
			nick = p.get("name").getAsString();
		} catch (Exception e) {
			// not a premium nick: try the public skin mirror below
		}
		if (id != null) {
			JsonObject prof = JsonParser.parseString(text("https://sessionserver.mojang.com/session/minecraft/profile/" + id)).getAsJsonObject();
			for (JsonElement e : prof.getAsJsonArray("properties")) {
				JsonObject pr = e.getAsJsonObject();
				if (!pr.get("name").getAsString().equals("textures")) {
					continue;
				}
				JsonObject tex = JsonParser.parseString(new String(Base64.getDecoder().decode(pr.get("value").getAsString()), StandardCharsets.UTF_8))
						.getAsJsonObject().getAsJsonObject("textures");
				if (tex != null && tex.has("SKIN")) {
					JsonObject skin = tex.getAsJsonObject("SKIN");
					boolean slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
					store(nick, fetch(skin.get("url").getAsString().replace("http://", "https://")), slim);
					return;
				}
			}
			throw new IllegalStateException(nick + " uses the default skin");
		}
		store(nick, fetch("https://mc-heads.net/skin/" + nick), null);
	}

	private static String text(String url) throws Exception {
		HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		if (r.statusCode() != 200) {
			throw new IllegalStateException("Player not found");
		}
		return r.body();
	}

	private static byte[] fetch(String url) throws Exception {
		HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
				HttpResponse.BodyHandlers.ofByteArray());
		if (r.statusCode() != 200) {
			throw new IllegalStateException("Download failed (" + r.statusCode() + ")");
		}
		return r.body();
	}

	/** Checks the PNG, guesses slim arms when unknown, saves it and selects nothing. */
	private static Skin store(String name, byte[] png, Boolean slim) throws Exception {
		if (png.length < 24 || (png[0] & 0xFF) != 0x89 || png[1] != 'P') {
			throw new IllegalStateException("That isn't a PNG image");
		}
		boolean isSlim;
		try (NativeImage img = NativeImage.read(new ByteArrayInputStream(png))) {
			int w = img.getWidth(), h = img.getHeight();
			if (w != 64 || (h != 64 && h != 32)) {
				throw new IllegalStateException("A skin must be 64x64 or 64x32 (this is " + w + "x" + h + ")");
			}
			// slim (Alex) skins leave this column of the right arm empty
			isSlim = slim != null ? slim : h == 64 && ((img.getPixel(55, 20) >>> 24) == 0);
		}
		load();
		String id = Long.toString(System.currentTimeMillis(), 36);
		String file = id + ".png";
		Files.createDirectories(dir());
		Files.write(dir().resolve(file), png);
		String clean = name.replaceAll("[^A-Za-z0-9_ .-]", "");
		Skin s = new Skin(id, clean.isEmpty() ? "Skin" : clean.length() > 24 ? clean.substring(0, 24) : clean, isSlim, file);
		synchronized (SkinChanger.class) {
			SKINS.add(0, s);
		}
		save();
		say("Added " + s.name, false);
		return s;
	}

	public static void remove(Skin s) {
		load();
		SKINS.remove(s);
		ACTIVE.values().removeIf(v -> v.equals(s.id));
		try {
			Files.deleteIfExists(dir().resolve(s.file));
		} catch (Exception ignored) {
		}
		save();
	}

	public static void setSlim(Skin s, boolean slim) {
		s.slim = slim;
		save();
	}

	// ------------------------------------------------------------------ equipping
	/** Equips a skin: at once for you, and sent to Mojang (Microsoft) or Solar (offline) for everybody else. */
	public static void equip(Skin s) {
		load();
		ACTIVE.put(me(), s.id);
		save();
		say("Equipped " + s.name, false);
		byte[] png;
		try {
			png = Files.readAllBytes(dir().resolve(s.file));
		} catch (Exception e) {
			say("Could not read the skin file", true);
			return;
		}
		String token = Accounts.currentMicrosoftToken();
		Social.IO.execute(() -> {
			if (token != null) {
				say("Equipped " + s.name + " - sending it to Mojang...", false);
				String err = MicrosoftAuth.uploadSkin(token, png, s.slim);
				if (err == null) {
					say("Equipped " + s.name + ". Others see it when you join a server.", false);
					return;
				}
				// Mojang failed: still share it with Plegumo players
				String solar = Social.uploadSkinAndUse(png, s.name);
				say(err + (solar == null ? " (shared with Sleep players)" : ""), true);
			} else {
				String solar = Social.uploadSkinAndUse(png, s.name);
				say(solar == null ? "Equipped " + s.name + ". Sleep players see it." : "Equipped for you only: " + solar, solar != null);
			}
		});
	}

	/** Back to the account's own skin (only for you; on Mojang the last uploaded skin stays). */
	public static void unequip() {
		load();
		ACTIVE.remove(me());
		save();
		say("Using your account's skin", false);
		if (Accounts.currentMicrosoftToken() == null && Social.activeSkinId != null) {
			Social.setActive(Social.activeCapeId, null); // also stop sharing the cloud skin with other Sleep players
		}
	}

	private static void say(String m, boolean err) {
		message = m;
		messageError = err;
	}

	// ------------------------------------------------------------------ in game
	/** Called by PlayerSkinMixin: your equipped skin texture (and its arm model) or null. */
	public static RemoteTextures.Tex selfTexture(UUID player) {
		Minecraft mc = Minecraft.getInstance();
		UUID me = mc.getUser().getProfileId();
		if (me == null || !me.equals(player)) {
			return null;
		}
		Skin s = active();
		return s == null ? null : s.tex();
	}

	public static boolean selfSlim() {
		Skin s = active();
		if (s != null) {
			return s.slim;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				PlayerModelType slim = modelType(true);
				return slim != null && mc.player.getSkin().model() == slim;
			}
			loadOwnSkinFile();
			return ownSkinSlim;
		} catch (Throwable t) {
			return false;
		}
	}

	private static PlayerModelType slimType, wideType;

	/** The arm model enum value (found by its "slim" / "default" text, so it doesn't depend on mapping names). */
	public static PlayerModelType modelType(boolean slim) {
		if (slimType == null || wideType == null) {
			for (PlayerModelType t : PlayerModelType.values()) {
				boolean isSlim = false;
				for (Field f : PlayerModelType.class.getDeclaredFields()) {
					if (f.getType() == String.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
						try {
							f.setAccessible(true);
							if ("slim".equals(f.get(t))) {
								isSlim = true;
							}
						} catch (Exception ignored) {
						}
					}
				}
				if (isSlim) {
					slimType = t;
				} else if (wideType == null) {
					wideType = t;
				}
			}
		}
		return slim ? slimType : wideType;
	}

	private static void loadTex(Skin s) {
		if (s.tex != null) {
			return;
		}
		try {
			byte[] png = Files.readAllBytes(dir().resolve(s.file));
			NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
			Identifier asset = Identifier.fromNamespaceAndPath("sleep_client", "skins/" + s.id);
			Identifier id = Identifier.fromNamespaceAndPath("sleep_client", "textures/skins/" + s.id + ".png");
			int w = img.getWidth(), h = img.getHeight();
			DynamicTexture tex = new DynamicTexture(() -> "plegumo_skin_" + s.id, img);
			tex.upload();
			Minecraft.getInstance().getTextureManager().register(id, tex);
			s.tex = new RemoteTextures.Tex(id, asset, w, h);
		} catch (Exception e) {
			System.err.println("[Sleep] could not load skin " + s.name + ": " + e);
		}
	}
}
