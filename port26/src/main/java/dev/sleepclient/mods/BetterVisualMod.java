package dev.sleepclient.mods;

import dev.sleepclient.option.Option;
import dev.sleepclient.pack.ShaderInstaller;

/**
 * Better Visual (AI look): one-click "cinematic" look. The warm light, soft shadows, bloom, reflections and depth you see in
 * showcase videos come from shader packs, so this installs the one you pick (from Modrinth, the author's licence is respected) and
 * selects it for the next start (Iris). Nothing is bundled and nothing runs in the game while it is not installed.
 */
public class BetterVisualMod extends Mod {

	private final Option.Choice look = tabs("Look", 0, "Balanced", "Ultra pretty (heavy)", "Classic cinematic");

	public BetterVisualMod() {
		super("Better Visual", "better_visual", "Cinematic light, shadows, reflections and bloom with one click (installs a shader pack). Heavy on weak computers.", Category.VISUAL);
		action("Install this look", "Install", () -> ShaderInstaller.install(ShaderInstaller.PROJECTS[Math.min(look.idx(), 2)]), ShaderInstaller::status);
		bool("Remember: restart the game after installing", true);
	}
}
