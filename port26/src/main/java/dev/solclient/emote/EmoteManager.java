package dev.solclient.emote;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Plays one emote at a time on the local player, and the emotes other Plegumo players start on the same
 * server (received from the presence Worker, see dev.solclient.social.Presence).
 */
public final class EmoteManager {

	/** An emote playing on another player. Stops when the animation ends or the player walks away. */
	public static final class Remote {
		final Animation anim;
		final EmoteLibrary.Info info;
		final long startNs;
		long stopNs;
		boolean stopping;
		double x, z;
		boolean placed;

		Remote(Animation anim, EmoteLibrary.Info info) {
			this.anim = anim;
			this.info = info;
			this.startNs = System.nanoTime();
		}
	}

	/** uuid without dashes -> emote. Client thread only (plus reads from render). */
	private static final Map<String, Remote> REMOTE = new ConcurrentHashMap<>();

	private static final float FADE_TICKS = 5F;
	private static final double TICK_NS = 50_000_000.0;

	private static Animation anim;
	private static EmoteLibrary.Info current;
	private static long startNs, stopNs;
	private static boolean stopping;
	private static boolean changedPerspective;
	private static CameraType previous = CameraType.FIRST_PERSON;

	private EmoteManager() {
	}

	public static boolean active() {
		return anim != null;
	}

	public static EmoteLibrary.Info current() {
		return anim == null ? null : current;
	}

	private static float ticksSince(long ns) {
		return (float) ((System.nanoTime() - ns) / TICK_NS);
	}

	/** An emote the site sent that is still downloading: it starts as soon as the file is there (or is dropped after 12 s). */
	private static EmoteLibrary.Info pendingInfo;
	private static long pendingAt;

