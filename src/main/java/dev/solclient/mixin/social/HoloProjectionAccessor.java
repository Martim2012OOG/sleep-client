package dev.solclient.mixin.social;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.render.ProjectionMatrix2;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ProjectionMatrix2.class)
public interface HoloProjectionAccessor {

	@Invoker("getMatrix")
	Matrix4f holo$matrix(float w, float h);

	@Accessor("buffer")
	GpuBuffer holo$buffer();

	@Accessor("width")
	void holo$setWidth(float w);

	@Accessor("height")
	void holo$setHeight(float h);
}
