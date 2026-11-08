package dev.solclient.ext;

import dev.solclient.Lang;
import dev.solclient.SolClient;
import dev.solclient.gui.Theme;
import dev.solclient.mods.Mod;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Everything an extension may plug into. The core client only calls these hooks (it never names a mod from an
 * extension), so a missing extension simply means nothing is registered.
 */
public final class KuboApi {

	public static final KuboApi INSTANCE = new KuboApi();

	private static final List<Consumer<Entity>> ATTACK = new CopyOnWriteArrayList<>();
	private static final List<Consumer<DrawContext>> HUD = new CopyOnWriteArrayList<>();
	private static final List<Consumer<DrawContext>> HUD_LAST = new CopyOnWriteArrayList<>();
	private static final List<Consumer<DrawContext>> HUD_F3 = new CopyOnWriteArrayList<>();
	private static final List<BiFunction<Entity, Text, Text>> NAME = new CopyOnWriteArrayList<>();
	private static final List<Predicate<Mod>> HIDE_HUD = new CopyOnWriteArrayList<>();
	private static final List<Function<LivingEntity, Float>> SCALE = new CopyOnWriteArrayList<>();
	private static final List<Predicate<ItemStack>> TOTEM = new CopyOnWriteArrayList<>();
	private static final List<java.util.function.BooleanSupplier> HIDE_TOASTS = new CopyOnWriteArrayList<>();
	private static final List<java.util.function.IntSupplier> STREAM_FPS = new CopyOnWriteArrayList<>();
	private static final List<Runnable> STATS_CHANGED = new CopyOnWriteArrayList<>();
	private static final List<java.util.function.BooleanSupplier> REPLACES_F3 = new CopyOnWriteArrayList<>();

	private static final List<BiFunction<UUID, Text, Text>> TAB = new CopyOnWriteArrayList<>();
	private static final List<java.util.function.BiConsumer<LivingEntity, Byte>> STATUS = new CopyOnWriteArrayList<>();
	private static final List<Consumer<dev.solclient.social.KuboLink.Ping>> FRIEND_PING = new CopyOnWriteArrayList<>();
	/** Commander (Ctrl + K) entries added by extensions. */
	public record Command(String title, String sub, String icon, Runnable run) {
	}
	public static final List<Command> COMMANDS = new CopyOnWriteArrayList<>();
	private static final List<java.util.function.UnaryOperator<net.minecraft.client.gui.screen.Screen>> SCREEN = new CopyOnWriteArrayList<>();
	private static final List<Consumer<net.minecraft.util.math.Vec3d>> EXPLOSION = new CopyOnWriteArrayList<>();
	private static final List<java.util.function.BiPredicate<net.minecraft.util.math.BlockPos, net.minecraft.block.BlockState>> BLOCK_BREAK = new CopyOnWriteArrayList<>();
	private static final List<Runnable> LEFT_CLICK = new CopyOnWriteArrayList<>();
	private static final List<SoundListener> SOUND = new CopyOnWriteArrayList<>();

	/** A sound the server played at a point in the world (id like "item.mace.smash_ground"). */
	@FunctionalInterface
	public interface SoundListener {
		void onSound(String id, double x, double y, double z);
	}

	/** The server played a sound at a position (before Minecraft plays it). Read only. */
	public void onWorldSound(SoundListener l) {
		SOUND.add(l);
	}

	/** The full F3 screen is open (not just one "always shown" debug line). */
	public static boolean f3Open() {
		return dev.solclient.SolClient.f3Open(net.minecraft.client.MinecraftClient.getInstance());
	}

	public static void worldSound(String id, double x, double y, double z) {
		for (var l : SOUND) {
			try {
				l.onSound(id, x, y, z);
			} catch (Throwable t) {
				System.err.println("[Sleep] extension (sound): " + t);
			}
		}
	}

	/** Lets an extension swap a screen before it opens (return the same screen to leave it alone). */
	public void screenFilter(java.util.function.UnaryOperator<net.minecraft.client.gui.screen.Screen> f) {
		SCREEN.add(f);
	}

	public static net.minecraft.client.gui.screen.Screen filterScreen(net.minecraft.client.gui.screen.Screen s) {
		for (var f : SCREEN) {
			try {
				net.minecraft.client.gui.screen.Screen r = f.apply(s);
				if (r != s) {
					return r;
				}
			} catch (Throwable ignored) {
			}
		}
		return s;
	}

	/** The server told us an explosion happened at this point. Read only. */
	public void onExplosion(Consumer<net.minecraft.util.math.Vec3d> c) {
		EXPLOSION.add(c);
	}

	public static void explosion(net.minecraft.util.math.Vec3d at) {
		for (var c : EXPLOSION) {
			try {
				c.accept(at);
			} catch (Throwable t) {
				System.err.println("[Sleep] extension (explosion): " + t);
			}
		}
	}

