package dev.solclient.mods;

import dev.solclient.fx.ForcedGlow;
import dev.solclient.fx.Fx;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Monsters in the Closet: when a bed tells you "You may not rest now, there are monsters nearby", the hostile mobs
 * that block your sleep (the ones the game checks around the bed) glow with an outline you can see through walls, like
 * a spectral arrow hit, plus a ring of particles over the head, and a short list says which ones they are and how far.
 * Uses only mobs your game already knows about.
 */
public class MonstersInTheClosetMod extends Mod {

	public static MonstersInTheClosetMod instance;

	private final Option.Num seconds = num("Show For", 15, 3, 60, 1, " s");
	private final Option.Choice particle = choice("Particle", 3, Fx.NAMES);
	private final Option.Bool glow = bool("Glow Through Walls", true);
	private final Option.Bool beam = bool("Beam Above Mob", false);
	private final Option.Bool chat = bool("List In Chat", true);

	private final List<HostileEntity> marked = new ArrayList<>();
	private long until;
	private int tickCount;

	public MonstersInTheClosetMod() {
		super("Monsters in the Closet", "monsters_in_the_closet", "Marks the monsters that stop you from sleeping.", Category.VISUAL);
		instance = this;
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			if (instance != null && instance.enabled && isNotSafe(message)) {
				instance.onNotSafe();
			}
			return true;
		});
	}

	@Override
	public boolean enabledByDefault() {
		return true;
	}

	private static boolean isNotSafe(Text m) {
		if (m.getContent() instanceof TranslatableTextContent t && "block.minecraft.bed.not_safe".equals(t.getKey())) {
			return true;
		}
		String s = m.getString().toLowerCase();
		return s.contains("monsters nearby") && s.contains("rest");
	}

	private void onNotSafe() {
		if (mc.player == null || mc.world == null) {
			return;
		}
		// the game checks a box around the bed you clicked: 8 blocks sideways, 5 up and down
		Vec3d c = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
		if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK && mc.crosshairTarget instanceof BlockHitResult bhr) {
			c = Vec3d.ofCenter(bhr.getBlockPos());
		}
		Box box = new Box(c.x - 8, c.y - 5, c.z - 8, c.x + 8, c.y + 5, c.z + 8);
		marked.clear();
		marked.addAll(mc.world.getEntitiesByClass(HostileEntity.class, box, e -> e.isAlive()));
		until = System.currentTimeMillis() + (long) (seconds.f() * 1000);
		applyGlow();
		if (chat.on()) {
			if (marked.isEmpty()) {
				mc.player.sendMessage(Text.literal("\u00a77No monsters found around that bed (they may not be loaded yet)."), false);
				return;
			}
			Map<String, Integer> names = new LinkedHashMap<>();
			double nearest = Double.MAX_VALUE;
			for (HostileEntity e : marked) {
				names.merge(e.getType().getName().getString(), 1, Integer::sum);
				nearest = Math.min(nearest, mc.player.distanceTo(e));
			}
			StringBuilder b = new StringBuilder("\u00a7eMonsters stopping you from sleeping: \u00a7f");
			boolean first = true;
			for (Map.Entry<String, Integer> en : names.entrySet()) {
				b.append(first ? "" : ", ").append(en.getValue()).append("x ").append(en.getKey());
				first = false;
			}
			b.append(" \u00a77(nearest ").append(Math.round(nearest)).append(" blocks)");
			mc.player.sendMessage(Text.literal(b.toString()), false);
		}
	}

	/** The monsters get the glowing outline of a spectral arrow, visible through walls (this client only). */
	private void applyGlow() {
		ForcedGlow.clear();
		if (glow.on()) {
			for (HostileEntity e : marked) {
				ForcedGlow.set(e.getId(), 0xFF5A3C);
			}
		}
	}

	@Override
	protected void onDisable() {
		ForcedGlow.clear();
		marked.clear();
	}

	@Override
	public void tick() {
		if (marked.isEmpty()) {
			return;
		}
		if (System.currentTimeMillis() > until || mc.player == null || mc.world == null) {
			marked.clear();
			ForcedGlow.clear();
			return;
		}
		tickCount++;
		var type = Fx.type(particle.idx());
		for (int i = marked.size() - 1; i >= 0; i--) {
			HostileEntity e = marked.get(i);
			if (e == null || e.isRemoved() || !e.isAlive()) {
				marked.remove(i);
				if (e != null) {
					applyGlow();
				}
				continue;
			}
			double x = e.getX(), y = e.getY(), z = e.getZ(), h = e.getHeight();
			// a ring around the head so you can find it...
			double a = tickCount * 0.4;
			Fx.spawn(type, x + Math.cos(a) * 0.5, y + h + 0.2, z + Math.sin(a) * 0.5, 0, 0, 0);
			Fx.spawn(type, x - Math.cos(a) * 0.5, y + h + 0.2, z - Math.sin(a) * 0.5, 0, 0, 0);
			// ...and a beam going up
			if (beam.on() && tickCount % 2 == 0) {
				for (double dy = h + 0.4; dy < h + 4.0; dy += 0.5) {
					Fx.spawn(ParticleTypes.END_ROD, x, y + dy, z, 0, 0.01, 0);
				}
			}
		}
	}
}
