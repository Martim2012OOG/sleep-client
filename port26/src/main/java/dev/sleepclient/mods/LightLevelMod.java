package dev.sleepclient.mods;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.sleepclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * Light Level: the light of every block around you written on the ground (like MiniHUD's light overlay): red where
 * monsters can spawn, yellow where they can only spawn at night, green where it's safe. The blocks are looked up a
 * few times a second, not every frame, so it costs almost no FPS.
 */
public class LightLevelMod extends Mod {

	public static LightLevelMod instance;
	private static boolean hooked;

	private final Option.Num range = num("Range", 12, 3, 32, 1, " blocks");
	private final Option.Num height = num("Height Range", 6, 1, 24, 1, " blocks");
	private final Option.Num size = num("Number Size", 100, 40, 200, 5, "%");
	private final Option.Choice show = tabs("Show", 0, "All Blocks", "Only Danger");
	private final Option.Num dangerAt = num("Danger At Or Below", 0, 0, 15, 1, "").tip("Monsters spawn at block light 0 in the Overworld.");
	private final Option.Bool shadow = bool("Text Shadow", false);
	private final Option.Col safe = colour("Safe Colour", 0xFF55FF55);
	private final Option.Col night = colour("Night Danger Colour", 0xFFFFD34D);
	private final Option.Col danger = colour("Danger Colour", 0xFFFF5050);

	private record Spot(double x, double y, double z, String text, int colour) {
	}

	private volatile List<Spot> spots = List.of();
	private int timer;

	public LightLevelMod() {
		super("Light Level", "light_level", "Writes the light level on the blocks around you, in red where monsters can spawn.", Category.VISUAL);
		instance = this;
	}

	@Override
	protected void onEnable() {
		if (hooked) {
			return;
		}
		hooked = true;
		try {
			LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
				LightLevelMod m = instance;
				if (m != null && m.enabled && !m.spots.isEmpty()) {
					long t0 = System.nanoTime();
					try {
						m.render(ctx.poseStack(), ctx.submitNodeCollector(), ctx.levelState().cameraRenderState.pos);
					} catch (RuntimeException ignored) {
						// never break the world render
					}
					m.addCost(System.nanoTime() - t0);
				}
			});
		} catch (Throwable t) {
			System.err.println("[Sleep] Light Level unavailable: " + t);
		}
	}

	@Override
	protected void onDisable() {
		spots = List.of();
	}

	@Override
	public void tick() {
		if (--timer > 0) {
			return;
		}
		timer = 4;
		ClientLevel w = mc.level;
		if (w == null || mc.player == null) {
			spots = List.of();
			return;
		}
		BlockPos me = mc.player.blockPosition();
		int r = Math.round(range.get()), h = Math.round(height.get());
		boolean onlyDanger = show.idx() == 1;
		int limit = Math.round(dangerAt.get());
		List<Spot> out = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), below = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z > r * r || !w.hasChunk((me.getX() + x) >> 4, (me.getZ() + z) >> 4)) {
					continue;
				}
				for (int y = -h; y <= h; y++) {
					p.set(me.getX() + x, me.getY() + y, me.getZ() + z);
					below.set(p.getX(), p.getY() - 1, p.getZ());
					BlockState st = w.getBlockState(p);
					BlockState ground = w.getBlockState(below);
					if (!st.getCollisionShape(w, p).isEmpty() || st.getFluidState().getType() != Fluids.EMPTY || !ground.isFaceSturdy(w, below, Direction.UP)) {
						continue;
					}
					int block = w.getBrightness(LightLayer.BLOCK, p), sky = w.getBrightness(LightLayer.SKY, p);
					boolean dark = block <= limit;
					if (onlyDanger && !dark) {
						continue;
					}
					int col = !dark ? safe.c() : sky > 7 ? night.c() : danger.c();
					out.add(new Spot(p.getX() + .5, p.getY() + .02, p.getZ() + .5, String.valueOf(block), col));
				}
			}
		}
		spots = out;
	}

	private void render(PoseStack ms, SubmitNodeCollector queue, Vec3 cam) {
		float sc = 0.035F * size.get() / 100F;
		float yaw = mc.player == null ? 0 : mc.player.getDirection().toYRot();
		for (Spot s : spots) {
			ms.pushPose();
			ms.translate(s.x - cam.x, s.y - cam.y, s.z - cam.z);
			ms.mulPose(Axis.YP.rotationDegrees(180F - yaw));
			ms.mulPose(Axis.XP.rotationDegrees(-90F)); // flat on the block, its top towards where you look
			ms.scale(sc, -sc, sc);
			var t = Component.literal(s.text).getVisualOrderText();
			float x = -mc.font.width(t) / 2F;
			queue.submitText(ms, x, -4F, t, shadow.on(), Font.DisplayMode.NORMAL, 0xF000F0, s.colour, 0, 0);
			ms.popPose();
		}
	}
}
