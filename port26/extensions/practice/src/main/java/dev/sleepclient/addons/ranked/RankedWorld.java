package dev.sleepclient.addons.ranked;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sleepclient.gui.WorldTransfer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The arena map (15 duel arenas in one world). A fresh copy is unpacked from the extension jar for every match, so
 * blocks placed / broken in one match never show up in the next.
 * The spawn points of every arena (2 per team for 2v2) were measured from the map and ship in ranked_arenas.json.
 */
public final class RankedWorld {

	/** x, y, z, yaw. */
	public record Spot(double x, double y, double z, float yaw) {
	}

	/** x1, z1, x2, z2 = the arena's outline (measured from the map); the barrier walls go just outside it. */
	public record Arena(int id, Spot a, Spot b, Spot a2, Spot b2, int x1, int z1, int x2, int z2) {
	}

	private static List<Arena> arenas;
	private static volatile boolean installing;

	private RankedWorld() {
	}

	public static synchronized List<Arena> arenas() {
		if (arenas == null) {
			List<Arena> out = new ArrayList<>();
			try (InputStream in = RankedWorld.class.getResourceAsStream("/kubo_practice/ranked_arenas.json")) {
				JsonArray a = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
				for (var e : a) {
					JsonObject o = e.getAsJsonObject();
					JsonArray box = o.getAsJsonArray("box");
					out.add(new Arena(o.get("id").getAsInt(), spot(o, "spawnA"), spot(o, "spawnB"), spot(o, "spawnA2"), spot(o, "spawnB2"),
							box.get(0).getAsInt(), box.get(1).getAsInt(), box.get(2).getAsInt(), box.get(3).getAsInt()));
				}
			} catch (Exception e) {
				System.err.println("[Sleep] ranked arenas: " + e);
			}
			arenas = out;
		}
		return arenas;
	}

	public static Arena arena(int id) {
		for (Arena a : arenas()) {
			if (a.id == id) {
				return a;
			}
		}
		return arenas().isEmpty() ? null : arenas().get(0);
	}

	private static Spot spot(JsonObject o, String k) {
		JsonArray a = o.getAsJsonArray(k);
		return new Spot(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsFloat());
	}

	/** Leaves wherever you are, unpacks a fresh copy of the arena world and opens it. */
	public static void open(Minecraft mc) {
		if (installing) {
			return;
		}
		installing = true;
		Path saves = mc.getLevelSource().getBaseDir();
		java.util.function.Consumer<String> start = name -> mc.execute(() -> mc.createWorldOpenFlows().openWorld(name, () -> dev.sleepclient.compat.Mc.setScreen(mc, new TitleScreen())));
		// leave first (the old copy may be the world you are in), then replace it off the render thread
		mc.execute(() -> {
			if (mc.level != null) {
				Ranked.quit(mc, false);
			}
			Thread t = new Thread(() -> install(saves, start), "sleep-arena-install");
			t.setDaemon(true);
			t.start();
		});
	}

	private static void install(Path saves, java.util.function.Consumer<String> start) {
		Path tmp = null;
		try {
			deleteTree(saves.resolve(Ranked.WORLD));
		} catch (Exception e) {
			System.err.println("[Sleep] old arena copy: " + e);
		}
		{
			try (InputStream in = RankedWorld.class.getResourceAsStream("/kubo_practice/ranked_arenas.zip")) {
				tmp = Files.createTempFile("sleep-arenas", ".zip");
				Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
				start.accept(WorldTransfer.unpack(tmp, saves, Ranked.WORLD));
			} catch (Exception e) {
				System.err.println("[Sleep] arena install failed: " + e);
				dev.sleepclient.social.Social.say("Could not install the arena map: " + e.getMessage(), true);
			} finally {
				installing = false;
				if (tmp != null) {
					try {
						Files.deleteIfExists(tmp);
					} catch (Exception ignored) {
						// temp file
					}
				}
			}
		}
	}

	private static void deleteTree(Path p) throws java.io.IOException {
		if (!Files.exists(p)) {
			return;
		}
		try (var w = Files.walk(p)) {
			for (Path f : (Iterable<Path>) w.sorted(java.util.Comparator.reverseOrder())::iterator) {
				Files.deleteIfExists(f);
			}
		}
	}
}