	/**
	 * A block was broken (by you or anyone near you). Return true to take over the break effect: the little flat
	 * vanilla break particles are then not spawned.
	 */
	public void onBlockBreak(java.util.function.BiPredicate<net.minecraft.util.math.BlockPos, net.minecraft.block.BlockState> p) {
		BLOCK_BREAK.add(p);
	}

	public static boolean blockBreak(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
		boolean took = false;
		for (var p : BLOCK_BREAK) {
			try {
				if (p.test(pos.toImmutable(), state)) {
					took = true;
				}
			} catch (Throwable t) {
				System.err.println("[Sleep] extension (block break): " + t);
			}
		}
		return took;
	}

	/** Every left mouse press in game (no screen open). */
	public void onLeftClick(Runnable r) {
		LEFT_CLICK.add(r);
	}

	public static void leftClick() {
		for (Runnable r : LEFT_CLICK) {
			try {
				r.run();
			} catch (Throwable ignored) {
			}
		}
	}

	private KuboApi() {
	}

	/** An entity status from the server (35 = totem used, 2 = hurt, 3 = death...). Read only. */
	public void onEntityStatus(java.util.function.BiConsumer<LivingEntity, Byte> c) {
		STATUS.add(c);
	}

	public static void entityStatus(LivingEntity e, byte status) {
		try {
			dev.solclient.mods.TotemPopsMod.onStatus(e, status);
		} catch (Throwable t) {
			System.err.println("[Sleep] totem pops: " + t);
		}
		for (var c : STATUS) {
			try {
				c.accept(e, status);
			} catch (Throwable t) {
				System.err.println("[Sleep] extension (status): " + t);
			}
		}
	}

	/** Changes a name in the tab list (uuid of the player). */
	public void tabDecorator(BiFunction<UUID, Text, Text> f) {
		TAB.add(f);
	}

	public static Text decorateTab(UUID id, Text name) {
		for (BiFunction<UUID, Text, Text> f : TAB) {
			try {
				Text r = f.apply(id, name);
				if (r != null) {
					name = r;
				}
			} catch (Throwable t) {
				System.err.println("[Sleep] extension (tab): " + t);
			}
		}
		return name;
	}

	/** A friend marked a place (Pings). */
	public void onFriendPing(Consumer<dev.solclient.social.KuboLink.Ping> c) {
		FRIEND_PING.add(c);
	}

	/** Adds an entry to the Commander (Ctrl + K search). */
	public void command(String title, String sub, String icon, Runnable run) {
		COMMANDS.add(new Command(title, sub, icon, run));
	}

	public static void friendPing(dev.solclient.social.KuboLink.Ping p) {
		for (var c : FRIEND_PING) {
			try {
				c.accept(p);
			} catch (Throwable t) {
				System.err.println("[Sleep] extension (ping): " + t);
			}
		}
	}

	// ------------------------------------------------------------------ registration
	/** Adds a mod to the menu (call from {@link KuboExtension#register}). */
	public void addMod(Mod m) {
		SolClient.MODS.add(m);
		Theme.addIcon(m.id);
	}

	/** Extra icon id (assets/sol_client/textures/gui/icon/&lt;id&gt;.png inside the extension jar). */
	public void addIcon(String id) {
		Theme.addIcon(id);
	}

	/** Portuguese translations ("English&lt;TAB&gt;Português" per line), e.g. from the extension's own jar. */
	public void addTranslations(InputStream tsv) {
		Lang.addFrom(tsv);
	}

	/** Called when you hit an entity. */
	public void onAttack(Consumer<Entity> c) {
		ATTACK.add(c);
	}

	/** Drawn on the HUD every frame (before the HUD mods). */
	public void onHud(Consumer<DrawContext> c) {
		HUD.add(c);
	}

	/** Drawn on the HUD every frame, on top of everything. */
	private static volatile java.util.function.Supplier<double[]> CAMERA;

	/**
	 * Takes over the camera (cut-scenes): the supplier returns {x, y, z, yaw, pitch, fov} every frame (fov &lt;= 0 keeps
	 * the game's), or null to let the game place the camera. One extension at a time; null clears it.
	 */
	public void cameraOverride(java.util.function.Supplier<double[]> s) {
		CAMERA = s;
	}

	/** The cut-scene camera of this frame, or null. */
	public static double[] camera() {
		var s = CAMERA;
		if (s == null) {
			return null;
		}
		try {
			return s.get();
		} catch (Throwable t) {
			return null;
		}
	}

	public void onHudLast(Consumer<DrawContext> c) {
		HUD_LAST.add(c);
	}

	/** Drawn every frame even while F3 is open (an F3 screen replacement). */
	public void onF3Hud(Consumer<DrawContext> c) {
		HUD_F3.add(c);
	}

	/** Changes the name shown above a player / in the tab list (null entity = tab list entry without a player). */
	public void nameDecorator(BiFunction<Entity, Text, Text> f) {
		NAME.add(f);
	}

