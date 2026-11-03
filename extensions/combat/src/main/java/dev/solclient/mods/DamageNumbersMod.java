package dev.solclient.mods;

import dev.solclient.Gfx;
import dev.solclient.fx.Project;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;

/** Damage numbers: how much every hit took (-3.5) pops out of the entity, bigger and redder for big hits. */
public class DamageNumbersMod extends Mod {

	public static DamageNumbersMod instance;
	private final Option.Choice unit = choice("Show As", 0, "Hearts", "Health Points");
	private final Option.Bool heals = bool("Show Healing", true);
	private final Option.Bool onlyMine = bool("Only Entities I Hit", false);
	private final Option.Num range = num("Range", 24, 6, 64, 2, " blocks");
	private final Option.Num size = num("Size", 100, 20, 400, 10, "%");

	private record Pop(double x, double y, double z, float amount, long at, float dx) {
	}

	private final Map<Integer, Float> last = new HashMap<>();
	private final Map<Integer, Long> hitByMe = new HashMap<>();
	private final List<Pop> pops = new ArrayList<>();
	private final Random rnd = new Random();

	public DamageNumbersMod() {
		super("Damage Numbers", "damage_numbers", "See how much damage each hit does, floating out of the target.", Category.VISUAL);
		instance = this;
	}

	public void onAttack(Entity e) {
		hitByMe.put(e.getId(), System.currentTimeMillis());
	}

	@Override
	public void tick() {
		if (mc.world == null || mc.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		double r2 = range.f() * range.f();
		Map<Integer, Float> cur = new HashMap<>();
		for (Entity e : mc.world.getEntities()) {
			if (!(e instanceof LivingEntity le) || e instanceof ArmorStandEntity || mc.player.squaredDistanceTo(e) > r2) {
				continue;
			}
			float hp = le.getHealth() + le.getAbsorptionAmount();
			cur.put(e.getId(), hp);
			Float before = last.get(e.getId());
			if (before == null || e == mc.player) {
				continue;
			}
			float d = hp - before;
			if (Math.abs(d) < 0.05F || d > 0 && !heals.on()) {
				continue;
			}
			if (onlyMine.on() && d < 0 && now - hitByMe.getOrDefault(e.getId(), 0L) > 1500L) {
				continue;
			}
			pops.add(new Pop(e.getX(), e.getY() + e.getHeight() * 0.8, e.getZ(), d, now, rnd.nextFloat() * 2 - 1));
		}
		last.clear();
		last.putAll(cur);
		pops.removeIf(p -> now - p.at > 1100L);
		hitByMe.values().removeIf(t -> now - t > 5000L);
		while (pops.size() > 60) {
			pops.remove(0);
		}
	}

	public void draw(DrawContext c) {
		if (pops.isEmpty() || !Project.begin()) {
			return;
		}
		long now = System.currentTimeMillis();
		var tr = MinecraftClient.getInstance().textRenderer;
		for (Pop p : new ArrayList<>(pops)) {
			float age = (now - p.at) / 1100F;
			double rise = age * 1.2;
			if (!Project.to(p.x + p.dx * 0.3 * age, p.y + rise, p.z)) {
				continue;
			}
			float amt = Math.abs(p.amount) / (unit.idx() == 0 ? 2F : 1F);
			String s = (p.amount < 0 ? "-" : "+") + (amt == Math.floor(amt) ? Integer.toString((int) amt) : String.format("%.1f", amt)) + (unit.idx() == 0 ? "❤" : "");
			boolean big = Math.abs(p.amount) >= 6;
			int col = p.amount > 0 ? 0xFF4ADE80 : big ? 0xFFFF2E4D : Math.abs(p.amount) >= 3 ? 0xFFFF8A3D : 0xFFFFD166;
			float pop = age < 0.15F ? 0.6F + age / 0.15F * 0.6F : 1.2F - Math.min(0.2F, (age - 0.15F));
			float sc = (float) (size.f() / 100F * pop * (big ? 1.4 : 1) * Math.max(0.6, Math.min(1.4, 8 / Math.max(1, Project.dist))));
			int a = Math.round(255 * Math.min(1, (1 - age) * 3));
			var m = c.getMatrices();
			m.pushMatrix();
			m.translate(Project.sx, Project.sy);
			m.scale(sc, sc);
			c.drawText(tr, s, -tr.getWidth(s) / 2, -4, Gfx.withAlpha(col, a), true);
			m.popMatrix();
		}
	}
}
