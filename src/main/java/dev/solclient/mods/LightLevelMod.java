package dev.solclient.mods;

import dev.solclient.option.Option;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluids;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

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
			WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
				LightLevelMod m = instance;
				if (m != null && m.enabled && !m.spots.isEmpty()) {
					long t0 = System.nanoTime();
					try {
						m.render(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos);
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
		ClientWorld w = mc.world;
		if (w == null || mc.player == null) {
			spots = List.of();
			return;
		}
		BlockPos me = mc.player.getBlockPos();
		int r = Math.round(range.get()), h = Math.round(height.get());
		boolean onlyDanger = show.idx() == 1;
		int limit = Math.round(dangerAt.get());
		List<Spot> out = new ArrayList<>();
		BlockPos.Mutable p = new BlockPos.Mutable(), below = new BlockPos.Mutable();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z > r * r || !w.isChunkLoaded((me.getX() + x) >> 4, (me.getZ() + z) >> 4)) {
					continue;
				}
				for (int y = -h; y <= h; y++) {
					p.set(me.getX() + x, me.getY() + y, me.getZ() + z);
					below.set(p.getX(), p.getY() - 1, p.getZ());
					BlockState st = w.getBlockState(p);
					BlockState ground = w.getBlockState(below);
					if (!st.getCollisionShape(w, p).isEmpty() || st.getFluidState().getFluid() != Fluids.EMPTY || !ground.isSideSolidFullSquare(w, below, Direction.UP)) {
						continue;
					}
					int block = w.getLightLevel(LightType.BLOCK, p), sky = w.getLightLevel(LightType.SKY, p);
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

	private void render(MatrixStack ms, OrderedRenderCommandQueue queue, Vec3d cam) {
		float sc = 0.035F * size.get() / 100F;
		float yaw = mc.player == null ? 0 : mc.player.getHorizontalFacing().getPositiveHorizontalDegrees();
		for (Spot s : spots) {
			ms.push();
			ms.translate(s.x - cam.x, s.y - cam.y, s.z - cam.z);
			ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180F - yaw));
			ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90F)); // flat on the block, its top towards where you look
			ms.scale(sc, -sc, sc);
			var t = Text.literal(s.text).asOrderedText();
			float x = -mc.textRenderer.getWidth(t) / 2F;
			queue.submitText(ms, x, -4F, t, shadow.on(), TextRenderer.TextLayerType.NORMAL, 0xF000F0, s.colour, 0, 0);
			ms.pop();
		}
	}
}
