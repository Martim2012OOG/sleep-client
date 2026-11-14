package dev.solclient.mixin.overlay;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen really is (its top-left corner and size): the Inventory Buttons are placed from this, not guessed. */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

	@Accessor("x")
	int sol$x();

	@Accessor("y")
	int sol$y();

	@Accessor("backgroundWidth")
	int sol$w();

	@Accessor("backgroundHeight")
	int sol$h();
}
