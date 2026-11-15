package dev.kuboext.particles.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Only reads the position / size of the panel of an inventory-like screen (for 3D Screens). */
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {

	@Accessor("leftPos")
	int kubo$x();

	@Accessor("topPos")
	int kubo$y();

	@Accessor("imageWidth")
	int kubo$w();

	@Accessor("imageHeight")
	int kubo$h();
}
