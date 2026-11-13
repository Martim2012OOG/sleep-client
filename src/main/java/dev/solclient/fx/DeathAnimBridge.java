package dev.solclient.fx;

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
				try {
					Object r = recipe.invoke(null, a);
					if (r != null) {
						Method d = r.getClass().getDeclaredMethod("description");
						d.setAccessible(true);
						desc = String.valueOf(d.invoke(r));
						Method ac = r.getClass().getDeclaredMethod("accent");
						ac.setAccessible(true);
						accent = 0xFF000000 | (Integer) ac.invoke(r);
					}
				} catch (Throwable ignored) {
				}
				try {
					icon = (Item) iconOf.invoke(null, a);
				} catch (Throwable ignored) {
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

	/** Switches off every animation you do not own (the Locker sells them; the engine would otherwise play all of them). */
	private static long enforcedAt;

	public static void enforce() {
		long now = System.currentTimeMillis();
		if (now - enforcedAt < 1000L) {
			return;
		}
		enforcedAt = now;
		try {
			if (!init() || !dev.solclient.social.Social.shopLoaded()) {
				return;
			}
			for (Entry e : all()) {
				if (!owned(e) && enabled(e)) {
					setEnabled(e, false);
				}
			}
		} catch (Throwable ignored) {
		}
	}

	/** True when you may use it: bought in the shop, or (owner switch) not sold in the shop yet. */
	public static boolean owned(Entry e) {
		dev.solclient.social.Social.Item it = dev.solclient.social.Social.deathItem(e.id, e.title);
		// the owner switch (Settings > Emotes) lets the owner account test every animation, bought or not, sold or not
		return dev.solclient.emote.EmoteLibrary.unreleasedAllowed() || it != null && it.owned;
	}
}
