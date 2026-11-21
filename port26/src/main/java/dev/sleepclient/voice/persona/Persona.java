package dev.sleepclient.voice.persona;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sleepclient.Gfx;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * A character you can talk as: how the microphone is reshaped (pitch, tone, robot, distortion, echo...) plus its picture.
 * Built-in ones ship in the jar (original art); you can add your own as kubo/personas/&lt;name&gt;.json + &lt;name&gt;.png.
 */
public final class Persona {

	public final String id, name, tagline, category;
	/** Semitones, tone -1..1 (dark..bright), nasal peak (Hz, dB), robot ring modulation (0..1 at Hz), drive, bit crush, echo, reverb, vibrato (st at Hz), tremolo. */
	public final float pitch, tone, peakHz, peakDb, ring, ringHz, drive, crush, echo, reverb, vibrato, vibratoHz, tremolo, bandLow, bandHigh, gainDb;
	/** Crowd (several voices at once) and whisper (only breath), 0..1. */
	public final float chorus, whisper;
	/** Where the formants (voice "size") end up, in semitones from your own voice; NaN = they move with the pitch (old behaviour). */
	public final float formant;
	/** Picture: a bundled texture, or a file from the kubo/personas folder (registered when first drawn). */
	public final Identifier image;
	public final Path imageFile;
	public final boolean custom;

	private Persona(String id, String name, String tagline, String category, float[] v, Identifier image, Path file, boolean custom) {
		this.id = id;
		this.name = name;
		this.tagline = tagline;
		this.category = category;
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
		chorus = v.length > 16 ? v[16] : 0;
		whisper = v.length > 17 ? v[17] : 0;
		formant = v.length > 18 ? v[18] : Float.NaN;
		this.image = image;
		this.imageFile = file;
		this.custom = custom;
	}

	public boolean neutral() {
		return pitch == 0 && tone == 0 && peakDb == 0 && ring == 0 && drive == 0 && crush == 0 && echo == 0 && reverb == 0 && vibrato == 0
				&& tremolo == 0 && bandLow == 0 && bandHigh == 0 && gainDb == 0 && chorus == 0 && whisper == 0 && Float.isNaN(formant);
	}

	// order: pitch tone peakHz peakDb ring ringHz drive crush echo reverb vibrato vibratoHz tremolo bandLow bandHigh gainDb chorus whisper [formant]
	// (made by tools/personas/voices.py; pictures: Google Noto 3D emoji, open licence; famous / characters: see PERSONA-IMAGES.md)
	private static Persona b(String id, String name, String tag, String cat, float... v) {
		return new Persona(id, name, tag, cat, v, Gfx.id("textures/persona/" + id + ".png"), null, false);
	}

	/** Category ids and names, in the order of the tabs. */
	public static final String[] CATS = {"all", "famous", "characters", "funny", "scary", "fantasy", "animals", "scifi", "places", "effects", "custom"};
	public static final String[] CAT_NAMES = {"All", "Famous", "Characters", "Funny", "Scary", "Fantasy", "Animals", "Robots & Space", "Places & Devices", "Effects", "Mine"};

