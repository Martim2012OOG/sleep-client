package dev.solclient.mixin.overlay;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen really is (its top-left corner and size): the Inventory Buttons are placed from this, not guessed. */
@Mixin(AbstractContainerScreen.class)
public interface HandledScreenAccessor {

	@Accessor("leftPos")
	int sol$x();

	@Accessor("topPos")
	int sol$y();

	@Accessor("imageWidth")
	int sol$w();

	@Accessor("imageHeight")
	int sol$h();
}
