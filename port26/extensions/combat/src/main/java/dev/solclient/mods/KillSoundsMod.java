package dev.solclient.mods;

import dev.solclient.Config;
import dev.solclient.SoundLib;
import dev.solclient.gui.SoundPickerScreen;
import dev.solclient.option.Option;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Kill Sounds: a sound of your choice when something you hit dies (same kill detection as Kill Effect).
 *
 * <p>Every slot (Kill / Player / Mob / Double / Triple) opens the "Pick a sound" window: it lists every sound of the
 * game and of your resource packs, with a search box and a play button to listen before you pick. Your own sounds
 * are {@code .wav} files in the {@code kubo-sounds} folder (or dropped onto the settings) and show up at the top of
 * the same list. The list itself lives in {@link SoundLib} (the notification sounds use it too).
 */
public class KillSoundsMod extends Mod {

	public static KillSoundsMod instance;

	// the chosen sounds (hidden text options: they only keep the choice in the config; the buttons below show them)
	private final Option.Text killId = text("Kill Sound Id", "minecraft:block.note_block.bell", 160, "").showIf(() -> false);
	private final Option.Text playerId = text("Player Kill Sound Id", "", 160, "").showIf(() -> false);
	private final Option.Text mobId = text("Mob Kill Sound Id", "", 160, "").showIf(() -> false);
	private final Option.Text doubleId = text("Double Kill Sound Id", "", 160, "").showIf(() -> false);
	private final Option.Text tripleId = text("Triple Kill Sound Id", "", 160, "").showIf(() -> false);

	private final Option.Num volume;
	private final Option.Num pitch;
	private final Option.Bool playersOnly;

	/** entity id -> when you last hit it */
	private final Map<Integer, Long> hit = new HashMap<>();
	private final Map<Integer, Entity> ents = new HashMap<>();
	private long lastKillAt;
	private int streak;

	public KillSoundsMod() {
		super("Kill Sounds", "kill_sounds", "Play a sound of your choice when you kill something. Pick from every game sound or add your own.",
				Category.VISUAL);
		instance = this;
		header("Sounds");
		action("Kill Sound", "Choose...", () -> pick("Kill Sound", killId, "No sound"), () -> SoundLib.pretty(killId.get()));
		action("Player Kill Sound", "Choose...", () -> pick("Player Kill Sound", playerId, "Use Kill Sound"), () -> playerId.get().isEmpty() ? "Kill Sound" : SoundLib.pretty(playerId.get()));
		action("Mob Kill Sound", "Choose...", () -> pick("Mob Kill Sound", mobId, "Use Kill Sound"), () -> mobId.get().isEmpty() ? "Kill Sound" : SoundLib.pretty(mobId.get()));
		action("Double Kill Sound", "Choose...", () -> pick("Double Kill Sound", doubleId, "Use normal"), () -> doubleId.get().isEmpty() ? "Normal" : SoundLib.pretty(doubleId.get()));
		action("Triple Kill Sound", "Choose...", () -> pick("Triple Kill Sound", tripleId, "Use normal"), () -> tripleId.get().isEmpty() ? "Normal" : SoundLib.pretty(tripleId.get()));
		header("Settings");
		volume = num("Volume", 80, 5, 100, 5, "%");
		pitch = num("Pitch", 1, 0.5F, 2F, 0.1F, "x");
		playersOnly = bool("Players Only", false);
		header("Your own sounds");
		action("Sounds folder", "Open folder", SoundLib::openFolder, () -> SoundLib.customFiles().size() + " own sound(s)");
		action("Test", "Play Kill Sound", () -> playRef(killId.get()), () -> "");
	}

	private void playRef(String ref) {
		SoundLib.play(ref, volume.f() / 100F, pitch.f());
	}

	// ------------------------------------------------------------------------------------------ picking
	private void pick(String title, Option.Text slot, String noneLabel) {
		var screen = dev.solclient.compat.Mc.screen(mc);
		dev.solclient.compat.Mc.setScreen(mc, new SoundPickerScreen(screen, title, slot.get(), noneLabel, false, ref -> {
			slot.set(ref == null ? "" : ref);
			Config.save();
		}));
	}

	// ------------------------------------------------------------------------------------------ kills
	public void onAttack(Entity e) {
		if (!(e instanceof LivingEntity) || playersOnly.on() && !(e instanceof Player)) {
			return;
		}
		hit.put(e.getId(), System.currentTimeMillis());
		ents.put(e.getId(), e);
	}

	@Override
	public void tick() {
		if (hit.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<Integer, Long>> it = hit.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Long> en = it.next();
			Entity e = ents.get(en.getKey());
			if (e == null || now - en.getValue() > 4000) {
				it.remove();
				ents.remove(en.getKey());
				continue;
			}
			boolean dead = e instanceof LivingEntity l ? l.isDeadOrDying() || l.getHealth() <= 0 : !e.isAlive();
			if (dead) {
				it.remove();
				ents.remove(en.getKey());
				onKill(e, now);
			}
		}
	}

	private void onKill(Entity victim, long now) {
		streak = now - lastKillAt < 6000 ? streak + 1 : 1;
		lastKillAt = now;
		String ref = "";
		if (streak >= 3) {
			ref = tripleId.get();
		} else if (streak == 2) {
			ref = doubleId.get();
		}
		if (ref.isEmpty()) {
			ref = victim instanceof Player ? playerId.get() : mobId.get();
		}
		if (ref.isEmpty()) {
			ref = killId.get();
		}
		playRef(ref);
	}

	/** A sound file dragged onto the window while the Kill Sounds settings are open: becomes the Kill Sound. */
	@Override
	public boolean onFileDropped(Path file) {
		String n = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".mp3")) {
			String ref = SoundLib.importFile(file);
			if (ref != null) {
				killId.set(ref);
				Config.save();
				playRef(ref);
			}
			return true;
		}
		return false;
	}
}
