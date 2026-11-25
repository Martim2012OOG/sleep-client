package dev.solclient.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * In-game account switcher: the account the launcher started with, Microsoft accounts you log in to here
 * (device code: microsoft.com/link, no password in the game) and offline / cracked accounts by nick.
 * Offline accounts only work on servers in offline mode.
 */
public final class Accounts {

	public static final String LAUNCHER = "launcher", MICROSOFT = "microsoft", OFFLINE = "offline";

	public static final class Account {
		public final String type;
		public String name, uuid;
		String refresh;
		transient String token;
		transient long tokenAt;

		Account(String type, String name, String uuid, String refresh) {
			this.type = type;
			this.name = name;
			this.uuid = uuid;
			this.refresh = refresh;
		}

		/** "Microsoft" or "Offline" (what the badge says). */
		public String label() {
			if (type.equals(LAUNCHER)) {
				return Accounts.type(launcher);
			}
			return type.equals(MICROSOFT) ? "Microsoft" : "Offline";
		}

		public UUID uuidOrNull() {
			try {
				String u = uuid.replace("-", "");
				return new UUID(Long.parseUnsignedLong(u.substring(0, 16), 16), Long.parseUnsignedLong(u.substring(16), 16));
			} catch (RuntimeException e) {
				return null;
			}
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static User launcher;
	private static Account launcherAcc;
	private static final List<Account> SAVED = new ArrayList<>();
	private static boolean loaded;

	/** Switch in progress / last result (shown by the screens). */
	public static volatile String message;
	public static volatile boolean messageError;
	public static volatile boolean busy;

	private Accounts() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("plegumo-accounts.json");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		launcher = Minecraft.getInstance().getUser();
		launcherAcc = new Account(LAUNCHER, launcher.getName(), launcher.getProfileId() == null ? "" : launcher.getProfileId().toString(), null);
		try {
			Path p = file();
			if (Files.exists(p)) {
				JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
				if (root.has("accounts")) {
					for (JsonElement e : root.getAsJsonArray("accounts")) {
						JsonObject o = e.getAsJsonObject();
						String type = o.get("type").getAsString();
						String name = o.get("name").getAsString();
						String uuid = o.has("uuid") ? o.get("uuid").getAsString() : "";
						String refresh = o.has("refresh") && !o.get("refresh").isJsonNull() ? o.get("refresh").getAsString() : null;
						if ((type.equals(MICROSOFT) && refresh != null) || (type.equals(OFFLINE) && valid(name))) {
							SAVED.add(new Account(type, name, uuid, refresh));
						}
					}
				}
				if (root.has("offline")) { // old format
					for (JsonElement e : root.getAsJsonArray("offline")) {
						String n = e.getAsString();
						if (valid(n) && find(OFFLINE, n) == null) {
							SAVED.add(new Account(OFFLINE, n, offlineUuid(n).toString(), null));
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Caos] Could not read accounts: " + e);
		}
	}

	private static void save() {
		JsonObject o = new JsonObject();
		JsonArray a = new JsonArray();
		for (Account acc : SAVED) {
			JsonObject j = new JsonObject();
			j.addProperty("type", acc.type);
			j.addProperty("name", acc.name);
			j.addProperty("uuid", acc.uuid);
			if (acc.refresh != null) {
				j.addProperty("refresh", acc.refresh);
			}
			a.add(j);
		}
		o.addProperty("_note", "Microsoft refresh tokens: keep this file private.");
		o.add("accounts", a);
		try {
			Files.writeString(file(), GSON.toJson(o));
		} catch (Exception e) {
			System.err.println("[Caos] Could not save accounts: " + e);
		}
	}

	private static Account find(String type, String name) {
		for (Account a : SAVED) {
			if (a.type.equals(type) && a.name.equalsIgnoreCase(name)) {
				return a;
			}
		}
		return null;
	}

	/** Call once at start-up so the launcher's account is remembered before any switch. */
	public static void init() {
		load();
	}

	public static boolean valid(String nick) {
		return nick != null && nick.matches("[A-Za-z0-9_]{3,16}");
	}

	private static UUID offlineUuid(String nick) {
		return UUID.nameUUIDFromBytes(("OfflinePlayer:" + nick).getBytes(StandardCharsets.UTF_8));
	}

	// ------------------------------------------------------------------ reading
	public static String current() {
		return Minecraft.getInstance().getUser().getName();
	}

	/** "Microsoft" when the session has a real token, else "Offline". */
	public static String type(User s) {
		String t = s.getAccessToken();
		boolean ms = t != null && t.length() > 60 && t.startsWith("ey");
		return ms ? "Microsoft" : "Offline";
	}

	public static String currentType() {
		return type(Minecraft.getInstance().getUser());
	}

	/** The launcher account first, then the saved ones. */
	public static List<Account> all() {
		load();
		List<Account> out = new ArrayList<>();
		out.add(launcherAcc);
		out.addAll(SAVED);
		return Collections.unmodifiableList(out);
	}

	public static boolean isCurrent(Account a) {
		load();
		User s = Minecraft.getInstance().getUser();
		if (a.type.equals(LAUNCHER)) {
			return s == launcher;
		}
		if (s == launcher) {
			return false;
		}
		UUID id = a.uuidOrNull();
		return s.getName().equalsIgnoreCase(a.name) && (id == null || id.equals(s.getProfileId()));
	}

	/** Access token of the current session when it is a real Microsoft one (for the skin changer), else null. */
	public static String currentMicrosoftToken() {
		User s = Minecraft.getInstance().getUser();
		return type(s).equals("Microsoft") ? s.getAccessToken() : null;
	}

	// ------------------------------------------------------------------ changing
	public static boolean addOffline(String nick) {
		load();
		if (!valid(nick)) {
			return false;
		}
		if (find(OFFLINE, nick) == null) {
			SAVED.add(new Account(OFFLINE, nick, offlineUuid(nick).toString(), null));
			save();
		}
		return true;
	}

	public static void remove(Account a) {
		load();
		if (a.type.equals(LAUNCHER)) {
			return;
		}
		if (isCurrent(a)) {
			set(launcher);
		}
		SAVED.remove(a);
		save();
	}

	/** Puts an account at another position in the list (drag to reorder). */
	public static void move(Account a, int index) {
		load();
		if (SAVED.remove(a)) {
			SAVED.add(Math.max(0, Math.min(SAVED.size(), index)), a);
			save();
		}
	}

	/** Switches account. Microsoft accounts refresh their token in the background first. */
	public static void use(Account a) {
		load();
		if (busy) {
			return;
		}
		switch (a.type) {
			case LAUNCHER -> {
				set(launcher);
				say("Using " + a.name, false);
			}
			case OFFLINE -> {
				set(new User(a.name, offlineUuid(a.name), "0", Optional.empty(), Optional.empty()));
				say("Using " + a.name + " (offline)", false);
			}
			default -> {
				if (a.token != null && System.currentTimeMillis() - a.tokenAt < 20 * 3600_000L) {
					useMicrosoft(a);
					return;
				}
				busy = true;
				say("Logging in as " + a.name + "...", false);
				Thread t = new Thread(() -> {
					try {
						MicrosoftAuth.Profile p = MicrosoftAuth.refresh(a.refresh);
						Minecraft.getInstance().execute(() -> {
							a.name = p.name();
							a.uuid = p.uuid();
							a.refresh = p.refreshToken();
							a.token = p.mcToken();
							a.tokenAt = System.currentTimeMillis();
							save();
							useMicrosoft(a);
							busy = false;
						});
					} catch (Exception e) {
						say(e.getMessage() == null ? e.toString() : e.getMessage(), true);
						busy = false;
					}
				}, "Caos account switch");
				t.setDaemon(true);
				t.start();
			}
		}
	}

	private static void useMicrosoft(Account a) {
		UUID id = a.uuidOrNull();
		set(new User(a.name, id, a.token, Optional.empty(), Optional.empty()));
		say("Using " + a.name, false);
	}

	/** Called when a device-code login finished: saves the account (or updates it) and switches to it. */
	public static void addMicrosoft(MicrosoftAuth.Profile p) {
		load();
		Account a = null;
		for (Account x : SAVED) {
			if (x.type.equals(MICROSOFT) && x.uuid.replace("-", "").equalsIgnoreCase(p.uuid().replace("-", ""))) {
				a = x;
			}
		}
		if (a == null) {
			a = new Account(MICROSOFT, p.name(), p.uuid(), p.refreshToken());
			SAVED.add(a);
		}
		a.name = p.name();
		a.refresh = p.refreshToken();
		a.token = p.mcToken();
		a.tokenAt = System.currentTimeMillis();
		save();
		useMicrosoft(a);
	}

	private static void say(String m, boolean error) {
		message = m;
		messageError = error;
	}

	// ------------------------------------------------------------------ old API (kept for other screens)
	public static List<String> offline() {
		List<String> out = new ArrayList<>();
		for (Account a : all()) {
			if (a.type.equals(OFFLINE)) {
				out.add(a.name);
			}
		}
		return out;
	}

	public static boolean useLauncher() {
		load();
		return set(launcher);
	}

	// ------------------------------------------------------------------ session swap
	private static boolean set(User s) {
		Minecraft mc = Minecraft.getInstance();
		try {
			boolean done = false;
			for (Field f : Minecraft.class.getDeclaredFields()) {
				if (f.getType() == User.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
					f.setAccessible(true);
					f.set(mc, s);
					done = true;
				}
			}
			if (!done) {
				return false;
			}
			refreshServices(mc, s);
			dev.solclient.social.Social.accountChanged();
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("[Caos] Could not switch account: " + e);
			say("Could not switch account", true);
		}
		return false;
	}

	/**
	 * Best effort: new UserApiService and chat signing keys for the new token (what In-Game Account Switcher does),
	 * so chat on servers with secure chat keeps working. If anything here fails, the switch still happened.
	 */
	private static void refreshServices(Minecraft mc, User s) {
		try {
			Object auth = null;
			for (Field f : Minecraft.class.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
					continue;
				}
				f.setAccessible(true);
				Object v = f.get(mc);
				if (v == null) {
					continue;
				}
				if (v.getClass().getName().endsWith("YggdrasilAuthenticationService")) {
					auth = v;
				} else if (auth == null && v.getClass().isRecord()) {
					for (Field rf : v.getClass().getDeclaredFields()) {
						rf.setAccessible(true);
						Object rv = rf.get(v);
						if (rv != null && rv.getClass().getName().endsWith("YggdrasilAuthenticationService")) {
							auth = rv;
						}
					}
				}
			}
			Object userApi;
			boolean offline = !type(s).equals("Microsoft");
			Class<?> uas = Class.forName("com.mojang.authlib.minecraft.UserApiService");
			if (offline || auth == null) {
				userApi = uas.getField("OFFLINE").get(null);
			} else {
				Method m = auth.getClass().getMethod("createUserApiService", String.class);
				userApi = m.invoke(auth, s.getAccessToken());
			}
			Field apiField = null, keysField = null;
			for (Field f : Minecraft.class.getDeclaredFields()) {
				if (uas.isAssignableFrom(f.getType())) {
					apiField = f;
				} else if (f.getType() == net.minecraft.client.multiplayer.ProfileKeyPairManager.class) {
					keysField = f;
				}
			}
			if (apiField != null) {
				apiField.setAccessible(true);
				apiField.set(mc, userApi);
			}
			if (keysField != null) {
				keysField.setAccessible(true);
				keysField.set(mc, net.minecraft.client.multiplayer.ProfileKeyPairManager.create((com.mojang.authlib.minecraft.UserApiService) userApi, s, mc.gameDirectory.toPath()));
			}
		} catch (Throwable e) {
			System.err.println("[Caos] account switch: could not refresh chat keys (" + e + ")");
		}
	}
}
