package dev.solclient.voice.persona;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solclient.Gfx;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Identifier;

/**
 * A character you can talk as: how the microphone is reshaped (pitch, tone, robot, distortion, echo...) plus its picture.
 * Built-in ones ship in the jar (original art); you can add your own as kubo/personas/&lt;name&gt;.json + &lt;name&gt;.png.
 */
public final class Persona {

	public final String id, name, tagline;
	/** Semitones, tone -1..1 (dark..bright), nasal peak (Hz, dB), robot ring modulation (0..1 at Hz), drive, bit crush, echo, reverb, vibrato (st at Hz), tremolo. */
	public final float pitch, tone, peakHz, peakDb, ring, ringHz, drive, crush, echo, reverb, vibrato, vibratoHz, tremolo, bandLow, bandHigh, gainDb;
	/** Picture: a bundled texture, or a file from the kubo/personas folder (registered when first drawn). */
	public final Identifier image;
	public final Path imageFile;
	public final boolean custom;

	private Persona(String id, String name, String tagline, float[] v, Identifier image, Path file, boolean custom) {
		this.id = id;
		this.name = name;
		this.tagline = tagline;
		pitch = v[0];
		tone = v[1];
		peakHz = v[2];
		peakDb = v[3];
		ring = v[4];
		ringHz = v[5];
		drive = v[6];
		crush = v[7];
		echo = v[8];
		reverb = v[9];
		vibrato = v[10];
		vibratoHz = v[11];
		tremolo = v[12];
		bandLow = v[13];
		bandHigh = v[14];
		gainDb = v[15];
		this.image = image;
		this.imageFile = file;
		this.custom = custom;
	}

	public boolean neutral() {
		return pitch == 0 && tone == 0 && peakDb == 0 && ring == 0 && drive == 0 && crush == 0 && echo == 0 && reverb == 0 && vibrato == 0
				&& tremolo == 0 && bandLow == 0 && bandHigh == 0 && gainDb == 0;
	}

	// order: pitch tone peakHz peakDb ring ringHz drive crush echo reverb vibrato vibratoHz tremolo bandLow bandHigh gainDb
	private static Persona b(String id, String name, String tag, float... v) {
		return new Persona(id, name, tag, v, Gfx.id("textures/persona/" + id + ".png"), null, false);
	}

	private static final List<Persona> BUILTIN = List.of(
			b("me", "Just Me", "Your own voice", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			b("sponge", "Square Sponge", "Squeaky, nasal and always cheerful", 4.5F, 0.25F, 1300, 6, 0, 0, 0, 0, 0, 0, 0.12F, 5.5F, 0, 0, 0, 0),
			b("hacker", "Hooded Hacker", "Deep, gritty, a little scary", -7.5F, -0.35F, 180, 3, 0.22F, 55, 0.32F, 0.3F, 0, 0.08F, 0, 0, 0, 0, 5200, -4),
			b("robot", "Bolt Bot", "Metallic robot", 0, 0.1F, 0, 0, 0.85F, 90, 0.1F, 0.15F, 0, 0, 0, 0, 0, 0, 0, 0),
			b("alien", "Little Green", "Warbly visitor from space", 3, 0.2F, 0, 0, 0.35F, 350, 0, 0, 0.1F, 0.15F, 0.8F, 6.5F, 0, 0, 0, 0),
			b("chipmunk", "Chip", "Tiny and super fast sounding", 9, 0.3F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			b("giant", "Big Rock", "Huge, slow and booming", -10, -0.5F, 120, 4, 0, 0, 0, 0, 0, 0.18F, 0, 0, 0, 0, 0, -3),
			b("demon", "Imp Lord", "Growly with a hellish echo", -9, -0.3F, 0, 0, 0.12F, 40, 0.5F, 0, 0.3F, 0.2F, 0.1F, 4, 0, 0, 0, 0),
			b("ghost", "Boo", "Hollow and floaty", -2, 0.2F, 0, 0, 0, 0, 0, 0, 0.2F, 0.75F, 0.5F, 4.5F, 0.35F, 0, 0, 0),
			b("phone", "Old Phone", "Tinny telephone line", 0, 0, 0, 0, 0, 0, 0.12F, 0.2F, 0, 0, 0, 0, 0, 350, 3200, 3),
			b("kid", "Little Kid", "Young and bright", 6, 0.2F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			b("grandpa", "Grandpa", "Old and shaky", -3.5F, -0.2F, 0, 0, 0, 0, 0, 0, 0, 0, 0.35F, 5, 0.1F, 0, 4200, 0),
			b("cave", "Cave Echo", "Talk from the inside of a cave", 0, 0, 0, 0, 0, 0, 0, 0, 0.35F, 0.9F, 0, 0, 0, 0, 0, 0));

	public static List<Persona> all() {
		List<Persona> out = new ArrayList<>(BUILTIN);
		out.addAll(custom());
		return out;
	}

	public static Persona find(String id) {
		for (Persona p : all()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return BUILTIN.get(0);
	}

	private static List<Persona> customCache;
	private static long customAt;

	/** Your own characters: kubo/personas/*.json (same keys as above) with a picture of the same name (.png). */
	public static synchronized List<Persona> custom() {
		long now = System.currentTimeMillis();
		if (customCache != null && now - customAt < 5000) {
			return customCache;
		}
		customAt = now;
		List<Persona> out = new ArrayList<>();
		Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("kubo").resolve("personas");
		try {
			Files.createDirectories(dir);
			try (var st = Files.list(dir)) {
				for (Path f : (Iterable<Path>) st.sorted()::iterator) {
					String fn = f.getFileName().toString();
					if (!fn.endsWith(".json")) {
						continue;
					}
					String base = fn.substring(0, fn.length() - 5);
					try (BufferedReader r = Files.newBufferedReader(f)) {
						JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
						float[] v = {n(o, "pitch"), n(o, "tone"), n(o, "peakHz"), n(o, "peakDb"), n(o, "ring"), n(o, "ringHz"), n(o, "drive"), n(o, "crush"),
								n(o, "echo"), n(o, "reverb"), n(o, "vibrato"), n(o, "vibratoHz"), n(o, "tremolo"), n(o, "bandLow"), n(o, "bandHigh"), n(o, "gainDb")};
						String name = o.has("name") ? o.get("name").getAsString() : base;
						String tag = o.has("tagline") ? o.get("tagline").getAsString() : "Your own character";
						Path img = dir.resolve(base + ".png");
						out.add(new Persona("custom_" + base, name, tag, v, Gfx.id("generated/persona_" + base.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_") + ".png"),
								Files.isRegularFile(img) ? img : null, true));
					} catch (Exception e) {
						System.err.println("[Sleep] persona " + fn + ": " + e);
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Sleep] personas folder: " + e);
		}
		customCache = out;
		return out;
	}

	private static float n(JsonObject o, String k) {
		return o.has(k) ? o.get(k).getAsFloat() : 0F;
	}
}
