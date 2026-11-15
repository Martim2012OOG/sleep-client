package dev.solclient.mods;

/** Respawns you right after you die. */
public class AutoRespawnMod extends Mod {

	private int wait;

	public AutoRespawnMod() {
		super("Auto Respawn", "auto_respawn", "Respawn automatically when you die.", Category.UTILITY);
	}

	@Override
	public void tick() {
		if (mc.player != null && dev.solclient.compat.Mc.screen(mc) instanceof net.minecraft.client.gui.screens.DeathScreen) {
			if (++wait > 10) {
				wait = 0;
				mc.player.respawn();
				dev.solclient.compat.Mc.setScreen(mc, null);
			}
		} else {
			wait = 0;
		}
	}
}
