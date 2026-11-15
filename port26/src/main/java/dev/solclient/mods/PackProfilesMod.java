package dev.solclient.mods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.Keys;
import dev.solclient.Lang;
import dev.solclient.option.Option;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Pack Profiles (pack organizer): save the resource packs you have on as a set ("PvP", "Survival", "Clean") and switch
 * between sets with one click or a key, instead of dragging packs around every time.
 */
public class PackProfilesMod extends Mod {

	public static PackProfilesMod instance;
	private static final int N = 4;
	private final Option.Text[] names = new Option.Text[N];
	private final Option.Key cycleKey = key("Next Set Key", -1);
	private boolean prev;
	private int current = -1;
	private String msg = "";
	private long msgAt;

	public PackProfilesMod() {
		super("Pack Profiles", "pack_profiles", "Save sets of resource packs and switch between them with one click.", Category.UTILITY);
		instance = this;
		for (int i = 0; i < N; i++) {
			final int slot = i;
			header("Set " + (i + 1));
			names[i] = text("Name", i == 0 ? "PvP" : i == 1 ? "Survival" : i == 2 ? "Clean" : "Set " + (i + 1), 20, "name");
			action("Save the packs I have on now", "Save", () -> {
				save(slot);
				say(Lang.tr("Saved ", "Guardado ") + names[slot].get());
			}, () -> System.currentTimeMillis() - msgAt < 4000L ? msg : describe(slot));
			action("Use this set", "Use", () -> use(slot), () -> "");
		}
	}

	private void say(String s) {
		msg = s;
		msgAt = System.currentTimeMillis();
	}

	private static Path file(int i) {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-pack-sets").resolve("set" + i + ".json");
	}

	private List<String> load(int i) {
		List<String> out = new ArrayList<>();
		try {
			if (Files.exists(file(i))) {
				JsonObject o = JsonParser.parseString(Files.readString(file(i), StandardCharsets.UTF_8)).getAsJsonObject();
				for (JsonElement e : o.getAsJsonArray("packs")) {
					out.add(e.getAsString());
				}
			}
		} catch (Exception ignored) {
		}
		return out;
	}

	private String describe(int i) {
		List<String> l = load(i);
		if (l.isEmpty()) {
			return Lang.tr("empty", "vazio");
		}
		int user = 0;
		for (String s : l) {
			if (s.startsWith("file/")) {
				user++;
			}
		}
		return user + Lang.tr(" packs", " packs");
	}

	private void save(int i) {
		PackRepository pm = mc.getResourcePackRepository();
		JsonArray a = new JsonArray();
		for (String id : pm.getSelectedIds()) {
			a.add(id);
		}
		JsonObject o = new JsonObject();
		o.add("packs", a);
		try {
			Files.createDirectories(file(i).getParent());
			Files.writeString(file(i), o.toString(), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	public void use(int i) {
		List<String> want = load(i);
		if (want.isEmpty()) {
			say(Lang.tr("That set is empty", "Esse conjunto está vazio"));
			return;
		}
		PackRepository pm = mc.getResourcePackRepository();
		pm.reload();
		List<String> ok = new ArrayList<>();
		for (String id : want) {
			if (pm.getAvailableIds().contains(id)) {
				ok.add(id);
			}
		}
		pm.setSelected(ok);
		mc.options.updateResourcePacks(pm); // saves options and reloads the packs
		current = i;
		dev.solclient.social.Social.say(Lang.tr("Packs: ", "Packs: ") + names[i].get(), false);
	}

	@Override
	public void tick() {
		boolean d = dev.solclient.compat.Mc.screen(mc) == null && Keys.down(cycleKey.code());
		if (d && !prev) {
			for (int k = 1; k <= N; k++) {
				int i = Math.floorMod(current + k, N);
				if (!load(i).isEmpty()) {
					use(i);
					break;
				}
			}
		}
		prev = d;
	}
}
