package dev.sleepclient.mods;

import dev.sleepclient.fx.ForcedGlow;
import dev.sleepclient.fx.Fx;
import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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

	private final List<Monster> marked = new ArrayList<>();
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

	private static boolean isNotSafe(Component m) {
		if (m.getContents() instanceof TranslatableContents t && "block.minecraft.bed.not_safe".equals(t.getKey())) {
			return true;
		}
		String s = m.getString().toLowerCase();
		return s.contains("monsters nearby") && s.contains("rest");
	}

	private void onNotSafe() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		// the game checks a box around the bed you clicked: 8 blocks sideways, 5 up and down
		Vec3 c = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
		if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK && mc.hitResult instanceof BlockHitResult bhr) {
			c = Vec3.atCenterOf(bhr.getBlockPos());
		}
		AABB box = new AABB(c.x - 8, c.y - 5, c.z - 8, c.x + 8, c.y + 5, c.z + 8);
		marked.clear();
		marked.addAll(mc.level.getEntitiesOfClass(Monster.class, box, e -> e.isAlive()));
		until = System.currentTimeMillis() + (long) (seconds.f() * 1000);
		applyGlow();
		if (chat.on()) {
			if (marked.isEmpty()) {
				mc.player.sendSystemMessage(Component.literal("\u00a77No monsters found around that bed (they may not be loaded yet)."));
				return;
			}
			Map<String, Integer> names = new LinkedHashMap<>();
			double nearest = Double.MAX_VALUE;
			for (Monster e : marked) {
				names.merge(e.getType().getDescription().getString(), 1, Integer::sum);
				nearest = Math.min(nearest, mc.player.distanceTo(e));
			}
			StringBuilder b = new StringBuilder("\u00a7eMonsters stopping you from sleeping: \u00a7f");
			boolean first = true;
			for (Map.Entry<String, Integer> en : names.entrySet()) {
				b.append(first ? "" : ", ").append(en.getValue()).append("x ").append(en.getKey());
				first = false;
			}
			b.append(" \u00a77(nearest ").append(Math.round(nearest)).append(" blocks)");
			mc.player.sendSystemMessage(Component.literal(b.toString()));
		}
	}

	/** The monsters get the glowing outline of a spectral arrow, visible through walls (this client only). */
	private void applyGlow() {
		ForcedGlow.clear();
		if (glow.on()) {
			for (Monster e : marked) {
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
		if (System.currentTimeMillis() > until || mc.player == null || mc.level == null) {
			marked.clear();
			ForcedGlow.clear();
			return;
		}
		tickCount++;
		var type = Fx.type(particle.idx());
		for (int i = marked.size() - 1; i >= 0; i--) {
			Monster e = marked.get(i);
			if (e == null || e.isRemoved() || !e.isAlive()) {
				marked.remove(i);
				if (e != null) {
					applyGlow();
				}
				continue;
			}
			double x = e.getX(), y = e.getY(), z = e.getZ(), h = e.getBbHeight();
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
