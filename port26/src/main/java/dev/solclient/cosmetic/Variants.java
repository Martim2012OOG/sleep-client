package dev.solclient.cosmetic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;

/** The colour you picked for cosmetics that come in several colours (Among Us hat, halo...). Kept on this computer. */
public final class Variants {

	private static final Gson GSON = new Gson();
	private static final Map<String, String> CHOICE = new ConcurrentHashMap<>();
	private static boolean loaded;

	private Variants() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("kubo-cosmetic-colours.json");
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			if (Files.exists(file())) {
				JsonObject o = GSON.fromJson(Files.readString(file()), JsonObject.class);
				if (o != null) {
					o.entrySet().forEach(e -> CHOICE.put(e.getKey(), e.getValue().getAsString()));
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] cosmetic colours: " + e);
		}
	}

	/** The colour (a cosmetic of the same group) chosen for this group: the first one when nothing was picked. */
	public static CosmeticModels.Def chosen(String group) {
		load();
		List<CosmeticModels.Def> v = CosmeticModels.variantsOf(group);
		if (v.isEmpty()) {
			return null;
		}
		String id = CHOICE.get(group);
		for (CosmeticModels.Def d : v) {
			if (d.id().equals(id)) {
				return d;
			}
		}
		return v.get(0);
	}

	public static void choose(String group, String defId) {
		load();
		CHOICE.put(group, defId);
		try {
			JsonObject o = new JsonObject();
			CHOICE.forEach(o::addProperty);
			Files.writeString(file(), GSON.toJson(o));
		} catch (IOException | RuntimeException e) {
			System.err.println("[Sleep] could not save the colour: " + e);
		}
	}

	/** What you wear for this cosmetic: your chosen colour of its group (the cosmetic itself when it has no colours). */
	public static CosmeticModels.Def resolve(CosmeticModels.Def d) {
		if (d == null || d.group() == null) {
			return d;
		}
		CosmeticModels.Def c = chosen(d.group());
		return c != null ? c : d;
	}
}
