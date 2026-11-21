package dev.sleepclient.mods;

import dev.sleepclient.Gfx;
import dev.sleepclient.option.Option;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * PatPat: crouch and right-click a dog with an empty hand: a hand pats its head (5 frames), the dog squishes a little,
 * you hear a pat and sometimes a happy sound and little hearts pop out. Every click is a pat, as fast as you can click.
 * Only on your screen: nothing is sent to the server (the dog does not sit).
 */
public class PatPatMod extends Mod {

	public static PatPatMod instance;

	private final Option.Choice who;
	private final Option.Bool sneak, sounds, hearts, players, hand;
	private final Option.Num volume, handSize;

	/** entity id -> when the last pat started (ms). */
	private static final Map<Integer, Long> PATS = new HashMap<>();
	/** entity id -> pats in a row (a happy sound every few pats). */
	private static final Map<Integer, Integer> COMBO = new HashMap<>();
	private static final long PAT_MS = 220L;
	public static final SoundEvent PAT = SoundEvent.createVariableRangeEvent(Gfx.id("patpat.pat")), HAPPY = SoundEvent.createVariableRangeEvent(Gfx.id("patpat.lopi"));

	public PatPatMod() {
		super("PatPat", "pat_pat", "Crouch + right-click a dog with an empty hand to pat it.", Category.VISUAL);
		instance = this;
		who = choice("Who can be patted", 0, "Dogs", "All pets", "Every animal");
		players = bool("Pat players too", false);
		players.tip("Only you see it: the other player does not get anything.");
		sneak = bool("Only while crouching", true);
		sneak.tip("Off: a right-click with an empty hand pats too (then you can't make your dog sit with it).");
		sounds = bool("Pat sounds", true);
		volume = num("Pat volume", 100, 0, 200, 5, "%");
		hearts = bool("Hearts", true);
		hand = bool("Show hand", true);
		handSize = num("Hand size", 100, 50, 200, 5, "%");
		handSize.showIf(hand::on);
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	private boolean canPat(Entity e) {
		if (!(e instanceof LivingEntity le) || !le.isAlive() || e == mc.player) {
			return false;
		}
		if (e instanceof Player) {
			return players.on();
		}
		return switch (who.idx()) {
			case 0 -> e instanceof Wolf;
			case 1 -> e instanceof TamableAnimal || e instanceof net.minecraft.world.entity.animal.fox.Fox
					|| e instanceof net.minecraft.world.entity.animal.equine.AbstractHorse;
			default -> e instanceof net.minecraft.world.entity.animal.Animal;
		};
	}

	/** From the right-click: true = it was a pat (the click is kept on this computer). */
	public static boolean tryPat(Player player, Entity e, InteractionHand hand) {
		PatPatMod m = instance;
		if (m == null || !m.enabled || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()
				|| (m.sneak.on() && !player.isShiftKeyDown()) || !m.canPat(e)) {
			return false;
		}
		long now = System.currentTimeMillis();
		Long last = PATS.get(e.getId());
		if (last != null && now - last < 45) {
			return true; // the same click (it arrives twice); every new click is a new pat, as fast as you can click
		}
		int combo = last != null && now - last < 1500 ? COMBO.getOrDefault(e.getId(), 0) + 1 : 1;
		COMBO.put(e.getId(), combo);
		PATS.put(e.getId(), now);
		m.effects(e, combo);
		return true;
	}

	/** The same click must not reach the server (interactEntity comes right after interactEntityAtLocation). */
	public static boolean patting(Entity e) {
		Long t = PATS.get(e.getId());
		return t != null && System.currentTimeMillis() - t < 60;
	}

	private void effects(Entity e, int combo) {
		var w = mc.level;
		if (w == null) {
			return;
		}
		Vec3 h = head(e, 1F);
		float vol = volume.f() / 100F;
		if (sounds.on() && vol > 0) {
			float pitch = 0.9F + (float) Math.random() * 0.25F + (e instanceof LivingEntity le && le.isBaby() ? 0.3F : 0F);
			w.playLocalSound(h.x, h.y, h.z, PAT, SoundSource.NEUTRAL, vol, pitch, false);
			if (combo % 3 == 0) {
				w.playLocalSound(h.x, h.y, h.z, HAPPY, SoundSource.NEUTRAL, vol * 0.9F, pitch, false);
			}
		}
		if (hearts.on() && (combo % 2 == 1)) {
			w.addParticle(ParticleTypes.HEART, h.x + (Math.random() - 0.5) * 0.3, h.y + 0.25, h.z + (Math.random() - 0.5) * 0.3, 0, 0.05, 0);
		}
	}

	/** Top of the head, a bit in front of the body's middle (where a dog's head is). */
	private static Vec3 head(Entity e, float td) {
		Vec3 p = e.getPosition(td);
		float yaw = e instanceof LivingEntity le ? le.getVisualRotationYInDegrees() : e.getYRot();
		double fwd = e.getBbWidth() * 0.45;
		double rad = Math.toRadians(yaw);
		return new Vec3(p.x - Math.sin(rad) * fwd, p.y + e.getEyeHeight() + e.getBbHeight() * 0.12, p.z + Math.cos(rad) * fwd);
	}

	/** 0..1..0 over one pat: how far down the hand / how squished the animal is. */
	private static float press(int id) {
		Long t = PATS.get(id);
		if (t == null) {
			return 0F;
		}
		float k = (System.currentTimeMillis() - t) / (float) PAT_MS;
		return k >= 1F ? 0F : (float) Math.sin(Math.PI * k);
	}

	public static float squish(Entity e) {
		return instance == null || !instance.enabled || PATS.isEmpty() ? 0F : press(e.getId());
	}

	@Override
	public void tick() {
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Integer, Long>> it = PATS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Long> en = it.next();
			if (now - en.getValue() > 2000) {
				it.remove();
				COMBO.remove(en.getKey());
			}
		}
	}