	/** Return true to hide a HUD mod (Streamer Mode). */
	public void hudFilter(Predicate<Mod> p) {
		HIDE_HUD.add(p);
	}

	/** Size multiplier of an entity model (1 = normal). */
	public void entityScale(Function<LivingEntity, Float> f) {
		SCALE.add(f);
	}

	/** Return true to replace the big totem animation for this stack (the extension draws its own). */
	public void totemAnimation(Predicate<ItemStack> p) {
		TOTEM.add(p);
	}

	public void hideToasts(java.util.function.BooleanSupplier s) {
		HIDE_TOASTS.add(s);
	}

	/** Called when the Sleep stats (kills, distance, minigame records...) change. */
	public void onStatsChanged(Runnable r) {
		STATS_CHANGED.add(r);
	}

	/**
	 * While you stream: return -1 when not streaming, 0 to keep the full frame rate even in the background (no
	 * "lower FPS in background" / AFK limit, so the live never drops to 15 FPS when you click OBS), or a number to also
	 * lock the game to that many FPS (steady frame pacing = a smoother stream).
	 */
	public void streamFps(java.util.function.IntSupplier s) {
		STREAM_FPS.add(s);
	}

	/** -1 = nobody streams; 0 = keep full FPS; n = keep full FPS and lock to n. */
	public static int streamFpsCap() {
		int out = -1;
		for (var s : STREAM_FPS) {
			try {
				int v = s.getAsInt();
				if (v == 0 && out < 0) {
					out = 0;
				} else if (v > 0) {
					out = out <= 0 ? v : Math.min(out, v);
				}
			} catch (Throwable ignored) {
				// a broken extension never changes the frame rate
			}
		}
		return out;
	}

	/** Return true while the extension draws its own F3 screen (vanilla debug HUD is then hidden). */
	public void replacesF3(java.util.function.BooleanSupplier s) {
		REPLACES_F3.add(s);
	}

	// ------------------------------------------------------------------ called by the core
	public static void fireAttack(Entity e) {
		for (Consumer<Entity> c : ATTACK) {
			try {
				c.accept(e);
			} catch (Throwable t) {
				System.err.println("[Sleep] extension (attack): " + t);
			}
		}
	}

	public static void drawF3(DrawContext c) {
		for (Consumer<DrawContext> d : HUD_F3) {
			try {
				d.accept(c);
			} catch (Throwable t) {
				System.err.println("[Sleep] extension (f3): " + t);
			}
		}
	}

	public static void drawHud(DrawContext c, boolean last) {
		long kuboPerfT0 = System.nanoTime();
		try {
			drawHud0(c, last);
		} finally {
			dev.solclient.Perf.add(last ? "Extensions HUD (top)" : "Extensions HUD", System.nanoTime() - kuboPerfT0);
		}
	}

	private static void drawHud0(DrawContext c, boolean last) {
		for (Consumer<DrawContext> d : last ? HUD_LAST : HUD) {
			try {
				d.accept(c);
			} catch (Throwable t) {
				System.err.println("[Sleep] extension (hud): " + t);
			}
		}
	}

	public static Text decorateName(Entity e, Text name) {
		for (BiFunction<Entity, Text, Text> f : NAME) {
			try {
				Text r = f.apply(e, name);
				if (r != null) {
					name = r;
				}
			} catch (Throwable t) {
				System.err.println("[Sleep] extension (name): " + t);
			}
		}
		return name;
	}

	public static boolean hidesHud(Mod m) {
		for (Predicate<Mod> p : HIDE_HUD) {
			if (p.test(m)) {
				return true;
			}
		}
		return false;
	}

	public static float scaleFor(LivingEntity e) {
		float s = 1F;
		for (Function<LivingEntity, Float> f : SCALE) {
			Float v = f.apply(e);
			if (v != null) {
				s *= v;
			}
		}
		return s;
	}

	public static boolean replaceTotem(ItemStack stack) {
		for (Predicate<ItemStack> p : TOTEM) {
			if (p.test(stack)) {
				return true;
			}
		}
		return false;
	}

	public static boolean toastsHidden() {
		for (var s : HIDE_TOASTS) {
			if (s.getAsBoolean()) {
				return true;
			}
		}
		return false;
	}

	public static void statsChanged() {
		for (Runnable r : STATS_CHANGED) {
			try {
				r.run();
			} catch (Throwable t) {
				System.err.println("[Sleep] extension (stats): " + t);
			}
		}
	}

	public static boolean f3Replaced() {
		for (var s : REPLACES_F3) {
			if (s.getAsBoolean()) {
				return true;
			}
		}
		return false;
	}

	/** Used by Tab list / tier tags: the uuid helper so extensions don't need to know the profile classes. */
	public static UUID idOf(Object profile) {
		return dev.solclient.social.SolarBadge.idOf(profile);
	}
}
