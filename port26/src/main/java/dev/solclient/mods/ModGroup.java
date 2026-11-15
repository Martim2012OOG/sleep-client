package dev.solclient.mods;

import dev.solclient.SolClient;
import java.util.ArrayList;
import java.util.List;

/**
 * A card that holds several small mods (Info HUD, Screen Tweaks, FOV...): the menu shows one card instead of a dozen,
 * and its settings list every mod inside with its own checkbox and options. The mods inside keep their own id,
 * settings and HUD position, so nothing is lost and profiles keep working.
 */
public class ModGroup extends Mod {

	public ModGroup(String name, String id, String description, Category category) {
		super(name, id, description, category);
		locked = true; // the group itself has no on / off: the mods inside do
	}

	public List<Mod> children() {
		List<Mod> out = new ArrayList<>();
		for (Mod m : SolClient.MODS) {
			if (m.parent == this) {
				out.add(m);
			}
		}
		return out;
	}

	public int onCount() {
		int n = 0;
		for (Mod m : children()) {
			if (m.enabled) {
				n++;
			}
		}
		return n;
	}
}