	/** The hand over each patted head (HUD, drawn like the waypoints: projected from the world). */
	public void draw(GuiGraphicsExtractor c) {
		if (!hand.on() || PATS.isEmpty() || mc.level == null || mc.gameRenderer == null) {
			return;
		}
		float td = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		var camera = dev.sleepclient.compat.Mc.camera(mc);
		Vec3 cam = camera.position();
		double yaw = Math.toRadians(camera.yRot()), pitch = Math.toRadians(camera.xRot());
		double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
		double ux = -(rz * fy), uy = rz * fx - rx * fz, uz = rx * fy;
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		double focal = (sh / 2.0) / Math.tan(Math.toRadians(Math.max(5F, WaypointsMod.worldFov)) / 2.0);
		for (int id : PATS.keySet()) {
			float p = press(id);
			Long t = PATS.get(id);
			if (t == null || System.currentTimeMillis() - t > PAT_MS + 250) {
				continue; // the hand lifts away a moment after the last pat
			}
			Entity e = mc.level.getEntity(id);
			if (e == null) {
				continue;
			}
			Vec3 h = head(e, td);
			double dx = h.x - cam.x, dy = h.y - cam.y, dz = h.z - cam.z;
			double zc = dx * fx + dy * fy + dz * fz;
			if (zc < 0.3) {
				continue;
			}
			double xc = dx * rx + dz * rz, yc = dx * ux + dy * uy + dz * uz;
			double sx = sw / 2.0 + xc / zc * focal, sy = sh / 2.0 - yc / zc * focal;
			int size = (int) Math.round(0.55 * handSize.f() / 100F * focal / zc);
			if (size < 4 || size > sh) {
				continue;
			}
			// the hand comes down onto the head and goes back up
			float lift = System.currentTimeMillis() - t > PAT_MS ? (System.currentTimeMillis() - t - PAT_MS) / 250F : 0F;
			int alpha = Math.round(255 * Math.max(0F, 1F - lift));
			int y = (int) Math.round(sy - size * (0.85 - 0.25 * p) - size * 0.4 * lift);
			// hand_frames.png: 5 frames of 112 x 112 side by side, played once per pat
			float k = Math.min(0.999F, (System.currentTimeMillis() - t) / (float) PAT_MS);
			int frame = lift > 0 ? 4 : Math.min(4, (int) (k * 5));
			Gfx.texUv(c, Gfx.id("textures/gui/patpat/hand_frames.png"), (int) Math.round(sx - size / 2.0), y, size, size, frame * 112, 0, 112, 112,
					560, 112, (alpha << 24) | 0xFFFFFF);
		}
	}
}
