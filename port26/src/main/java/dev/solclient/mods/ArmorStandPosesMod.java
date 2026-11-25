package dev.solclient.mods;

import dev.solclient.Keys;
import dev.solclient.option.Option;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Rotations;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Armor Stand Poses: the 13 Bedrock pose names on Java armor stands. Look at a stand and press the key to cycle the pose
 * (Shift + key = back). The pose is only drawn on the screens of players who have the client: it never touches the real
 * armor stand and never reaches the Minecraft server. It is shared through the same room as emotes (see Presence).
 * The angles are our own approximation of the Bedrock poses (the exact Mojang values are not published to copy).
 */
public class ArmorStandPosesMod extends Mod {

	/** Added to the pose number when it travels in the "emote" message, so old clients ignore it. */
	public static final int WIRE = 10_000;

	public static final String[] NAMES = {"Default", "None", "Solemn", "Athena", "Brandish", "Honor", "Entertain", "Salute", "Riposte", "Zombie", "Cancan A", "Cancan B", "Hero"};

	/** head, body, leftArm, rightArm, leftLeg, rightLeg: pitch, yaw, roll in degrees. */
	private static final float[][][] POSES = {
			null, // Default: nothing is changed
			{{0, 0, 0}, {0, 0, 0}, {0, 0, -3}, {0, 0, 3}, {0, 0, 0}, {0, 0, 0}},
			{{12, 0, 0}, {0, 0, 0}, {-45, -35, -8}, {-45, 35, 8}, {0, 0, 0}, {0, 0, 0}},
			{{0, -15, 0}, {0, 8, 0}, {5, 0, -10}, {-105, 0, 5}, {0, 0, -3}, {8, 0, 3}},
			{{0, -20, 0}, {0, -10, 0}, {10, 0, -15}, {-85, -25, 0}, {-18, 0, -3}, {22, 0, 3}},
			{{5, 20, 0}, {0, 0, 0}, {0, 0, -5}, {-100, 55, 0}, {0, 0, -2}, {0, 0, 2}},
			{{-8, 0, 0}, {0, 0, 0}, {-10, 0, -80}, {-10, 0, 80}, {0, 0, -12}, {0, 0, 12}},
			{{-5, -35, 0}, {0, 0, 0}, {0, 0, -5}, {-110, -40, 0}, {0, 0, -2}, {0, 0, 2}},
			{{0, -25, 0}, {0, -15, 0}, {12, 0, -105}, {-65, 20, 0}, {-22, 0, -3}, {32, 0, 3}},
			{{0, 0, 0}, {0, 0, 0}, {-90, 0, 0}, {-90, 0, 0}, {0, 0, -2}, {0, 0, 2}},
			{{0, 0, 0}, {0, 0, 0}, {0, 0, -45}, {0, 0, 45}, {-85, 0, -3}, {0, 0, 3}},
			{{0, 0, 0}, {0, 0, 0}, {0, 0, -45}, {0, 0, 45}, {0, 0, -3}, {-85, 0, 3}},
			{{-12, 0, 0}, {0, 0, 0}, {5, 0, -55}, {-170, 0, 0}, {0, 0, -10}, {0, 0, 10}}
	};

	private static ArmorStandPosesMod instance;
	private static final Map<UUID, Integer> POSE = new ConcurrentHashMap<>();

	private final Option.Key cycleKey = key("Cycle Pose Key", 71); // G
	private final Option.Bool showOthers = bool("See Other Players' Poses", true).tip("Shows the poses that other Caos users give to armor stands.");
	private boolean down;
	private long lastSend;

	public ArmorStandPosesMod() {
		super("Armor Stand Poses", "armor_stand_poses", "Bedrock armor stand poses on Java. Look at an armor stand and press the key. Other players with the client see them too.", Category.VISUAL);
		instance = this;
	}

	/** The pose to draw on this stand, or null to leave it alone. */
	public static float[][] poseFor(UUID stand) {
		ArmorStandPosesMod m = instance;
		if (m == null || !m.isEnabled()) {
			return null;
		}
		Integer p = POSE.get(stand);
		return p == null || p <= 0 || p >= POSES.length ? null : POSES[p];
	}

	public static Rotations angle(float[] a) {
		return new Rotations(a[0], a[1], a[2]);
	}

	private static boolean hooked;

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		// like the Java mod in the video: sneak + right-click an armor stand with an empty hand changes its pose
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			ArmorStandPosesMod m = instance;
			if (m != null && m.isEnabled() && world.isClientSide() && player == m.mc.player && hand == net.minecraft.world.InteractionHand.MAIN_HAND
					&& player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && entity instanceof ArmorStand stand) {
				m.cycleStand(stand, 1);
				return net.minecraft.world.InteractionResult.SUCCESS;
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
	}

	@Override
	protected void onDisable() {
		POSE.clear();
	}

	@Override
	public void tick() {
		if (mc.player == null || mc.level == null) {
			POSE.clear();
			return;
		}
		boolean now = dev.solclient.compat.Mc.screen(mc) == null && cycleKey.code() > 0 && Keys.down(cycleKey.code());
		if (now && !down) {
			cycle(Keys.shiftHeld() ? -1 : 1);
		}
		down = now;
	}

	private void cycle(int dir) {
		if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ArmorStand stand)
				|| mc.player.distanceToSqr(stand) > 36) {
			mc.player.sendOverlayMessage(Component.literal("Look at an armor stand (up to 6 blocks) and press the key"));
			return;
		}
		cycleStand(stand, dir);
	}

	private void cycleStand(ArmorStand stand, int dir) {
		int cur = POSE.getOrDefault(stand.getUUID(), 0);
		int next = (cur + dir + NAMES.length) % NAMES.length;
		set(stand.getUUID(), next);
		mc.player.sendOverlayMessage(Component.literal("Changed pose: " + NAMES[next]));
		long now = System.currentTimeMillis();
		if (now - lastSend > 2_200L) { // the room allows one message every 2 seconds
			lastSend = now;
			dev.solclient.social.Presence.sendEmote(WIRE + next);
		}
	}

	private static void set(UUID id, int pose) {
		if (pose <= 0) {
			POSE.remove(id);
		} else {
			POSE.put(id, pose);
		}
	}

	/** Someone on your server changed a pose: find the stand they were looking at (same ray as theirs). */
	public static void onRemote(String uuidNoDashes, int pose) {
		ArmorStandPosesMod m = instance;
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (m == null || !m.isEnabled() || !m.showOthers.on() || mc.level == null || pose < 0 || pose >= NAMES.length) {
			return;
		}
		UUID id = dev.solclient.social.Presence.dashed(uuidNoDashes);
		Player p = id == null ? null : mc.level.getPlayerByUUID(id);
		if (p == null || p == mc.player) {
			return;
		}
		Vec3 from = p.getEyePosition(), to = from.add(p.getViewVector(1F).scale(6.5));
		ArmorStand best = null;
		double bd = Double.MAX_VALUE;
		for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(7))) {
			if (e instanceof ArmorStand s) {
				java.util.Optional<Vec3> hit = s.getBoundingBox().inflate(0.15).clip(from, to);
				if (hit.isPresent()) {
					double d = from.distanceToSqr(hit.get());
					if (d < bd) {
						bd = d;
						best = s;
					}
				}
			}
		}
		if (best != null) {
			set(best.getUUID(), pose);
		}
	}
}
