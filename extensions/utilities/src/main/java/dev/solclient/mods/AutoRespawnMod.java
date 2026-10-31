package dev.solclient.mods;

/** Respawns you right after you die. */
public class AutoRespawnMod extends Mod {

	private int wait;

	public AutoRespawnMod() {
		super("Auto Respawn", "auto_respawn", "Respawn automatically when you die.", Category.UTILITY);
	}

	@Override
	public void tick() {
		if (mc.player != null && mc.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen) {
			if (++wait > 10) {
				wait = 0;
				mc.player.requestRespawn();
				mc.setScreen(null);
			}
		} else {
			wait = 0;
		}
	}
}