	private static final List<Persona> BUILTIN = List.of(
			b("me", "Just Me", "Your own voice", "all", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			b("ronaldo", "Cristiano Ronaldo", "SIUUUU! Confident and full of energy", "famous", 1.0F, 0.15F, 2200, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, -2.0F),
			b("messi", "Messi", "Calm and soft spoken", "famous", 1.5F, 0.1F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2.5F),
			b("speed", "IShowSpeed", "Screams everything, always at max", "famous", 2.0F, 0.2F, 2500, 6, 0, 0, 0.35F, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 1.0F),
			b("mrbeast", "MrBeast", "YouTube video energy", "famous", 1.0F, 0.2F, 3000, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 2.0F),
			b("patrickjane", "Patrick Jane", "Calm, charming and knows everything", "famous", -1.5F, -0.1F, 200, 2, 0, 0, 0, 0, 0, 0.1F, 0, 0, 0, 0, 0, 0, 0, 0, -3.0F),
			b("patrick", "Patrick Star", "Slow, silly and happy", "characters", -2.0F, -0.15F, 700, 5, 0, 0, 0, 0, 0, 0, 0.1F, 3, 0, 0, 0, 0, 0, 0, -4.0F),
			b("squidward", "Squidward", "Nasal and always annoyed", "characters", -1.0F, 0, 1400, 9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 300, 0, 0, 0, 0, -3.0F),
			b("krabs", "Mr. Krabs", "Money! Raspy old sailor", "characters", -3.0F, -0.1F, 900, 4, 0, 0, 0.3F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1.0F),
			b("plankton", "Plankton", "Tiny evil genius", "characters", 5.0F, 0.2F, 0, 0, 0.1F, 80, 0.2F, 0, 0, 0.2F, 0, 0, 0, 0, 0, 0, 0, 0, 6.0F),
			b("shrek", "Shrek", "Deep Scottish ogre", "characters", -5.0F, -0.3F, 250, 4, 0, 0, 0.15F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -6.0F),
			b("donkey", "Donkey", "Never stops talking", "characters", 3.0F, 0.2F, 1500, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.0F),
			b("minion", "Minion", "Banana!", "characters", 10.0F, 0.35F, 1600, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.2F, 0, 9.0F),
			b("pikachu", "Pikachu", "Pika pika!", "characters", 11.0F, 0.4F, 2500, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10.0F),
			b("sonic", "Sonic", "Gotta go fast", "characters", 4.0F, 0.2F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3.0F),
			b("homer", "Homer", "D'oh!", "characters", -3.0F, -0.2F, 600, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -4.0F),
			b("peppa", "Peppa Pig", "Oink oink", "characters", 6.0F, 0.2F, 1000, 8, 0, 0, 0.1F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5.0F),
			b("vader", "Darth Vader", "Heavy breathing, helmet voice", "characters", -7.0F, -0.3F, 0, 0, 0.15F, 30, 0.2F, 0, 0, 0.25F, 0, 0, 0, 0, 5000, 0, 0, 0, -5.0F),
			b("yoda", "Yoda", "Wise and small, backwards he talks", "characters", 3.0F, 0, 1200, 6, 0, 0, 0, 0, 0, 0, 0.15F, 4, 0, 0, 0, 0, 0, 0, 4.0F),
			b("gollum", "Gollum", "My precious...", "characters", 2.0F, 0.2F, 1800, 8, 0, 0, 0.45F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.2F, 3.0F),
			b("groot", "Groot", "I am Groot", "characters", -9.0F, -0.4F, 150, 5, 0, 0, 0, 0, 0, 0.2F, 0, 0, 0, 0, 0, 0, 0, 0, -8.0F),
			b("venom", "Venom", "We are Venom", "characters", -10.0F, -0.3F, 0, 0, 0.1F, 35, 0.5F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.35F, 0, -6.0F),
			b("joker", "Joker", "Why so serious?", "characters", 1.0F, 0.1F, 1500, 4, 0, 0, 0.15F, 0, 0, 0, 0.3F, 5, 0, 0, 0, 0, 0, 0, -1.0F),
			b("batman", "Batman", "Raspy and mysterious", "characters", -6.0F, -0.35F, 0, 0, 0, 0, 0.35F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.15F, -4.0F),
			b("optimus", "Optimus Prime", "Autobots, roll out!", "characters", -5.0F, -0.1F, 0, 0, 0.3F, 70, 0, 0, 0, 0.2F, 0, 0, 0, 0, 0, 0, 0, 0, -3.0F),
			b("stitch", "Stitch", "Ohana!", "characters", 4.0F, 0, 1200, 8, 0, 0, 0.4F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 5.0F),
			b("goku", "Goku", "Kamehameha!", "characters", 3.0F, 0.2F, 2000, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2.0F),
			b("herobrine", "Herobrine", "He is watching you...", "characters", -8.0F, -0.3F, 0, 0, 0, 0, 0, 0, 0.3F, 0.6F, 0, 0, 0, 0, 0, 0, 0, 0.3F, -6.0F),
			b("creeper", "Creeper", "Tssss... BOOM", "characters", 0, 0.3F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.3F, 0, 0, 0, 0, 0.8F, 0.0F),
			b("villager", "Villager", "Hmm. Hrrm.", "characters", -1.0F, -0.1F, 500, 10, 0, 0, 0, 0, 0, 0, 0.2F, 6, 0, 0, 0, 0, 0, 0, -2.0F),
			b("enderman", "Enderman", "Glitchy and creepy", "characters", -6.0F, 0, 0, 0, 0.25F, 120, 0, 0.3F, 0, 0.4F, 0.6F, 4, 0, 0, 0, 0, 0, 0, -4.0F),
			b("sponge", "Square Sponge", "Squeaky, nasal and always cheerful", "funny", 4.5F, 0.25F, 1300, 6, 0, 0, 0, 0, 0, 0, 0.12F, 5.5F, 0, 0, 0, 0, 0, 0),
			b("chipmunk", "Chip", "Tiny and super fast sounding", "funny", 9, 0.3F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7.0F),
			b("helium", "Helium Balloon", "Just breathed a whole balloon", "funny", 12, 0.35F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6.0F),
			b("clown", "Silly Clown", "Wobbly, bright and goofy", "funny", 5, 0.3F, 1500, 6, 0, 0, 0, 0, 0, 0, 0.5F, 8, 0, 0, 0, 0, 0, 0),
			b("baby", "Baby", "Tiny voice, big ideas", "funny", 7, 0.3F, 0, 0, 0, 0, 0, 0, 0, 0, 0.15F, 7, 0, 0, 0, 0, 0, 0, 6.0F),
			b("kid", "Little Kid", "Young and bright", "funny", 6, 0.2F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4.0F),
			b("grandpa", "Grandpa", "Old and shaky", "funny", -3.5F, -0.2F, 0, 0, 0, 0, 0, 0, 0, 0, 0.35F, 5, 0.1F, 0, 4200, 0, 0, 0, -2.0F),
			b("grandma", "Grandma", "Sweet and a little wobbly", "funny", 2, -0.1F, 0, 0, 0, 0, 0, 0, 0, 0, 0.3F, 5, 0, 0, 5000, 0, 0, 0),
			b("dizzy", "Dizzy", "The room is spinning", "funny", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.5F, 1.5F, 0.3F, 0, 0, 0, 0, 0),
			b("fast", "Fast Forward", "Talking at double speed", "funny", 7, 0.2F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			b("slow", "Slow Motion", "Sooo... slooow...", "funny", -8, -0.3F, 0, 0, 0, 0, 0, 0, 0, 0, 0.1F, 1, 0, 0, 0, 0, 0, 0),
			b("cowboy", "Cowboy", "Howdy partner, nasal drawl", "funny", -2, -0.1F, 900, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			b("pirate", "Pirate", "Arr, gravelly sea dog", "funny", -3, -0.2F, 0, 0, 0, 0, 0.2F, 0, 0, 0, 0.15F, 3, 0, 0, 0, 0, 0, 0),
			b("santa", "Santa", "Ho ho ho, warm and deep", "funny", -5, -0.3F, 0, 0, 0, 0, 0, 0, 0, 0.2F, 0, 0, 0, 0, 0, 1, 0, 0, -4.0F),
			b("snowman", "Snowman", "Cold, shivering and cheerful", "funny", 3, 0.2F, 0, 0, 0, 0, 0, 0, 0, 0.3F, 0, 0, 0.35F, 0, 0, 0, 0, 0),
			b("hacker", "Hooded Hacker", "Deep, gritty, a little scary", "scary", -7.5F, -0.35F, 180, 3, 0.22F, 55, 0.32F, 0.3F, 0, 0.08F, 0, 0, 0, 0, 5200, -4, 0, 0),
			b("demon", "Imp Lord", "Growly with a hellish echo", "scary", -9, -0.3F, 0, 0, 0.12F, 40, 0.5F, 0, 0.3F, 0.2F, 0.1F, 4, 0, 0, 0, 0, 0, 0, -6.0F),
			b("villain", "Masked Villain", "Heavy breathing behind a mask", "scary", -6, -0.3F, 0, 0, 0.1F, 30, 0.25F, 0, 0, 0.2F, 0, 0, 0, 0, 6000, 0, 0, 0),
			b("monster", "Monster", "Huge growling beast", "scary", -11, -0.4F, 0, 0, 0, 0, 0.45F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.3F, 0, -7.0F),
			b("goblin", "Goblin", "Sneaky, raspy little thing", "scary", 3, 0, 1200, 6, 0, 0, 0.4F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			b("zombie", "Zombie", "Brains... slow and broken", "scary", -4, -0.2F, 0, 0, 0, 0, 0.35F, 0.1F, 0, 0, 0.6F, 3, 0.2F, 0, 0, 0, 0, 0),
			b("vampire", "Vampire", "Breathy night voice", "scary", -3, 0.1F, 0, 0, 0, 0, 0, 0, 0, 0.5F, 0, 0, 0, 0, 0, 0, 0, 0.25F),
			b("ghost", "Boo", "Hollow and floaty", "scary", -2, 0.2F, 0, 0, 0, 0, 0, 0, 0.2F, 0.75F, 0.5F, 4.5F, 0.35F, 0, 0, 0, 0, 0),
			b("skeleton", "Skeleton", "Rattly and thin", "scary", 1, 0.4F, 0, 0, 0, 0, 0, 0.3F, 0, 0, 0, 0, 0.5F, 600, 0, 0, 0, 0),
			b("doll", "Creepy Doll", "Sweet... too sweet", "scary", 6, 0, 0, 0, 0.15F, 220, 0, 0, 0, 0.3F, 0, 0, 0, 0, 0, 0, 0, 0.3F),
			b("giant", "Big Rock", "Huge, slow and booming", "scary", -10, -0.5F, 120, 4, 0, 0, 0, 0, 0, 0.18F, 0, 0, 0, 0, 0, -3, 0, 0, -7.0F),
			b("wizard", "Old Wizard", "Wise, echoing tower voice", "fantasy", -4, -0.1F, 0, 0, 0, 0, 0, 0, 0.15F, 0.6F, 0.1F, 3, 0, 0, 0, 0, 0, 0),
			b("elf", "Elf", "Light and magical", "fantasy", 4, 0.3F, 0, 0, 0, 0, 0, 0, 0, 0.25F, 0, 0, 0, 0, 0, 0, 0.15F, 0),
			b("fairy", "Fairy", "Tiny sparkly voice", "fantasy", 10, 0.4F, 0, 0, 0, 0, 0, 0, 0, 0.55F, 0.2F, 7, 0, 0, 0, 0, 0.3F, 0, 7.0F),
			b("unicorn", "Unicorn", "Shiny and dreamy", "fantasy", 9, 0.4F, 0, 0, 0, 0, 0, 0, 0, 0.3F, 0, 0, 0, 0, 0, 0, 0.3F, 0),
			b("dragon", "Dragon", "Fire in the throat", "fantasy", -9, 0, 0, 0, 0.08F, 25, 0.55F, 0, 0, 0.3F, 0, 0, 0, 0, 0, 0, 0.25F, 0),
			b("knight", "Knight", "Talking inside a helmet", "fantasy", -2, 0, 0, 0, 0.1F, 60, 0, 0, 0, 0.2F, 0, 0, 0, 0, 3000, 0, 0, 0),
			b("cat", "Kitty", "Meow-ish and purry", "animals", 6, 0, 2500, 6, 0, 0, 0, 0, 0, 0, 0.4F, 6, 0, 0, 0, 0, 0, 0),
			b("mouse", "Mouse", "Squeak squeak", "animals", 11, 0.4F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 400, 0, 0, 0, 0, 8.0F),
			b("bear", "Bear", "Big, warm and grumbly", "animals", -7, -0.4F, 200, 4, 0, 0, 0.15F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -5.0F),
			b("lion", "Lion", "Roaring king", "animals", -8, -0.3F, 0, 0, 0, 0, 0.5F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.2F, 0, -5.0F),
			b("frog", "Frog", "Croaky and bubbly", "animals", -2, 0, 600, 9, 0, 0, 0.3F, 0, 0, 0, 0, 0, 0.3F, 0, 0, 0, 0, 0),
			b("duck", "Duck", "Quacky and nasal", "animals", 4, 0, 1600, 10, 0, 0, 0.2F, 0, 0, 0, 0, 0, 0, 500, 0, 0, 0, 0),
			b("pig", "Piggy", "Snorty and round", "animals", 3, 0, 1000, 8, 0, 0, 0.25F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			b("bee", "Busy Bee", "Buzzy and tiny", "animals", 8, 0, 0, 0, 0.4F, 220, 0, 0, 0, 0, 0, 0, 0.6F, 0, 0, 0, 0, 0),
			b("bat", "Cave Bat", "Squeaky echoes in the dark", "animals", 8, 0, 0, 0, 0, 0, 0, 0, 0.35F, 0.9F, 0, 0, 0, 0, 0, 0, 0, 0),
			b("fish", "Underwater", "Talking from the bottom of the sea", "animals", -1, 0, 0, 0, 0, 0, 0, 0, 0, 0.4F, 0.5F, 2.5F, 0.25F, 0, 900, 0, 0, 0),
			b("robot", "Bolt Bot", "Metallic robot", "scifi", 0, 0.1F, 0, 0, 0.85F, 90, 0.1F, 0.15F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
			b("cyborg", "Cyborg", "Half human, half machine", "scifi", -2, 0, 0, 0, 0.45F, 140, 0, 0.3F, 0, 0, 0, 0, 0, 0, 0, 0, 0.2F, 0),
			b("alien", "Little Green", "Warbly visitor from space", "scifi", 3, 0.2F, 0, 0, 0.35F, 350, 0, 0, 0.1F, 0.15F, 0.8F, 6.5F, 0, 0, 0, 0, 0, 0),
			b("ufo", "UFO Captain", "Signals from the mothership", "scifi", 5, 0, 0, 0, 0.5F, 600, 0, 0, 0.25F, 0, 0.9F, 9, 0, 0, 0, 0, 0, 0),
			b("astronaut", "Astronaut", "Radio from the space station", "scifi", 0, 0, 0, 0, 0, 0, 0.1F, 0.15F, 0, 0.1F, 0, 0, 0, 300, 3400, 0, 0, 0),
			b("glitch", "Glitch", "Broken game character", "scifi", 2, 0, 0, 0, 0.3F, 900, 0, 0.8F, 0, 0, 0, 0, 0.4F, 0, 0, 0, 0, 0),
			b("8bit", "8-Bit", "Straight out of an old console", "scifi", 3, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 4000, 0, 0, 0),
			b("phone", "Old Phone", "Tinny telephone line", "places", 0, 0, 0, 0, 0, 0, 0.12F, 0.2F, 0, 0, 0, 0, 0, 350, 3200, 3, 0, 0),
			b("radio", "Old Radio", "Crackly AM station", "places", 0, 0, 0, 0, 0, 0, 0.3F, 0.25F, 0, 0, 0, 0, 0, 500, 2800, 4, 0, 0),
			b("megaphone", "Megaphone", "LOUD announcements", "places", 0, 0, 1800, 6, 0, 0, 0.6F, 0, 0, 0, 0, 0, 0, 600, 3600, 2, 0, 0),
			b("tvhost", "TV Host", "Polished studio voice", "places", 0, 0, 2500, 4, 0, 0, 0.1F, 0, 0, 0, 0, 0, 0, 120, 7000, 2, 0, 0),
			b("trailer", "Movie Trailer", "In a world... deep and epic", "places", -5, -0.3F, 120, 5, 0, 0, 0, 0, 0, 0.25F, 0, 0, 0, 0, 0, 2, 0, 0, -4.0F),
			b("stadium", "Stadium", "Announcer in a huge arena", "places", 0, 0, 0, 0, 0, 0, 0.1F, 0, 0.45F, 0.7F, 0, 0, 0, 200, 0, 3, 0, 0),
			b("cathedral", "Cathedral", "Giant stone hall", "places", 0, 0, 0, 0, 0, 0, 0, 0, 0.2F, 1, 0, 0, 0, 0, 0, 0, 0, 0),
			b("cave", "Cave Echo", "Talk from the inside of a cave", "places", 0, 0, 0, 0, 0, 0, 0, 0, 0.35F, 0.9F, 0, 0, 0, 0, 0, 0, 0, 0),
			b("bathroom", "Shower", "Singing in the shower", "places", 0, 0, 300, 4, 0, 0, 0, 0, 0, 0.55F, 0, 0, 0, 0, 0, 0, 0, 0),
			b("whisper", "Whisper", "Only air, like a secret", "effects", 0, 0.3F, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 0, 1),
			b("crowd", "Crowd", "A whole group talking at once", "effects", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0),
			b("bass", "Mega Bass", "Boom boom low end", "effects", -1, 0, 90, 9, 0, 0, 0.2F, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0));

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
								n(o, "echo"), n(o, "reverb"), n(o, "vibrato"), n(o, "vibratoHz"), n(o, "tremolo"), n(o, "bandLow"), n(o, "bandHigh"), n(o, "gainDb"),
								n(o, "chorus"), n(o, "whisper"), o.has("formant") ? n(o, "formant") : Float.NaN};
						String name = o.has("name") ? o.get("name").getAsString() : base;
						String tag = o.has("tagline") ? o.get("tagline").getAsString() : "Your own character";
						Path img = dir.resolve(base + ".png");
						out.add(new Persona("custom_" + base, name, tag, "custom", v, Gfx.id("generated/persona_" + base.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_") + ".png"),
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
