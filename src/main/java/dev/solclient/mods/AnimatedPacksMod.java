package dev.solclient.mods;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.option.Option;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Animated Packs: animated resource packs (animated water / lava / fire, animated blocks and items, animated GUI
 * sprites...) keep working even when another mod tries to switch the animations off.
 *
 * Written from scratch, no third-party code. It does not touch how Minecraft draws anything; it only puts the
 * "turn animations off" switches of other mods back to "animate":
 *  - Sodium: "Animate only visible textures" (the main reason animated packs look frozen or only animate on screen);
 *  - Sodium Extra: the whole Animations page (animations, water, lava, fire, portal, block animations, sculk sensor).
 * The switches are read by those mods every tick, so this works live, with no restart. If the live switch can't be
 * found (a new mod version with other names) the mod's config file is fixed instead, and that needs a restart.
 * When this mod is turned off, the values are put back exactly as they were.
 */
public class AnimatedPacksMod extends Mod {

	public static AnimatedPacksMod instance;

	public final Option.Bool sodium = bool("Unblock Sodium", true);
	public final Option.Bool sodiumExtra = bool("Unblock Sodium Extra", true);
	public final Option.Action check = action("Unblocked animations", "Check now", AnimatedPacksMod::applyNow, () -> status);

	/** What happened in the last pass, shown next to the button. */
	private static volatile String status = "Waiting";
	private static boolean listening;
	private static int ticks;
	/** Original values of the switches we flipped: restored when the mod is turned off. */
	private static final List<Saved> SAVED = new ArrayList<>();
	/** Config files already fixed in this session (no need to write them again). */
	private static final List<String> FILES_DONE = new ArrayList<>();
	private static boolean restartNeeded;

	private record Saved(Object target, Field field, boolean original) {
	}

