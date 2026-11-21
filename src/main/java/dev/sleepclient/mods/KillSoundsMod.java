package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

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
			case 1 -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
			case 2 -> SoundEvents.BLOCK_BELL_USE;
			case 3 -> SoundEvents.BLOCK_ANVIL_LAND;
			case 4 -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
			case 5 -> SoundEvents.ENTITY_ARROW_HIT_PLAYER;
			case 6 -> SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST;
			case 7 -> SoundEvents.ENTITY_ENDER_DRAGON_GROWL;
			case 8 -> SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER;
			case 9 -> SoundEvents.ENTITY_GENERIC_EXPLODE.value();
			default -> SoundEvents.ENTITY_PLAYER_LEVELUP;
		};
	}

	private void play() {
		mc.getSoundManager().play(PositionedSoundInstance.ui(event(), pitch.f() / 100F, volume.f() / 100F));
	}
}
