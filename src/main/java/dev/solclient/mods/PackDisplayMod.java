package dev.solclient.mods;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resource.ResourcePackProfile;

/** Shows the resource pack you are using. */
public class PackDisplayMod extends TextHudMod {

	private String cached;
	private long at;

	public PackDisplayMod() {
		super("Pack Display", "pack_display", "Shows the resource pack you are using.", 0.01F, 0.62F);
	}

	@Override
	protected String text(boolean edit) {
		long now = System.currentTimeMillis();
		if (cached == null || now - at > 2000) {
			at = now;
			cached = compute();
		}
		if (cached == null) {
			return edit ? "Pack: Default" : null;
		}
		return cached;
	}

	private String compute() {
		try {
			List<String> names = new ArrayList<>();
			for (ResourcePackProfile p : mc.getResourcePackManager().getEnabledProfiles()) {
				String id = p.getId();
				if (!id.startsWith("file/") && !id.equals("programmer_art")) {
					continue; // mod / built-in packs
				}
				String n = p.getDisplayName().getString().replaceAll("§.", "");
				if (n.toLowerCase().endsWith(".zip")) {
					n = n.substring(0, n.length() - 4);
				}
				names.add(n);
			}
			if (names.isEmpty()) {
				return "Pack: Default";
			}
			String top = names.get(names.size() - 1); // highest priority is last
			if (top.length() > 28) {
				top = top.substring(0, 27) + "…";
			}
			return names.size() > 1 ? top + " (+" + (names.size() - 1) + ")" : top;
		} catch (RuntimeException e) {
			return null;
		}
	}
}
