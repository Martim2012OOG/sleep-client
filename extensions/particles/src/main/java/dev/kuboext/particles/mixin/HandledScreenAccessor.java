package dev.kuboext.particles.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Only reads the position / size of the panel of an inventory-like screen (for 3D Screens). */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {

	@Accessor("x")
	int kubo$x();

	@Accessor("y")
	int kubo$y();

	@Accessor("backgroundWidth")
	int kubo$w();

	@Accessor("backgroundHeight")
	int kubo$h();
}