	public AnimatedPacksMod() {
		super("Animated Packs", "animated_packs", "Animated resource packs keep animating, even if Sodium or another mod blocks them.", Category.VISUAL);
		instance = this;
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	@Override
	public void init() {
		super.init();
		if (!listening) {
			listening = true;
			// own listener: also runs in the menus (Mod.tick() only runs inside a world), and also after other mods
			// finished loading their config (they may load it after Caos starts)
			ClientTickEvents.END_CLIENT_TICK.register(client -> {
				AnimatedPacksMod m = instance;
				if (m == null || !m.enabled) {
					return;
				}
				if (ticks++ % 40 == 0) { // first tick, then every 2 seconds
					applyNow();
				}
			});
		}
	}

	@Override
	protected void onEnable() {
		ticks = 0; // apply on the next tick
	}

	@Override
	protected void onDisable() {
		restore();
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}

	// ------------------------------------------------------------------ work

	/** One pass: flips every blocking switch we know. Safe to call as often as you like. */
	public static synchronized void applyNow() {
		AnimatedPacksMod m = instance;
		if (m == null) {
			return;
		}
		List<String> done = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		FabricLoader fl = FabricLoader.getInstance();
		boolean anyBlocker = false;

		if (m.sodium.on() && fl.isModLoaded("sodium")) {
			anyBlocker = true;
			int n = fixLive(new String[] { "net.caffeinemc.mods.sodium.client.SodiumClientMod", "me.jellysquid.mods.sodium.client.SodiumClientMod" }, false);
			if (n < 0) {
				if (fixFile("sodium-options.json", false)) {
					done.add("Sodium (file, restart)");
				} else {
					problems.add("Sodium");
				}
			} else {
				done.add("Sodium");
			}
		}
		if (m.sodiumExtra.on() && fl.isModLoaded("sodium-extra")) {
			anyBlocker = true;
			int n = fixLive(new String[] { "me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod" }, true);
			if (n < 0) {
				if (fixFile("sodium-extra-options.json", true)) {
					done.add("Sodium Extra (file, restart)");
				} else {
					problems.add("Sodium Extra");
				}
			} else {
				done.add("Sodium Extra");
			}
		}

		if (!anyBlocker) {
			status = "No blocking mods found";
		} else if (!problems.isEmpty()) {
			status = "Could not unblock: " + String.join(", ", problems);
		} else if (restartNeeded) {
			status = "Unblocked: " + String.join(", ", done) + " - restart the game";
		} else {
			status = "Unblocked: " + String.join(", ", done);
		}
	}

	/**
	 * Finds the live options object of a mod (static options() method) and flips the switches in it.
	 * @param extra false = Sodium (turn "animate only visible" OFF), true = Sodium Extra (turn the Animations page ON)
	 * @return how many switches were found, or -1 when the mod's options could not be reached
	 */
	private static int fixLive(String[] classNames, boolean extra) {
		Object options = null;
		for (String cn : classNames) {
			try {
				Class<?> c = Class.forName(cn, false, AnimatedPacksMod.class.getClassLoader());
				Method get = c.getMethod("options");
				options = get.invoke(null);
				if (options != null) {
					break;
				}
			} catch (Throwable ignored) {
			}
		}
		if (options == null) {
			return -1;
		}
		int[] found = { 0 };
		try {
			walk(options, 0, false, extra, found, new IdentityHashMap<>());
		} catch (Throwable t) {
			return -1;
		}
		return found[0] > 0 ? found[0] : -1;
	}

	/** Looks through the fields of an options object (2 levels deep) and flips the matching booleans. */
	private static void walk(Object o, int depth, boolean inAnimationGroup, boolean extra, int[] found, IdentityHashMap<Object, Boolean> seen) throws Exception {
		if (o == null || depth > 3 || seen.put(o, Boolean.TRUE) != null) {
			return;
		}
		for (Field f : o.getClass().getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers())) {
				continue;
			}
			String n = f.getName().toLowerCase();
			Class<?> t = f.getType();
			try {
				f.setAccessible(true);
			} catch (Throwable e) {
				continue;
			}
			if (t == boolean.class) {
				boolean want;
				boolean match;
				if (!extra) {
					match = n.contains("animate") && n.contains("visible"); // animateOnlyVisibleTextures
					want = false;
				} else {
					match = inAnimationGroup; // every switch inside the Animations group
					want = true;
				}
				if (match) {
					found[0]++;
					boolean cur = f.getBoolean(o);
					if (cur != want) {
						remember(o, f, cur);
						f.setBoolean(o, want);
					}
				}
			} else if (!t.isPrimitive() && !t.isEnum() && !t.getName().startsWith("java.") && !t.isArray()) {
				boolean group = inAnimationGroup || (extra && n.contains("animation"));
				walk(f.get(o), depth + 1, group, extra, found, seen);
			}
		}
	}

	private static void remember(Object target, Field f, boolean original) {
		for (Saved s : SAVED) {
			if (s.target == target && s.field.equals(f)) {
				return; // keep the very first value
			}
		}
		SAVED.add(new Saved(target, f, original));
	}

	/** Mod turned off: put every switch back as it was. */
	private static synchronized void restore() {
		for (Saved s : SAVED) {
			try {
				s.field.setBoolean(s.target, s.original);
			} catch (Throwable ignored) {
			}
		}
		SAVED.clear();
		status = "Off";
	}

	// ------------------------------------------------------------------ config file fallback

	/** Fixes the other mod's config file (used only when its live options could not be reached). */
	private static boolean fixFile(String fileName, boolean extra) {
		if (FILES_DONE.contains(fileName)) {
			return true;
		}
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve(fileName);
			if (!Files.isRegularFile(p)) {
				return false;
			}
			JsonElement root = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
			if (!root.isJsonObject()) {
				return false;
			}
			int[] changed = { 0, 0 }; // [0] = switches found, [1] = switches changed
			patchJson(root.getAsJsonObject(), false, extra, changed);
			if (changed[0] == 0) {
				return false;
			}
			if (changed[1] > 0) {
				Files.writeString(p, new Gson().newBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
				restartNeeded = true;
			}
			FILES_DONE.add(fileName);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	private static void patchJson(JsonObject o, boolean inAnimationGroup, boolean extra, int[] count) {
		for (Map.Entry<String, JsonElement> e : new ArrayList<>(o.entrySet())) {
			String k = e.getKey().toLowerCase();
			JsonElement v = e.getValue();
			if (v.isJsonObject()) {
				patchJson(v.getAsJsonObject(), inAnimationGroup || (extra && k.contains("animation")), extra, count);
			} else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean()) {
				boolean match = extra ? inAnimationGroup : (k.contains("animate") && k.contains("visible"));
				boolean want = extra;
				if (match) {
					count[0]++;
					if (v.getAsBoolean() != want) {
						o.addProperty(e.getKey(), want);
						count[1]++;
					}
				}
			}
		}
	}
}
