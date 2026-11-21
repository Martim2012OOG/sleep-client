package dev.kuboext.particles;

import dev.sleepclient.mods.AuraMod;
import dev.sleepclient.mods.TrailsMod;
import dev.sleepclient.mods.JumpCirclesMod;
import dev.sleepclient.mods.KillEffectMod;
import dev.sleepclient.mods.HitBubblesMod;
import dev.sleepclient.mods.PickupBurstMod;
import dev.sleepclient.mods.FirefliesMod;
import dev.sleepclient.mods.CaveDustMod;
import dev.sleepclient.mods.FallingLeavesMod;
import dev.sleepclient.mods.WaterSplashesMod;
import dev.sleepclient.mods.WorldParticlesMod;
import dev.sleepclient.mods.MaterialFootstepsMod;
import dev.sleepclient.mods.ExplosionDebrisMod;
import dev.sleepclient.mods.MaceEffectsMod;
import dev.sleepclient.mods.ToolEffectsMod;
import dev.sleepclient.mods.Physics3DMod;
import dev.sleepclient.ext.KuboApi;
import dev.sleepclient.ext.KuboExtension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Sleep extension "Extra Particles": Aura, Trails, Jump Circles, Fireflies, Cave Dust, Falling Leaves, Kill Effect, Mace Effects, Tool Effects, 3D Physics and more. */
public class ParticlesExtension implements KuboExtension {

	@Override
	public void register(KuboApi api) {
		api.addMod(new AuraMod());
		api.addMod(new TrailsMod());
		api.addMod(new JumpCirclesMod());
		api.addMod(new KillEffectMod());
		api.addMod(new HitBubblesMod());
		api.addMod(new PickupBurstMod());
		api.addMod(new FirefliesMod());
		api.addMod(new CaveDustMod());
		api.addMod(new FallingLeavesMod());
		api.addMod(new WaterSplashesMod());
		api.addMod(new WorldParticlesMod());
		api.addMod(new MaterialFootstepsMod());
		api.addMod(new ExplosionDebrisMod());
		api.addMod(new MaceEffectsMod());
		api.addMod(new ToolEffectsMod());
		api.addMod(new Physics3DMod());
		// 3D Screens: Fabric's screen events (no guessed mixin method names). Tilts inventory / crafting / furnace...
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BEFORE_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
				return;
			}
			final boolean[] tilted = {false};
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.beforeExtract(screen).register((s, ctx, mx, my, delta) -> {
				tilted[0] = false;
				try {
					Physics3DMod m = Physics3DMod.instance;
					if (m == null || !m.enabled || !m.allowed(s)) {
						return;
					}
					int px = (s.width - 176) / 2, py = (s.height - 166) / 2, pw = 176, ph = 166;
					if (s instanceof dev.kuboext.particles.mixin.HandledScreenAccessor acc) {
						px = acc.kubo$x();
						py = acc.kubo$y();
						pw = acc.kubo$w();
						ph = acc.kubo$h();
					}
					tilted[0] = m.begin(ctx, s, mx, my, px, py, pw, ph);
				} catch (Throwable t) {
					tilted[0] = false;
				}
			});
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> {
				if (tilted[0]) {
					tilted[0] = false;
					try {
						Physics3DMod.instance.end(ctx);
					} catch (Throwable ignored) {
					}
				}
			});
		});
		api.onBlockBreak((pos, st) -> Physics3DMod.instance != null && Physics3DMod.instance.enabled && Physics3DMod.instance.onBreak(pos, st));
		api.onWorldSound((id, x, y, z) -> {
			if (MaceEffectsMod.instance != null && MaceEffectsMod.instance.enabled) MaceEffectsMod.instance.onSound(id, x, y, z);
		});
		api.onExplosion(at -> {
			if (ExplosionDebrisMod.instance != null && (ExplosionDebrisMod.instance.enabled || ExplosionDebrisMod.instance.external)) ExplosionDebrisMod.instance.onExplosion(at);
		});
		// the server tells every client when an entity dies (status 3): the moment of a kill, the same signal servers use
		api.onEntityStatus((e, st) -> {
			if (st == 3 && KillEffectMod.instance != null && KillEffectMod.instance.enabled) KillEffectMod.instance.onDeathSignal(e);
		});
		api.onAttack(e -> {
			if (KillEffectMod.instance != null && KillEffectMod.instance.enabled) KillEffectMod.instance.onAttack(e);
			if (HitBubblesMod.instance != null && HitBubblesMod.instance.enabled) HitBubblesMod.instance.onAttack(e);
			if (ToolEffectsMod.instance != null && ToolEffectsMod.instance.enabled) ToolEffectsMod.instance.onAttack(e);
		});
	}
}
