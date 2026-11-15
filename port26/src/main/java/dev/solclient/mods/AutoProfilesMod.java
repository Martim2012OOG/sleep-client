package dev.solclient.mods;

import dev.solclient.Lang;
import dev.solclient.Profiles;
import dev.solclient.option.Option;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Auto Profiles: picks a mod profile by itself when you join a server ("hypixel.net = PvP", "play.myserver.pt = Build",
 * "singleplayer = Survival"). The rules live in their own file so loading a profile never loses them.
 */
public class AutoProfilesMod extends Mod {

	public static AutoProfilesMod instance;
	private static final int N = 6;
	private final Option.Text[] rules = new Option.Text[N];
	private final Option.Bool tell = bool("Tell Me When It Switches", true);
	private static final String[] saved = new String[N];
	private static boolean loaded, hooked, restoring;

	public AutoProfilesMod() {
		super("Auto Profiles", "auto_profiles", "Switches your mod profile by itself depending on the server you join.", Category.UTILITY);
		instance = this;
		header("Rules (server = profile)");
		for (int i = 0; i < N; i++) {
			rules[i] = text("Rule " + (i + 1), "", 60, i == 0 ? "hypixel.net = PvP" : i == 1 ? "singleplayer = Survival" : "server = profile");
		}
		action("Your profiles", "Show", () -> {
		}, () -> String.join(", ", Profiles.list()));
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-auto-profiles.txt");
	}

	private void loadFile() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (Files.exists(file())) {
				String[] lines = Files.readString(file(), StandardCharsets.UTF_8).split("\n");
				for (int i = 0; i < N && i < lines.length; i++) {
					saved[i] = lines[i].trim();
				}
			}
		} catch (Exception ignored) {
		}
		for (int i = 0; i < N; i++) {
			if (saved[i] == null) {
				saved[i] = rules[i].get();
			}
		}
	}

	private void saveFile() {
		try {
			Files.writeString(file(), String.join("\n", saved), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	@Override
	public void init() {
		super.init();
		loadFile();
		restore();
		if (hooked) {
			return;
		}
		hooked = true;
		ClientPlayConnectionEvents.JOIN.register((h, s, c) -> c.execute(() -> {
			if (instance != null && instance.enabled) {
				instance.apply();
			}
		}));
	}

	/** Puts the rules back in the options (after a profile was loaded). */
	private void restore() {
		restoring = true;
		for (int i = 0; i < N; i++) {
			if (saved[i] != null) {
				rules[i].set(saved[i]);
			}
		}
		restoring = false;
	}

	@Override
	public void tick() {
		loadFile();
		boolean changed = false;
		for (int i = 0; i < N; i++) {
			String v = rules[i].get().trim();
			if (!v.equals(saved[i])) {
				saved[i] = v;
				changed = true;
			}
		}
		if (changed && !restoring) {
			saveFile();
		}
	}

	private void apply() {
		loadFile();
		String addr = mc.isLocalServer() ? "singleplayer" : mc.getCurrentServer() == null ? "" : mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
		for (String r : saved) {
			if (r == null || !r.contains("=")) {
				continue;
			}
			String server = r.substring(0, r.indexOf('=')).trim().toLowerCase(Locale.ROOT);
			String profile = r.substring(r.indexOf('=') + 1).trim();
			if (server.isEmpty() || profile.isEmpty() || !addr.contains(server)) {
				continue;
			}
			if (profile.equalsIgnoreCase(Profiles.active()) || !Profiles.list().contains(profile)) {
				return;
			}
			Profiles.load(profile);
			// the profile may have other rules / this mod off: keep ours
			setEnabledQuiet();
			restore();
			if (tell.on()) {
				dev.solclient.social.Social.say(Lang.tr("Profile: ", "Perfil: ") + profile, false);
			}
			return;
		}
	}

	private void setEnabledQuiet() {
		if (!enabled) {
			enabled = true;
		}
	}
}
