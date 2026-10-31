package dev.solclient.addons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Registry of the add-ons shown in the menu's "Add-ons" tab. */
public final class Addons {

	private static final List<Addon> LIST = new ArrayList<>();

	private Addons() {
	}

	public static void init() {
		// the built-in ones moved to extensions (PvP / Bridge Practice = the "practice" extension), which call register()
	}

	public static void register(Addon a) {
		LIST.removeIf(o -> o.id().equals(a.id()));
		LIST.add(a);
	}

	public static void unregister(String id) {
		LIST.removeIf(o -> o.id().equals(id));
	}

	public static List<Addon> list() {
		return Collections.unmodifiableList(LIST);
	}
}
