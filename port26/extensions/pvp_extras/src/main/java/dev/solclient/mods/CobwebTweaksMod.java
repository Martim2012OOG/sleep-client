package dev.solclient.mods;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.solclient.Gfx;
import dev.solclient.fx.WorldDraw;
import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Cobweb Tweaks: cobwebs around you get a clear outline (easy to see in PvP), and a warning shows when you are stuck
 * in one.
 */
public class CobwebTweaksMod extends Mod {

	public static CobwebTweaksMod instance;

	private final Option.Bool outline = bool("Outline Cobwebs", true);
	private final Option.Num range = num("Range", 8, 3, 16, 1, " blocks");
	private final Option.Col colour = colour("Outline Colour", 0xFFE8E8F0);
	private final Option.Bool stuck = bool("Warning When Stuck", true);
	private final List<BlockPos> webs = new ArrayList<>();
	private int scanTick;
	private static boolean hooked;

	public CobwebTweaksMod() {
		super("Cobweb Tweaks", "cobweb_tweaks", "Outlines cobwebs near you and warns you when you are stuck in one.", Category.VISUAL);
		instance = this;
	}

	@Override
	public void init() {
		super.init();
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
				if (instance != null && instance.enabled && instance.outline.on()) {
					try {
						instance.render(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
					}
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Cobweb Tweaks outline unavailable: " + t);
		}
	}

	@Override
	public void tick() {
		if (mc.player == null || mc.level == null || ++scanTick % 10 != 0) {
			return;
		}
		int r = range.i();
		BlockPos c = mc.player.blockPosition();
		List<BlockPos> found = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) {
			for (int y = -r; y <= r; y++) {
				for (int z = -r; z <= r; z++) {
					p.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (mc.level.getBlockState(p).is(Blocks.COBWEB)) {
						found.add(p.immutable());
						if (found.size() > 400) {
							break;
						}
					}
				}
			}
		}
		synchronized (webs) {
			webs.clear();
			webs.addAll(found);
		}
	}

	private void render(PoseStack m, SubmitNodeCollector q, Vec3 cam) {
		WorldDraw.Lines lines = new WorldDraw.Lines(cam);
		int col = colour.c();
		synchronized (webs) {
			for (BlockPos b : webs) {
				lines.box(b.getX() + 0.02, b.getY() + 0.02, b.getZ() + 0.02, b.getX() + 0.98, b.getY() + 0.98, b.getZ() + 0.98, 0.025F, col);
				lines.line(b.getX() + 0.02, b.getY() + 0.02, b.getZ() + 0.02, b.getX() + 0.98, b.getY() + 0.98, b.getZ() + 0.98, 0.02F, (col & 0xFFFFFF) | 0x66000000);
			}
		}
		lines.draw(m, q);
	}

	/** HUD: "In a cobweb" under the crosshair while you are stuck. */
	public void draw(GuiGraphicsExtractor c) {
		if (!stuck.on() || mc.player == null || mc.level == null || dev.solclient.compat.Mc.hideGui(mc)) {
			return;
		}
		BlockPos feet = mc.player.blockPosition(), head = feet.above();
		boolean in = mc.level.getBlockState(feet).is(Blocks.COBWEB) || mc.level.getBlockState(head).is(Blocks.COBWEB);
		if (!in) {
			return;
		}
		String t = dev.solclient.Lang.tr("In a cobweb", "Numa teia");
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		int w = Gfx.twRaw(t) + 12;
		Gfx.rrectFree(c, sw / 2F - w / 2F, sh / 2F + 22, w, 13, 4F, 0xB0101114);
		Gfx.textRaw(c, t, sw / 2 - Gfx.twRaw(t) / 2, sh / 2 + 25, 0xFFE8E8F0);
	}
}