	public static void play(EmoteLibrary.Info info) {
		Animation a = EmoteLibrary.animation(info);
		if (a == null) {
			if (EmoteLibrary.isRemote(info)) {
				pendingInfo = info;
				pendingAt = System.currentTimeMillis();
			}
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		if (anim != null) {
			finish();
		}
		anim = a;
		current = info;
		startNs = System.nanoTime();
		if (dev.solclient.SolClient.settings == null || dev.solclient.SolClient.settings.shareEmotes.on()) {
			dev.solclient.social.Presence.sendEmote(EmoteLibrary.rawIndex(info));
		}
		stopping = false;
		previous = mc.options.getCameraType();
		changedPerspective = previous == CameraType.FIRST_PERSON;
		if (changedPerspective) {
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
	}

	public static void stop() {
		if (anim != null && !stopping) {
			stopping = true;
			stopNs = System.nanoTime();
		}
	}

	private static void finish() {
		Minecraft mc = Minecraft.getInstance();
		if (changedPerspective && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
			mc.options.setCameraType(previous);
		}
		changedPerspective = false;
		anim = null;
		current = null;
		stopping = false;
	}

	private static boolean wantsToMove(Minecraft mc) {
		Options o = mc.options;
		return o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown()
				|| o.keyJump.isDown() || o.keyShift.isDown();
	}

	// ------------------------------------------------------------ other players
	/** Emote #index (position in emotes/index.json) started by the player with this uuid (no dashes). */
	public static void playRemote(String uuid, int index) {
		if (dev.solclient.SolClient.settings != null && !dev.solclient.SolClient.settings.othersEmotes.on()) {
			return;
		}
		EmoteLibrary.Info info = EmoteLibrary.byRawIndex(index);
		if (info == null) {
			return; // out of range, or an emote that was removed
		}
		Animation a = EmoteLibrary.animation(info);
		if (a != null) {
			REMOTE.put(uuid, new Remote(a, info));
		}
	}

	/** The emote to show on this player entity right now (null = none). */
	public static Remote remoteFor(net.minecraft.world.entity.Entity entity) {
		if (REMOTE.isEmpty() || !(entity instanceof Player p)) {
			return null;
		}
		// only players who use the client (the logo shows in the tab list) can have an emote
		if (!dev.solclient.social.SolarBadge.usesClient(p)) {
			return null;
		}
		Remote r = REMOTE.get(entity.getUUID().toString().replace("-", ""));
		if (r == null) {
			// offline-mode servers: the emote arrived under the account uuid, not the in-game uuid
			String acc = dev.solclient.social.Presence.accountId(p.getName().getString());
			if (acc != null) {
				r = REMOTE.get(acc);
			}
		}
		return r;
	}

	private static void tickRemote(Minecraft mc) {
		if (REMOTE.isEmpty()) {
			return;
		}
		if (mc.level == null) {
			REMOTE.clear();
			return;
		}
		Map<String, Player> byId = new java.util.HashMap<>();
		for (Player p : mc.level.players()) {
			byId.put(p.getUUID().toString().replace("-", ""), p);
			// also under the account uuid, so offline-mode players keep their emotes
			String acc = dev.solclient.social.Presence.accountId(p.getName().getString());
			if (acc != null) {
				byId.put(acc, p);
			}
		}
		REMOTE.entrySet().removeIf(e -> {
			Remote r = e.getValue();
			Player p = byId.get(e.getKey());
			if (p == null) {
				return true;
			}
			if (r.stopping) {
				return ticksSince(r.stopNs) >= FADE_TICKS;
			}
			if (!r.placed) {
				r.placed = true;
				r.x = p.getX();
				r.z = p.getZ();
			}
			float t = ticksSince(r.startNs);
			double dx = p.getX() - r.x, dz = p.getZ() - r.z;
			if ((!r.anim.loop && t >= r.anim.lengthTicks()) || (t > 4F && (dx * dx + dz * dz > 0.04 || p.isShiftKeyDown()))) {
				r.stopping = true;
				r.stopNs = System.nanoTime();
			}
			return false;
		});
	}

	/** Every client tick. */
	public static void tick() {
		tickRemote(Minecraft.getInstance());
		if (pendingInfo != null) {
			Animation pa = EmoteLibrary.animation(pendingInfo);
			if (pa != null) {
				EmoteLibrary.Info p = pendingInfo;
				pendingInfo = null;
				play(p);
			} else if (System.currentTimeMillis() - pendingAt > 12_000L) {
				pendingInfo = null;
			}
		}
		if (anim == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			finish();
			return;
		}
		float t = ticksSince(startNs);
		if (stopping) {
			if (ticksSince(stopNs) >= FADE_TICKS) {
				finish();
			}
			return;
		}
		if (!anim.loop && t >= anim.lengthTicks()) {
			stop();
			return;
		}
		if (t > 4F && dev.solclient.compat.Mc.screen(mc) == null) {
			Vec3 v = mc.player.getDeltaMovement();
			if (wantsToMove(mc) || v.x * v.x + v.z * v.z > 0.0036 || mc.player.isShiftKeyDown()
					|| !mc.player.onGround()) {
				stop();
			}
		}
	}

	// ------------------------------------------------------------ menu preview
	private static Remote preview;
	private static long previewAskedAt;

	/**
	 * The Locker asks for this every frame while an emote is selected: your skin in the preview plays it (looping,
	 * with a short pause between plays). Nothing is sent to other players and your player in the world does not move.
	 */
	public static void requestPreview(EmoteLibrary.Info info) {
		previewAskedAt = System.currentTimeMillis();
		if (info == null) {
			preview = null;
			return;
		}
		if (preview == null || preview.info != info) {
			Animation a = EmoteLibrary.animation(info);
			preview = a == null ? null : new Remote(a, info);
		} else if (!preview.anim.loop && ticksSince(preview.startNs) > preview.anim.lengthTicks() + 16F) {
			preview = new Remote(preview.anim, info); // play it again
		}
	}

	/** The emote the menu preview must show right now (null = none). */
	public static Remote previewRemote() {
		if (preview == null || System.currentTimeMillis() - previewAskedAt > 250L) {
			return null;
		}
		return preview;
	}

	/** Pose for the out-of-game preview model (no props). */
	public static void applyPreview(Remote r, ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg,
			ModelPart leftLeg) {
		float t = ticksSince(r.startNs);
		float w = Math.min(1F, t / FADE_TICKS);
		if (!r.anim.loop) {
			float left = r.anim.lengthTicks() - t;
			w = Math.min(w, Math.max(0F, left / FADE_TICKS)); // ease back to standing before it restarts
		}
		if (w <= 0F) {
			return;
		}
		EmoteRig.apply(r.anim, r.anim.animTime(Math.min(t, Math.max(0F, r.anim.lengthTicks()))), w, head, body, rightArm, leftArm, rightLeg, leftLeg);
	}

	/** Model mixin, for a render pass flagged with another player's emote. */
	public static void applyRemote(Remote r, ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm,
			ModelPart rightLeg, ModelPart leftLeg) {
		float t = ticksSince(r.startNs);
		float w = Math.min(1F, t / FADE_TICKS);
		if (r.stopping) {
			w = Math.min(w, 1F - ticksSince(r.stopNs) / FADE_TICKS);
		}
		if (w <= 0F) {
			return;
		}
		EmoteRig.apply(r.anim, r.anim.animTime(t), w, head, body, rightArm, leftArm, rightLeg, leftLeg);
	}

	/** Props for another player's emote (same as {@link #applyItems} but for that emote). */
	public static void applyRemoteItems(Remote r, ArmedEntityRenderState state) {
		String[] items = EmoteItems.get(r.info.id());
		if (items == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (items[0] != null) {
			mc.getItemModelResolver().updateForTopItem(state.rightHandItemState, stack(items[0]),
					ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, mc.level, mc.player, 0);
		}
		if (items[1] != null) {
			mc.getItemModelResolver().updateForTopItem(state.leftHandItemState, stack(items[1]),
					ItemDisplayContext.THIRD_PERSON_LEFT_HAND, mc.level, mc.player, 0);
		}
	}

	/** Called from the model mixin for every render pass flagged as "local emote". */
	public static void applyTo(ModelPart head, ModelPart body, ModelPart rightArm, ModelPart leftArm,
			ModelPart rightLeg, ModelPart leftLeg) {
		Animation a = anim;
		if (a == null) {
			return;
		}
		float t = ticksSince(startNs);
		float w = Math.min(1F, t / FADE_TICKS);
		if (stopping) {
			w = Math.min(w, 1F - ticksSince(stopNs) / FADE_TICKS);
		}
		if (w <= 0F) {
			return;
		}
		EmoteRig.apply(a, a.animTime(t), w, head, body, rightArm, leftArm, rightLeg, leftLeg);
	}

	/**
	 * Where the emote wants the prop of one hand (position / rotation relative to the hand), already faded in / out: fills o with
	 * x, y, z, pitch, yaw, roll. False when this render pass has no emote or the emote does not move that prop.
	 */
	public static boolean itemPose(EmoteFlag flag, boolean right, float[] o) {
		if (!EmoteItems.ENABLED) {
			return false;
		}
		Animation a;
		float t, w;
		if (flag.sol$isEmoting()) {
			a = anim;
			if (a == null) {
				return false;
			}
			t = ticksSince(startNs);
			w = Math.min(1F, t / FADE_TICKS);
			if (stopping) {
				w = Math.min(w, 1F - ticksSince(stopNs) / FADE_TICKS);
			}
		} else if (flag.sol$remoteEmote() != null) {
			Remote r = flag.sol$remoteEmote();
			a = r.anim;
			t = ticksSince(r.startNs);
			w = Math.min(1F, t / FADE_TICKS);
			if (r.stopping) {
				w = Math.min(w, 1F - ticksSince(r.stopNs) / FADE_TICKS);
			}
			if (r == preview && !a.loop) {
				w = Math.min(w, Math.max(0F, (a.lengthTicks() - t) / FADE_TICKS));
			}
		} else {
			return false;
		}
		int part = right ? Animation.RIGHT_ITEM : Animation.LEFT_ITEM;
		if (w <= 0F || !a.hasItem(part)) {
			return false;
		}
		float[][] s = new float[Animation.PARTS.length][Animation.PROPS.length];
		a.sample(a.animTime(t), s);
		for (int i = 0; i < 6; i++) {
			o[i] = s[part][i] * w;
		}
		return true;
	}

	private static ItemStack stack(String name) {
		if (name == null) {
			return ItemStack.EMPTY;
		}
		if (name.startsWith("prop:")) {
			// our own 3D prop (assets/sol_client/items/prop_<name>.json): a plain stick wearing that item model
			ItemStack st = new ItemStack(net.minecraft.world.item.Items.STICK);
			st.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("sol_client", "prop_" + name.substring(5)));
			return st;
		}
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", name));
		return new ItemStack(item);
	}

	/**
	 * Puts the emote's prop in the hands of the flagged (local player) render state. Done every frame:
	 * Minecraft reuses the same render state object and refills the hands with the real held items each frame.
	 */
	public static void applyItems(ArmedEntityRenderState state) {
		if (current == null) {
			return;
		}
		String[] items = EmoteItems.get(current.id());
		if (items == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (items[0] != null) {
			mc.getItemModelResolver().updateForTopItem(state.rightHandItemState, stack(items[0]),
					ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, mc.level, mc.player, 0);
		}
		if (items[1] != null) {
			mc.getItemModelResolver().updateForTopItem(state.leftHandItemState, stack(items[1]),
					ItemDisplayContext.THIRD_PERSON_LEFT_HAND, mc.level, mc.player, 0);
		}
	}
}
