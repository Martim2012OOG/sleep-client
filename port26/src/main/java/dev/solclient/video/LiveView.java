package dev.solclient.video;

import net.minecraft.resources.Identifier;

/** Something that keeps a texture filled with a live picture (a video, a window of this computer). Render thread. */
public interface LiveView extends AutoCloseable {

	/** Copies the newest picture into the texture. True once there is one. */
	boolean upload();

	boolean hasPicture();

	boolean failed();

	Identifier id();

	int width();

	int height();

	@Override
	void close();
}
