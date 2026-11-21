package dev.sleepclient.fx;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;

/**
 * The Locker's "Death Animations" section talks to the death animation engine that ships inside Sleep through this class
 * (so the old stand-alone screen is never needed). Everything is looked up by name once and every call is guarded: if the
 * engine is missing or changed, the section just shows nothing instead of crashing the menu.
 */
public final class DeathAnimBridge {

	private static final String P = "com.baom5.deathanimation.client.";

	/** One animation of the engine. */
	public static final class Entry {
		public final Object anim;
		public final String id, title, description;
		public final int accent;
		public final Item icon;

		Entry(Object anim, String id, String title, String description, int accent, Item icon) {
			this.anim = anim;
			this.id = id;
			this.title = title;
			this.description = description;
			this.accent = accent;
			this.icon = icon;
		}
	}

	private static boolean tried, ok;
	private static Class<?> animCls, cfgCls, ctlCls, rendCls, catCls, relCls;
	private static Method isEnabled, setEnabled, toggleFav, save, setHover, playPreview, recipe, iconOf, animations, useForRandom, previewBody, previewProps, previewZoom;
	private static Field instance, favourites, enabledMaster, randomMode, label;
	private static List<Entry> cache;
	private static Object helper;
	private static Method hIcon, hAccent, hDesc;
	private static boolean hoverOn;

	private DeathAnimBridge() {
	}

	private static synchronized boolean init() {
		if (tried) {
			return ok;
		}
		tried = true;
		try {
			animCls = Class.forName(P + "DeathAnimationConfig$Animation");
			cfgCls = Class.forName(P + "DeathAnimationConfig");
			ctlCls = Class.forName(P + "DeathEffectController");
			rendCls = Class.forName(P + "DeathSceneRenderer");
			catCls = Class.forName(P + "AnimationCatalog");
			relCls = Class.forName(P + "ReleaseProfile");
			instance = cfgCls.getField("INSTANCE");
			favourites = cfgCls.getField("favourites");
			enabledMaster = cfgCls.getField("enabled");
			randomMode = cfgCls.getField("randomMode");
			isEnabled = cfgCls.getMethod("isEnabled", animCls);
			setEnabled = cfgCls.getMethod("setEnabled", animCls, boolean.class);
			toggleFav = cfgCls.getMethod("toggleFavourite", animCls);
			save = cfgCls.getMethod("save");
			useForRandom = cfgCls.getMethod("useForRandom", List.class);
			setHover = ctlCls.getMethod("setHoverPreview", animCls);
			playPreview = ctlCls.getMethod("playPreview", animCls);
			recipe = catCls.getMethod("recipe", animCls);
			iconOf = catCls.getMethod("icon", animCls);
			animations = relCls.getMethod("animations");
			previewBody = rendCls.getMethod("previewBody", DrawContext.class, float.class, int.class, int.class, int.class, int.class);
			previewProps = rendCls.getMethod("previewProps", DrawContext.class, float.class, int.class, int.class, int.class, int.class);
			previewZoom = rendCls.getMethod("previewZoom");
			try {
				Class<?> scr = Class.forName(P + "DeathAnimationsScreen");
				helper = scr.getConstructor(net.minecraft.client.gui.screen.Screen.class).newInstance((Object) null);
				hIcon = scr.getDeclaredMethod("icon", animCls);
				hIcon.setAccessible(true);
				hAccent = scr.getDeclaredMethod("accent", animCls);
				hAccent.setAccessible(true);
				hDesc = scr.getDeclaredMethod("cardDescription", animCls);
				hDesc.setAccessible(true);
			} catch (Throwable t) {
				helper = null; // fall back to the catalog recipes
			}
			label = animCls.getField("label");
			ok = true;
		} catch (Throwable t) {
			System.err.println("[Sleep] death animations engine not available: " + t);
			ok = false;
		}
		return ok;
	}

	public static boolean available() {
		return init();
	}

	private static Object config() throws ReflectiveOperationException {
		return instance.get(null);
	}

