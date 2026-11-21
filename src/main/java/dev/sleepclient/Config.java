package dev.sleepclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.sleepclient.mods.HudMod;
import dev.sleepclient.mods.Mod;
import dev.sleepclient.option.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class Config {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Config() {
	}

	private static Path path() {
		Path dir = FabricLoader.getInstance().getConfigDir();
		Path now = dir.resolve("sleep-client.json");
		if (!Files.exists(now)) {
			// the client used to be called "Sol Client": take the old settings along (the old file is left untouched)
			Path old = dir.resolve("sol-client.json");
			if (Files.exists(old)) {
				try {
					Files.copy(old, now);
				} catch (java.io.IOException ignored) {
					// no old settings: start fresh
				}
			}
		}
		return now;
	}

	public static void load(List<Mod> mods) {
		loadFrom(path(), mods);
	}

	/** Reads every mod's state from a file (main config or a profile). */
	public static void loadFrom(Path p, List<Mod> mods) {
		if (!Files.exists(p)) {
			return;
		}
		try {
			JsonObject root = GSON.fromJson(Files.readString(p), JsonObject.class);
			if (root == null) {
				return;
			}
			for (Mod mod : mods) {
				if (!root.has(mod.id)) {
					continue;
				}
				JsonObject o = root.getAsJsonObject(mod.id);
				if (o.has("enabled") && !mod.locked) {
					mod.enabled = o.get("enabled").getAsBoolean();
				}
				if (o.has("favorite")) {
					mod.favorite = o.get("favorite").getAsBoolean();
				}
				if (o.has("options")) {
					JsonObject opts = o.getAsJsonObject("options");
					for (Option<?> opt : mod.options) {
						// options renamed from "Plegumo ..." / "Nimbo ..." to "Sleep ..." keep their saved value
						// o nome Kubo passou a Sleep: as definições guardadas com o nome antigo continuam a valer
						String k = opts.has(opt.key) ? opt.key
								: opts.has(opt.key.replace("sleep", "kubo")) ? opt.key.replace("sleep", "kubo")
								: opts.has(opt.key.replace("sleep", "nimbo")) ? opt.key.replace("sleep", "nimbo") : opt.key.replace("sleep", "plegumo");
						if (opts.has(k)) {
							try {
								opt.load(opts.get(k));
							} catch (RuntimeException ignored) {
								// keep default
							}
						}
					}
				}
				if (mod instanceof HudMod h && o.has("x") && o.has("y")) {
					h.hx = o.get("x").getAsFloat();
					h.hy = o.get("y").getAsFloat();
				}
			}
			// the normal Mojang loading screen is back when resource packs reload (hiding it broke the HUD fonts)
			if (!root.has("reloadScreenBack") && SleepClient.settings != null) {
				SleepClient.settings.hideReloadScreen.set(false);
			}
			// Christmas season: a Halloween picked by hand becomes Christmas once
			if (!root.has("xmasSeason") && SleepClient.settings != null && SleepClient.settings.seasonal.idx() == 2) {
				SleepClient.settings.seasonal.set(3);
			}
			// the notification size default went from 80% to 55%: move configs that still have the old default
			if (!root.has("notifySize55") && SleepClient.settings != null && Math.round(SleepClient.settings.notifySize.f()) == 80) {
				SleepClient.settings.notifySize.set(55F);
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] Could not read config: " + e);
		}
	}

	public static void save() {
		saveTo(path());
	}

	/** Writes every mod's state to a file (main config or a profile). */
	public static void saveTo(Path target) {
		JsonObject root = new JsonObject();
		for (Mod mod : SleepClient.MODS) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", mod.enabled);
			o.addProperty("favorite", mod.favorite);
			JsonObject opts = new JsonObject();
			for (Option<?> opt : mod.options) {
				if (opt instanceof Option.Header || opt instanceof Option.Action) {
					continue;
				}
				if (opt instanceof Option.Text t && t.secret && !target.equals(path())) {
					continue; // API keys never go into exported profiles
				}
				JsonElement e = opt.save();
				opts.add(opt.key, e);
			}
			o.add("options", opts);
			if (mod instanceof HudMod h) {
				o.addProperty("x", h.hx);
				o.addProperty("y", h.hy);
			}
			root.add(mod.id, o);
		}
		root.addProperty("notifySize55", true);
		root.addProperty("reloadScreenBack", true);
		root.addProperty("xmasSeason", true);
		try {
			Files.createDirectories(target.getParent());
			Files.writeString(target, GSON.toJson(root));
		} catch (IOException e) {
			System.err.println("[Sleep] Could not save config: " + e);
		}
	}
}
