package dev.sleepclient.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Camada de compatibilidade 26.x: o BlockColors.getColor(...) antigo foi substituido por "tint sources". */
public final class BlockTint {
	private BlockTint() {
	}

	/** Cor de tinta (bioma/etc.) do bloco, ou -1 se o bloco nao tem tinta no indice dado. */
	public static int color(BlockState state, BlockAndTintGetter level, BlockPos pos, int index) {
		var src = Minecraft.getInstance().getBlockColors().getTintSource(state, index);
		return src == null ? -1 : src.colorInWorld(state, level, pos);
	}

	/** Igual, mas aceita um Level qualquer (so o ClientLevel tem tinta; os outros dao -1). */
	public static int colorAny(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, int index) {
		return level instanceof BlockAndTintGetter t ? color(state, t, pos, index) : -1;
	}
}