	/** Every animation the engine offers, in its own order. */
	public static synchronized List<Entry> all() {
		if (cache != null) {
			return cache;
		}
		List<Entry> out = new ArrayList<>();
		if (!init()) {
			return out;
		}
		try {
			for (Object a : (List<?>) animations.invoke(null)) {
				String id = ((Enum<?>) a).name();
				String title = String.valueOf(label.get(a));
				String desc = "";
				int accent = 0xFF6BA8FF;
				Item icon = null;
				// the engine's own screen knows every animation's icon, colour and text (a switch inside it): ask it
				if (helper != null) {
					try {
						icon = (Item) hIcon.invoke(helper, a);
					} catch (Throwable ignored) {
					}
					try {
						accent = 0xFF000000 | (Integer) hAccent.invoke(helper, a);
					} catch (Throwable ignored) {
					}
					try {
						desc = String.valueOf(hDesc.invoke(helper, a));
					} catch (Throwable ignored) {
					}
				}
				if (icon == null || desc.isEmpty()) {
					try {
						Object r = recipe.invoke(null, a);
						if (r != null) {
							if (desc.isEmpty()) {
								Method d = r.getClass().getDeclaredMethod("description");
								d.setAccessible(true);
								desc = String.valueOf(d.invoke(r));
							}
							Method ac = r.getClass().getDeclaredMethod("accent");
							ac.setAccessible(true);
							accent = 0xFF000000 | (Integer) ac.invoke(r);
						}
					} catch (Throwable ignored) {
					}
					if (icon == null) {
						try {
							icon = (Item) iconOf.invoke(null, a);
						} catch (Throwable ignored) {
						}
					}
				}
				if (icon == null || icon == net.minecraft.item.Items.AIR) {
					icon = net.minecraft.item.Items.SKELETON_SKULL;
				}
				out.add(new Entry(a, id, title, desc, accent, icon));
			}
		} catch (Throwable t) {
			System.err.println("[Sleep] death animations list: " + t);
		}
		cache = out;
		return out;
	}

	public static boolean enabled(Entry e) {
		try {
			return init() && (Boolean) isEnabled.invoke(config(), e.anim);
		} catch (Throwable t) {
			return false;
		}
	}

	public static void setEnabled(Entry e, boolean on) {
		try {
			if (init()) {
				setEnabled.invoke(config(), e.anim, on);
				save.invoke(config());
			}
		} catch (Throwable ignored) {
		}
	}

	public static boolean favourite(Entry e) {
		try {
			return init() && ((List<?>) favourites.get(config())).contains(e.id);
		} catch (Throwable t) {
			return false;
		}
	}

	public static void toggleFavourite(Entry e) {
		try {
			if (init()) {
				toggleFav.invoke(config(), e.anim);
				save.invoke(config());
			}
		} catch (Throwable ignored) {
		}
	}

	/** Master switch: death animations on / off. */
	public static boolean master() {
		try {
			return init() && enabledMaster.getBoolean(config());
		} catch (Throwable t) {
			return false;
		}
	}

	public static void setMaster(boolean on) {
		try {
			if (init()) {
				enabledMaster.setBoolean(config(), on);
				save.invoke(config());
			}
		} catch (Throwable ignored) {
		}
	}

	/** "Random": a different enabled animation each time. */
	public static boolean random() {
		try {
			return init() && randomMode.getBoolean(config());
		} catch (Throwable t) {
			return false;
		}
	}

	public static void setRandom(boolean on) {
		try {
			if (init()) {
				randomMode.setBoolean(config(), on);
				save.invoke(config());
			}
		} catch (Throwable ignored) {
		}
	}

	/** Turns all animations on (true) or off (false) for the random pick. */
	public static void setAll(boolean on) {
		try {
			if (!init()) {
				return;
			}
			List<Object> list = new ArrayList<>();
			if (on) {
				for (Entry e : all()) {
					list.add(e.anim);
				}
			}
			useForRandom.invoke(config(), list);
			save.invoke(config());
		} catch (Throwable ignored) {
		}
	}

	/** The animation the live preview shows (null = none). */
	public static void hover(Entry e) {
		try {
			if (!init() || e == null && !hoverOn) {
				return;
			}
			setHover.invoke(null, e == null ? null : e.anim);
			hoverOn = e != null;
		} catch (Throwable ignored) {
		}
	}

