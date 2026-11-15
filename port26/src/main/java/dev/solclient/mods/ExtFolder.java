package dev.solclient.mods;

/**
 * Not a real mod: the "folder" card of an installed extension in the mod list. Opening it shows the extension's mods
 * (the real ones, with their own switches and settings). Never saved and never added to the mod list itself.
 */
public final class ExtFolder extends Mod {

	public final String modId, extId;

	public ExtFolder(String modId, String extId, String name, String description) {
		super(name, "folder_" + extId, description, Category.NONE);
		this.modId = modId;
		this.extId = extId;
		this.locked = true;
	}
}
