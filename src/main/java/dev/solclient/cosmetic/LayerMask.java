package dev.solclient.cosmetic;

/** Added to the player render state: which outer skin layers are drawn in 3D this frame (bits of {@link SkinLayers3D}). */
public interface LayerMask {

	int sleep$getMask();

	void sleep$setMask(int mask);
}