	/** Draws the live preview of the hovered animation inside a rectangle (screen units). */
	public static void drawPreview(DrawContext c, float scale, int x0, int y0, int x1, int y1) {
		try {
			if (init()) {
				// the engine's own zoom (it is a zoom, not a time: 0 draws a dot); never below 3 like its own screen
				float zoom = Math.max(3F, (Float) previewZoom.invoke(null) * Math.max(0.5F, scale));
				previewBody.invoke(null, c, zoom, x0, y0, x1, y1);
				previewProps.invoke(null, c, zoom, x0, y0, x1, y1);
			}
		} catch (Throwable ignored) {
		}
	}

	/** Plays the animation in the world, in front of you ("View"). */
	public static boolean play(Entry e) {
		try {
			if (init()) {
				playPreview.invoke(null, e.anim);
				return true;
			}
		} catch (Throwable t) {
			System.err.println("[Sleep] death animation view: " + t);
		}
		return false;
	}

	// ------------------------------------------------------------------ equipped (one at a time, like any kill effect)
	private static String equippedId;
	private static boolean equippedLoaded;
	private static long enforcedAt;

	private static java.nio.file.Path equippedFile() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kubo").resolve("death-equipped.txt");
	}

	private static synchronized void loadEquipped() {
		if (equippedLoaded) {
			return;
		}
		equippedLoaded = true;
		try {
			java.nio.file.Path f = equippedFile();
			if (java.nio.file.Files.isRegularFile(f)) {
				String t = java.nio.file.Files.readString(f).trim();
				equippedId = t.isEmpty() ? null : t;
			}
		} catch (Exception ignored) {
		}
	}

	private static void saveEquipped() {
		try {
			java.nio.file.Files.createDirectories(equippedFile().getParent());
			java.nio.file.Files.writeString(equippedFile(), equippedId == null ? "" : equippedId);
		} catch (Exception ignored) {
		}
	}

	/** The death animation that is equipped (the one that plays), or null. */
	public static Entry equipped() {
		loadEquipped();
		if (equippedId == null) {
			return null;
		}
		for (Entry e : all()) {
			if (e.id.equals(equippedId)) {
				return e;
			}
		}
		return null;
	}

	public static boolean isEquipped(Entry e) {
		loadEquipped();
		return e != null && e.id.equals(equippedId);
	}

	/** Equips this one (the others and the normal kill effects are switched off by the caller / the shop). */
	public static void equip(Entry e) {
		loadEquipped();
		if (e == null || !owned(e)) {
			return;
		}
		equippedId = e.id;
		saveEquipped();
		enforcedAt = 0;
		enforce();
	}

	public static void unequip() {
		loadEquipped();
		if (equippedId == null) {
			return;
		}
		equippedId = null;
		saveEquipped();
		enforcedAt = 0;
		enforce();
	}

	/**
	 * Keeps the engine in line with what is equipped: only the equipped (and owned) animation is on and nothing else, random off. The engine
	 * itself starts with all of them on, which is why this runs once a second.
	 */
	public static void enforce() {
		long now = System.currentTimeMillis();
		if (now - enforcedAt < 1000L) {
			return;
		}
		enforcedAt = now;
		try {
			if (!init() || !dev.sleepclient.social.Social.shopLoaded()) {
				return;
			}
			loadEquipped();
			Entry eq = equipped();
			if (eq != null && !owned(eq)) {
				equippedId = null;
				eq = null;
				saveEquipped();
			}
			for (Entry e : all()) {
				boolean want = e == eq;
				if (enabled(e) != want) {
					setEnabled(e, want);
				}
			}
			if (master() != (eq != null)) {
				setMaster(eq != null);
			}
			if (random()) {
				setRandom(false);
			}
		} catch (Throwable ignored) {
		}
	}

	/** True when you may use it: bought in the shop, or (owner switch) not sold in the shop yet. */
	public static boolean owned(Entry e) {
		dev.sleepclient.social.Social.Item it = dev.sleepclient.social.Social.deathItem(e.id, e.title);
		// the owner switch (Settings > Emotes) lets the owner account test every animation, bought or not, sold or not
		return dev.sleepclient.emote.EmoteLibrary.unreleasedAllowed() || it != null && it.owned;
	}
}
