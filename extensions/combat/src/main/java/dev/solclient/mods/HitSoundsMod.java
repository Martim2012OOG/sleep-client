package dev.solclient.mods;

import dev.solclient.option.Option;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Plays a sound every time you hit something. */
public class HitSoundsMod extends Mod {

	public static HitSoundsMod instance;
	private final Option.Choice sound = choice("Sound", 0, "Ding", "Orb", "Pop", "Chime", "Crit");
	private final Option.Num volume = num("Volume", 60, 5, 100, 5, "%");
	private final Option.Num pitch = num("Pitch", 1, 0.5F, 2F, 0.1F, "x");

	public HitSoundsMod() {
		super("Hit Sounds", "hit_sounds", "A sound every time you hit something.", Category.VISUAL);
		instance = this;
	}

	public void onAttack(net.minecraft.entity.Entity e) {
		if (mc.player == null || !(e instanceof net.minecraft.entity.LivingEntity)) return;
		SoundEvent s = switch (sound.idx()) {
			case 1 -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
			case 2 -> SoundEvents.ENTITY_ITEM_PICKUP;
			case 3 -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
			case 4 -> SoundEvents.ENTITY_PLAYER_ATTACK_CRIT;
			default -> SoundEvents.ENTITY_ARROW_HIT_PLAYER;
		};
		mc.player.playSound(s, volume.f() / 100F, pitch.f());
	}
}
