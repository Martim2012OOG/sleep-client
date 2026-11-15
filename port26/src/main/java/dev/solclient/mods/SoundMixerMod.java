package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.client.resources.sounds.SoundInstance;

/** Sound Mixer: separate volume for footsteps, explosions, hits, rain, eating, cave sounds and more. */
public class SoundMixerMod extends Mod {

	public static SoundMixerMod instance;
	private final Option.Num steps = num("Footsteps", 100, 0, 200, 5, "%");
	private final Option.Num explosions = num("Explosions", 100, 0, 200, 5, "%");
	private final Option.Num hits = num("Hits & Hurt", 100, 0, 200, 5, "%");
	private final Option.Num rain = num("Rain & Thunder", 100, 0, 200, 5, "%");
	private final Option.Num eating = num("Eating & Drinking", 100, 0, 200, 5, "%");
	private final Option.Num blocks = num("Placing & Breaking", 100, 0, 200, 5, "%");
	private final Option.Num cave = num("Cave Ambience", 100, 0, 200, 5, "%");
	private final Option.Num pearls = num("Pearls & Bows", 100, 0, 200, 5, "%");
	private final Option.Num xp = num("XP & Level Up", 100, 0, 200, 5, "%");
	private final Option.Num portal = num("Portals", 100, 0, 200, 5, "%");
	private final Option.Num villagers = num("Villagers", 100, 0, 200, 5, "%");
	private final Option.Num pistons = num("Pistons & Doors", 100, 0, 200, 5, "%");

	public SoundMixerMod() {
		super("Sound Mixer", "sound_mixer", "Its own volume for footsteps, explosions, hits, rain and more.", Category.UTILITY);
		instance = this;
	}

	/** Volume multiplier for one sound (1 = unchanged). Called a lot: keep it cheap. */
	public static float factor(SoundInstance s) {
		if (instance == null || !instance.enabled || s == null || s.getIdentifier() == null) {
			return 1F;
		}
		String p = s.getIdentifier().getPath();
		SoundMixerMod m = instance;
		Option.Num o = null;
		if (p.endsWith(".step") || p.contains(".step.")) {
			o = m.steps;
		} else if (p.contains("explode") || p.contains("explosion")) {
			o = m.explosions;
		} else if (p.contains(".hurt") || p.contains("player.attack") || p.contains(".death") && p.startsWith("entity.player")) {
			o = m.hits;
		} else if (p.startsWith("weather.") || p.contains("lightning_bolt")) {
			o = m.rain;
		} else if (p.contains(".eat") || p.contains(".drink") || p.contains(".burp")) {
			o = m.eating;
		} else if (p.startsWith("block.") && (p.endsWith(".place") || p.endsWith(".break") || p.endsWith(".hit"))) {
			o = m.blocks;
		} else if (p.startsWith("ambient.cave") || p.startsWith("ambient.")) {
			o = m.cave;
		} else if (p.contains("ender_pearl") || p.contains("arrow") || p.contains("crossbow") || p.contains(".bow")) {
			o = m.pearls;
		} else if (p.contains("experience_orb") || p.contains("player.levelup")) {
			o = m.xp;
		} else if (p.contains("portal")) {
			o = m.portal;
		} else if (p.contains("villager") || p.contains("wandering_trader")) {
			o = m.villagers;
		} else if (p.contains("piston") || p.contains("_door") || p.contains("trapdoor") || p.contains("fence_gate")) {
			o = m.pistons;
		}
		return o == null ? 1F : o.f() / 100F;
	}
}
