package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Plays a sound of your choice every time you kill a player (it uses the same kill detection as the kill effects). */
public class KillSoundsMod extends Mod {

	private static KillSoundsMod instance;

	private final Option.Choice sound = choice("Sound", 0, "Level Up", "Pling", "Bell", "Anvil", "Orb", "Arrow Hit", "Fireworks", "Dragon Growl", "Thunder", "Explosion");
	private final Option.Num volume = num("Volume", 100, 0, 100, 5, "%");
	private final Option.Num pitch = num("Pitch", 100, 50, 200, 5, "%");
	private final Option.Action test = action("Try It", "Play", () -> {
		if (instance != null) {
			instance.play();
		}
	}, () -> "Plays the sound once");

	public KillSoundsMod() {
		super("Kill Sounds", "kill_sounds", "Plays a sound when you kill a player.", Category.UTILITY);
		instance = this;
	}

	public static boolean on() {
		return instance != null && instance.enabled;
	}

	public static void onKill() {
		if (on()) {
			instance.play();
		}
	}

	private SoundEvent event() {
		return switch (sound.idx()) {
			case 1 -> SoundEvents.NOTE_BLOCK_PLING.value();
			case 2 -> SoundEvents.BELL_BLOCK;
			case 3 -> SoundEvents.ANVIL_LAND;
			case 4 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
			case 5 -> SoundEvents.ARROW_HIT_PLAYER;
			case 6 -> SoundEvents.FIREWORK_ROCKET_BLAST;
			case 7 -> SoundEvents.ENDER_DRAGON_GROWL;
			case 8 -> SoundEvents.LIGHTNING_BOLT_THUNDER;
			case 9 -> SoundEvents.GENERIC_EXPLODE.value();
			default -> SoundEvents.PLAYER_LEVELUP;
		};
	}

	private void play() {
		mc.getSoundManager().play(SimpleSoundInstance.forUI(event(), pitch.f() / 100F, volume.f() / 100F));
	}
}
